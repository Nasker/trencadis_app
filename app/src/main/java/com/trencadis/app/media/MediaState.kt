package com.trencadis.app.media

import android.net.Uri

/**
 * Media capture and playback state for Trencadis 2.0
 */
enum class MediaMode {
    LIVE_CAMERA,        // Current live camera feed
    STATIC_IMAGE,       // Frozen still image playback
    VIDEO_PLAYBACK,     // Video file playback
    VIDEO_RECORDING     // Recording raw camera feed
}

data class MediaFile(
    val id: String,
    val name: String,
    val type: MediaType,
    val uri: Uri,
    val thumbnailUri: Uri?,
    val durationMs: Long = 0L, // For videos
    val createdAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0L
)

enum class MediaType {
    STILL_IMAGE,
    VIDEO
}

data class MediaLibrary(
    val stills: List<MediaFile> = emptyList(),
    val videos: List<MediaFile> = emptyList(),
    val favorites: List<String> = emptyList() // Media file IDs
)

data class MediaCaptureState(
    val mode: MediaMode = MediaMode.LIVE_CAMERA,
    val currentMedia: MediaFile? = null,
    val library: MediaLibrary = MediaLibrary(),
    val isRecording: Boolean = false,
    val recordingStartTime: Long = 0L,
    val playbackState: VideoPlaybackState = VideoPlaybackState()
)

data class VideoPlaybackState(
    val isPlaying: Boolean = false,
    val currentTime: Long = 0L,
    val duration: Long = 0L,
    val loopStart: Long = 0L,
    val loopEnd: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isLooping: Boolean = false
)
