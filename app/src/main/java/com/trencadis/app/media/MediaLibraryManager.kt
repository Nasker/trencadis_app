package com.trencadis.app.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Media library manager for Trencadis 2.0
 * Handles browsing, organizing, and managing captured media files
 */
class MediaLibraryManager(private val context: Context) {
    
    private val _libraryState = MutableStateFlow(MediaLibraryState())
    val libraryState = _libraryState.asStateFlow()
    
    private val mediaCaptureManager: RawMediaCaptureManager
    
    init {
        mediaCaptureManager = RawMediaCaptureManager(context)
        // Observe media capture state changes
        observeMediaCaptureState()
    }
    
    /**
     * Load media library from storage
     */
    suspend fun loadLibrary() {
        _libraryState.update { it.copy(isLoading = true) }
        
        try {
            val captureState = mediaCaptureManager.captureState.value
            _libraryState.update { 
                it.copy(
                    isLoading = false,
                    mediaLibrary = captureState.library,
                    stills = captureState.library.stills,
                    videos = captureState.library.videos,
                    favorites = captureState.library.favorites.mapNotNull { id ->
                        captureState.library.stills.find { it.id == id } ?: 
                        captureState.library.videos.find { it.id == id }
                    }
                )
            }
        } catch (e: Exception) {
            _libraryState.update { 
                it.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * Refresh library from storage
     */
    suspend fun refreshLibrary() {
        loadLibrary()
    }
    
    /**
     * Get all media files
     */
    fun getAllMedia(): List<MediaFile> {
        val state = _libraryState.value
        return state.stills + state.videos
    }
    
    /**
     * Get media files by type
     */
    fun getMediaByType(type: MediaType): List<MediaFile> {
        return when (type) {
            MediaType.STILL_IMAGE -> _libraryState.value.stills
            MediaType.VIDEO -> _libraryState.value.videos
        }
    }
    
    /**
     * Get favorite media files
     */
    fun getFavorites(): List<MediaFile> {
        return _libraryState.value.favorites
    }
    
    /**
     * Get recent media files (last 10)
     */
    fun getRecentMedia(): List<MediaFile> {
        return getAllMedia()
            .sortedByDescending { it.createdAt }
            .take(10)
    }
    
    /**
     * Search media files by name
     */
    fun searchMedia(query: String): List<MediaFile> {
        if (query.isBlank()) return getAllMedia()
        
        val lowercaseQuery = query.lowercase()
        return getAllMedia().filter { 
            it.name.lowercase().contains(lowercaseQuery) ||
            it.id.lowercase().contains(lowercaseQuery)
        }
    }
    
    /**
     * Toggle favorite status for a media file
     */
    suspend fun toggleFavorite(mediaFile: MediaFile) {
        mediaCaptureManager.toggleFavorite(mediaFile.id)
        // Library will be updated through observation
    }
    
    /**
     * Delete a media file
     */
    suspend fun deleteMedia(mediaFile: MediaFile): Boolean {
        return try {
            mediaCaptureManager.deleteMediaFile(mediaFile)
            true
        } catch (e: Exception) {
            _libraryState.update { 
                it.copy(error = "Failed to delete media: ${e.message}")
            }
            false
        }
    }
    
    /**
     * Rename a media file
     */
    suspend fun renameMedia(mediaFile: MediaFile, newName: String): Boolean {
        return try {
            // TODO: Implement file renaming
            // This would involve renaming the actual file and updating metadata
            false
        } catch (e: Exception) {
            _libraryState.update { 
                it.copy(error = "Failed to rename media: ${e.message}")
            }
            false
        }
    }
    
    /**
     * Get media file by ID
     */
    fun getMediaById(id: String): MediaFile? {
        return getAllMedia().find { it.id == id }
    }
    
    /**
     * Get media statistics
     */
    fun getMediaStats(): MediaStats {
        val state = _libraryState.value
        val totalSize = getAllMedia().sumOf { it.fileSize }
        val totalDuration = state.videos.sumOf { it.durationMs }
        
        return MediaStats(
            totalFiles = getAllMedia().size,
            stillCount = state.stills.size,
            videoCount = state.videos.size,
            favoriteCount = state.favorites.size,
            totalSizeBytes = totalSize,
            totalDurationMs = totalDuration
        )
    }
    
    /**
     * Set view mode for media library
     */
    fun setViewMode(mode: ViewMode) {
        _libraryState.update { it.copy(viewMode = mode) }
    }
    
    /**
     * Set sort order for media library
     */
    fun setSortOrder(order: SortOrder) {
        _libraryState.update { it.copy(sortOrder = order) }
        applySorting()
    }
    
    /**
     * Set filter for media library
     */
    fun setFilter(filter: MediaFilter) {
        _libraryState.update { it.copy(filter = filter) }
        applyFiltering()
    }
    
    /**
     * Get filtered and sorted media list
     */
    fun getFilteredMedia(): List<MediaFile> {
        var media = getAllMedia()
        
        // Apply type filter
        if (_libraryState.value.filter.mediaTypes.isNotEmpty()) {
            media = media.filter { it.type in _libraryState.value.filter.mediaTypes }
        }
        
        // Apply favorite filter
        if (_libraryState.value.filter.favoritesOnly) {
            media = media.filter { it.id in _libraryState.value.mediaLibrary.favorites }
        }
        
        // Apply date range filter
        _libraryState.value.filter.dateRange?.let { range ->
            media = media.filter { 
                it.createdAt >= range.first && it.createdAt <= range.second 
            }
        }
        
        // Apply search query
        if (_libraryState.value.filter.searchQuery.isNotBlank()) {
            media = searchMedia(_libraryState.value.filter.searchQuery)
        }
        
        // Apply sorting
        media = when (_libraryState.value.sortOrder) {
            SortOrder.NAME_ASC -> media.sortedBy { it.name }
            SortOrder.NAME_DESC -> media.sortedByDescending { it.name }
            SortOrder.DATE_ASC -> media.sortedBy { it.createdAt }
            SortOrder.DATE_DESC -> media.sortedByDescending { it.createdAt }
            SortOrder.SIZE_ASC -> media.sortedBy { it.fileSize }
            SortOrder.SIZE_DESC -> media.sortedByDescending { it.fileSize }
            SortOrder.TYPE -> media.sortedBy { it.type.name }
        }
        
        return media
    }
    
    /**
     * Import media from gallery
     */
    suspend fun importFromGallery(uri: Uri): Result<MediaFile> {
        return try {
            // TODO: Implement gallery import
            // This would copy the file from gallery to app's media directory
            Result.failure(NotImplementedError("Gallery import not yet implemented"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Export media to external storage
     */
    suspend fun exportMedia(mediaFile: MediaFile, destination: Uri): Result<Boolean> {
        return try {
            // TODO: Implement media export
            // This would copy the file to external storage
            Result.failure(NotImplementedError("Media export not yet implemented"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _libraryState.update { it.copy(error = null) }
    }
    
    /**
     * Observe media capture state changes
     */
    private fun observeMediaCaptureState() {
        // TODO: Implement state observation
        // This would observe the media capture manager's state flow
        // and update the library state accordingly
    }
    
    /**
     * Apply current sorting to media lists
     */
    private fun applySorting() {
        // Sorting is applied in getFilteredMedia()
    }
    
    /**
     * Apply current filtering to media lists
     */
    private fun applyFiltering() {
        // Filtering is applied in getFilteredMedia()
    }
}

/**
 * Media library state
 */
data class MediaLibraryState(
    val isLoading: Boolean = false,
    val mediaLibrary: MediaLibrary = MediaLibrary(),
    val stills: List<MediaFile> = emptyList(),
    val videos: List<MediaFile> = emptyList(),
    val favorites: List<MediaFile> = emptyList(),
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val filter: MediaFilter = MediaFilter(),
    val error: String? = null
)

/**
 * View modes for media library
 */
enum class ViewMode {
    GRID,
    LIST,
    CAROUSEL
}

/**
 * Sort orders for media library
 */
enum class SortOrder {
    NAME_ASC,
    NAME_DESC,
    DATE_ASC,
    DATE_DESC,
    SIZE_ASC,
    SIZE_DESC,
    TYPE
}

/**
 * Media filter options
 */
data class MediaFilter(
    val mediaTypes: Set<MediaType> = emptySet(),
    val favoritesOnly: Boolean = false,
    val dateRange: Pair<Long, Long>? = null, // Start and end timestamps
    val searchQuery: String = ""
)

/**
 * Media statistics
 */
data class MediaStats(
    val totalFiles: Int,
    val stillCount: Int,
    val videoCount: Int,
    val favoriteCount: Int,
    val totalSizeBytes: Long,
    val totalDurationMs: Long
) {
    val totalSizeMB: Float
        get() = totalSizeBytes / (1024f * 1024f)
    
    val totalDurationSeconds: Float
        get() = totalDurationMs / 1000f
    
    val averageFileSizeMB: Float
        get() = if (totalFiles > 0) totalSizeMB / totalFiles else 0f
}
