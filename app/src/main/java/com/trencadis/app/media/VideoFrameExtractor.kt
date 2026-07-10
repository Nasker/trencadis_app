package com.trencadis.app.media

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Video frame extractor for Trencadis 2.0
 * Extracts frames from video files for pixel grid conversion and audio synthesis
 */
class VideoFrameExtractor {
    
    private var mediaRetriever: MediaMetadataRetriever? = null
    private var currentVideoFile: File? = null
    private var frameCount: Int = 0
    private var frameRate: Float = 30f
    private var duration: Long = 0L
    
    /**
     * Load a video file for frame extraction
     */
    suspend fun loadVideo(videoFile: File): Result<VideoInfo> = withContext(Dispatchers.IO) {
        return@withContext try {
            release()
            
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)
            mediaRetriever = retriever
            currentVideoFile = videoFile
            
            // Extract video metadata
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val frameRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            
            duration = durationStr?.toLongOrNull() ?: 0L
            frameRate = frameRateStr?.toFloatOrNull() ?: 30f
            frameCount = (duration * frameRate / 1000).toInt()
            
            val videoInfo = VideoInfo(
                width = width,
                height = height,
                duration = duration,
                frameRate = frameRate,
                frameCount = frameCount,
                file = videoFile
            )
            
            Result.success(videoInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Extract frame at specific timestamp (in microseconds)
     */
    suspend fun extractFrameAtTime(timeUs: Long): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            mediaRetriever?.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Extract frame at specific timestamp (in milliseconds)
     */
    suspend fun extractFrameAtTimeMs(timeMs: Long): Bitmap? {
        return extractFrameAtTime(timeMs * 1000)
    }
    
    /**
     * Extract frame by index
     */
    suspend fun extractFrameByIndex(frameIndex: Int): Bitmap? {
        if (frameIndex < 0 || frameIndex >= frameCount) return null
        
        val timeMs = (frameIndex * 1000 / frameRate).toLong()
        return extractFrameAtTimeMs(timeMs)
    }
    
    /**
     * Extract current frame based on playback time
     */
    suspend fun extractCurrentFrame(playbackTimeMs: Long): Bitmap? {
        return extractFrameAtTimeMs(playbackTimeMs)
    }
    
    /**
     * Get frame at specific percentage of video duration
     */
    suspend fun extractFrameAtPercentage(percentage: Float): Bitmap? {
        if (percentage < 0f || percentage > 1f) return null
        
        val timeMs = (duration * percentage).toLong()
        return extractFrameAtTimeMs(timeMs)
    }
    
    /**
     * Extract multiple frames for preview
     */
    suspend fun extractPreviewFrames(frameCount: Int = 10): List<Bitmap> = withContext(Dispatchers.IO) {
        return@withContext try {
            val frames = mutableListOf<Bitmap>()
            val interval = 1f / frameCount
            
            for (i in 0 until frameCount) {
                val percentage = i * interval
                extractFrameAtPercentage(percentage)?.let { frame ->
                    frames.add(frame)
                }
            }
            
            frames
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get thumbnail for video (first frame)
     */
    suspend fun getThumbnail(): Bitmap? {
        return extractFrameAtTimeMs(0)
    }
    
    /**
     * Convert frame to pixel grid for audio synthesis
     */
    suspend fun extractPixelGridAtTime(timeMs: Long, blockSize: Int = 20): com.trencadis.app.camera.PixelGrid? = withContext(Dispatchers.IO) {
        return@withContext try {
            val frame = extractFrameAtTimeMs(timeMs)
            frame?.let { bitmap ->
                convertBitmapToPixelGrid(bitmap, blockSize)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get video information
     */
    fun getVideoInfo(): VideoInfo? {
        val file = currentVideoFile ?: return null
        
        return VideoInfo(
            width = mediaRetriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
            height = mediaRetriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
            duration = duration,
            frameRate = frameRate,
            frameCount = frameCount,
            file = file
        )
    }
    
    /**
     * Get frame count
     */
    fun getFrameCount(): Int = frameCount
    
    /**
     * Get frame rate
     */
    fun getFrameRate(): Float = frameRate
    
    /**
     * Get duration in milliseconds
     */
    fun getDuration(): Long = duration
    
    /**
     * Check if video is loaded
     */
    fun isVideoLoaded(): Boolean = mediaRetriever != null
    
    /**
     * Release resources
     */
    fun release() {
        mediaRetriever?.release()
        mediaRetriever = null
        currentVideoFile = null
        frameCount = 0
        frameRate = 30f
        duration = 0L
    }
    
    /**
     * Convert bitmap to pixel grid (same logic as camera analyzer)
     */
    private suspend fun convertBitmapToPixelGrid(bitmap: Bitmap, blockSize: Int): com.trencadis.app.camera.PixelGrid = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val cols = width / blockSize
        val rows = height / blockSize
        
        val pixels = mutableListOf<com.trencadis.app.camera.PixelData>()
        
        // Extract pixel data
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = col * blockSize
                val y = row * blockSize
                
                // Get average color for this block
                var totalR = 0f
                var totalG = 0f
                var totalB = 0f
                var pixelCount = 0
                
                for (dy in 0 until blockSize) {
                    for (dx in 0 until blockSize) {
                        if (x + dx < width && y + dy < height) {
                            val pixel = bitmap.getPixel(x + dx, y + dy)
                            totalR += (pixel shr 16 and 0xFF) / 255f
                            totalG += (pixel shr 8 and 0xFF) / 255f
                            totalB += (pixel and 0xFF) / 255f
                            pixelCount++
                        }
                    }
                }
                
                val avgR = totalR / pixelCount
                val avgG = totalG / pixelCount
                val avgB = totalB / pixelCount
                
                // Convert to HSB
                val max = maxOf(avgR, avgG, avgB)
                val min = minOf(avgR, avgG, avgB)
                val delta = max - min
                
                val brightness = max
                val saturation = if (max == 0f) 0f else delta / max
                
                var hue = 0f
                if (delta != 0f) {
                    hue = when (max) {
                        avgR -> (avgG - avgB) / delta + if (avgG < avgB) 6f else 0f
                        avgG -> (avgB - avgR) / delta + 2f
                        avgB -> (avgR - avgG) / delta + 4f
                        else -> 0f
                    }
                    hue /= 6f
                }
                
                val pixelData = com.trencadis.app.camera.PixelData(
                    gridX = col,
                    gridY = row,
                    hue = hue * 360f,
                    saturation = saturation,
                    brightness = brightness,
                    red = avgR,
                    green = avgG,
                    blue = avgB
                )
                
                pixels.add(pixelData)
            }
        }
        
        // TODO: Add blob detection if needed
        val blobs = emptyList<com.trencadis.app.camera.BlobDetector.PixelBlob>()
        
        com.trencadis.app.camera.PixelGrid(
            cols = cols,
            rows = rows,
            blockSize = blockSize,
            pixels = pixels,
            blobs = blobs
        )
    }
}

/**
 * Video information
 */
data class VideoInfo(
    val width: Int,
    val height: Int,
    val duration: Long,        // in milliseconds
    val frameRate: Float,
    val frameCount: Int,
    val file: File
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height else 1f
    
    val durationSeconds: Float
        get() = duration / 1000f
}
