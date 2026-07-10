package com.trencadis.app.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manager for raw media capture (stills and videos)
 * Handles camera frame capture and file storage
 */
class RawMediaCaptureManager(private val context: Context) {
    
    private val _captureState = MutableStateFlow<MediaCaptureState>(MediaCaptureState())
    val captureState: StateFlow<MediaCaptureState> = _captureState.asStateFlow()
    
    private val mediaDir = File(context.filesDir, "media")
    private val stillsDir = File(mediaDir, "stills")
    private val videosDir = File(mediaDir, "videos")
    private val thumbnailsDir = File(mediaDir, "thumbnails")
    
    init {
        // Create directories if they don't exist
        mediaDir.mkdirs()
        stillsDir.mkdirs()
        videosDir.mkdirs()
        thumbnailsDir.mkdirs()
        
        // Load existing media library
        loadMediaLibrary()
    }
    
    /**
     * Capture a still frame from the current camera feed
     * This should be called when a frame is available from CameraPixelAnalyzer
     */
    suspend fun captureStillFrame(bitmap: Bitmap): MediaFile {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "trencadis_still_$timestamp.jpg"
        val file = File(stillsDir, fileName)
        
        // Save bitmap to file
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        // Create thumbnail
        val thumbnailFile = createThumbnail(bitmap, fileName)
        
        val mediaFile = MediaFile(
            id = fileName.removeSuffix(".jpg"),
            name = fileName,
            type = MediaType.STILL_IMAGE,
            uri = Uri.fromFile(file),
            thumbnailUri = Uri.fromFile(thumbnailFile),
            fileSize = file.length()
        )
        
        // Update state
        val currentLibrary = _captureState.value.library
        val updatedLibrary = currentLibrary.copy(
            stills = currentLibrary.stills + mediaFile
        )
        _captureState.value = _captureState.value.copy(library = updatedLibrary)
        
        return mediaFile
    }
    
    /**
     * Start video recording session
     */
    fun startVideoRecording(): VideoRecordingSession {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "trencadis_video_$timestamp.mp4"
        val file = File(videosDir, fileName)
        
        val session = VideoRecordingSession(
            id = fileName.removeSuffix(".mp4"),
            file = file,
            startTime = System.currentTimeMillis()
        )
        
        _captureState.value = _captureState.value.copy(
            isRecording = true,
            recordingStartTime = session.startTime
        )
        
        return session
    }
    
    /**
     * Stop video recording and save to library
     */
    suspend fun stopRecording(session: VideoRecordingSession): MediaFile {
        val duration = System.currentTimeMillis() - session.startTime
        
        // Create thumbnail from first frame
        val thumbnailFile = createThumbnailFromVideo(session.file, session.id)
        
        val mediaFile = MediaFile(
            id = session.id,
            name = session.file.name,
            type = MediaType.VIDEO,
            uri = Uri.fromFile(session.file),
            thumbnailUri = Uri.fromFile(thumbnailFile),
            durationMs = duration,
            fileSize = session.file.length()
        )
        
        // Update state
        val currentLibrary = _captureState.value.library
        val updatedLibrary = currentLibrary.copy(
            videos = currentLibrary.videos + mediaFile
        )
        _captureState.value = _captureState.value.copy(
            library = updatedLibrary,
            isRecording = false,
            recordingStartTime = 0L
        )
        
        return mediaFile
    }
    
    /**
     * Delete a media file from the library
     */
    suspend fun deleteMediaFile(mediaFile: MediaFile) {
        // Delete main file
        File(mediaFile.uri.path ?: "").delete()
        
        // Delete thumbnail if exists
        mediaFile.thumbnailUri?.let { thumbnailUri ->
            File(thumbnailUri.path ?: "").delete()
        }
        
        // Update library
        val currentLibrary = _captureState.value.library
        val updatedLibrary = when (mediaFile.type) {
            MediaType.STILL_IMAGE -> currentLibrary.copy(
                stills = currentLibrary.stills.filter { it.id != mediaFile.id }
            )
            MediaType.VIDEO -> currentLibrary.copy(
                videos = currentLibrary.videos.filter { it.id != mediaFile.id }
            )
        }
        
        _captureState.value = _captureState.value.copy(library = updatedLibrary)
    }
    
    /**
     * Toggle favorite status for a media file
     */
    suspend fun toggleFavorite(mediaFileId: String) {
        val currentLibrary = _captureState.value.library
        val favorites = currentLibrary.favorites.toMutableList()
        
        if (favorites.contains(mediaFileId)) {
            favorites.remove(mediaFileId)
        } else {
            favorites.add(mediaFileId)
        }
        
        _captureState.value = _captureState.value.copy(
            library = currentLibrary.copy(favorites = favorites)
        )
    }
    
    /**
     * Load existing media from storage
     */
    private fun loadMediaLibrary() {
        val stills = stillsDir.listFiles()?.mapNotNull { file ->
            if (file.extension.equals("jpg", ignoreCase = true)) {
                val thumbnailFile = File(thumbnailsDir, "${file.nameWithoutExtension}_thumb.jpg")
                MediaFile(
                    id = file.nameWithoutExtension,
                    name = file.name,
                    type = MediaType.STILL_IMAGE,
                    uri = Uri.fromFile(file),
                    thumbnailUri = if (thumbnailFile.exists()) Uri.fromFile(thumbnailFile) else null,
                    fileSize = file.length()
                )
            } else null
        }?.sortedByDescending { it.createdAt } ?: emptyList()
        
        val videos = videosDir.listFiles()?.mapNotNull { file ->
            if (file.extension.equals("mp4", ignoreCase = true)) {
                val thumbnailFile = File(thumbnailsDir, "${file.nameWithoutExtension}_thumb.jpg")
                MediaFile(
                    id = file.nameWithoutExtension,
                    name = file.name,
                    type = MediaType.VIDEO,
                    uri = Uri.fromFile(file),
                    thumbnailUri = if (thumbnailFile.exists()) Uri.fromFile(thumbnailFile) else null,
                    fileSize = file.length()
                )
            } else null
        }?.sortedByDescending { it.createdAt } ?: emptyList()
        
        _captureState.value = _captureState.value.copy(
            library = MediaLibrary(stills = stills, videos = videos)
        )
    }
    
    /**
     * Create thumbnail from bitmap
     */
    private fun createThumbnail(bitmap: Bitmap, fileName: String): File {
        val thumbnailFile = File(thumbnailsDir, "${fileName.removeSuffix(".jpg")}_thumb.jpg")
        
        // Scale down to thumbnail size
        val thumbnailSize = 200
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            thumbnailSize,
            (bitmap.height * thumbnailSize / bitmap.width),
            true
        )
        
        FileOutputStream(thumbnailFile).use { out ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        
        return thumbnailFile
    }
    
    /**
     * Create thumbnail from video file (placeholder implementation)
     */
    private fun createThumbnailFromVideo(videoFile: File, videoId: String): File {
        // TODO: Implement video frame extraction for thumbnail
        // For now, create a placeholder
        val thumbnailFile = File(thumbnailsDir, "${videoId}_thumb.jpg")
        if (!thumbnailFile.exists()) {
            // Create a simple placeholder thumbnail
            // In a real implementation, you'd extract the first frame
        }
        return thumbnailFile
    }
}

/**
 * Video recording session data
 */
data class VideoRecordingSession(
    val id: String,
    val file: File,
    val startTime: Long
)
