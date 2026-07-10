package com.trencadis.app.media

import android.graphics.Bitmap
import com.trencadis.app.camera.CameraPixelAnalyzer
import com.trencadis.app.camera.PixelData
import com.trencadis.app.camera.PixelGrid
import com.trencadis.app.camera.PixelSelectionMode
import com.trencadis.app.ui.BlobModulation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Static image playback engine for Trencadis 2.0
 * Converts captured images into playable pixel grids for audio synthesis
 */
class StaticImagePlayer {
    
    private val _playbackState = MutableStateFlow(StaticImagePlaybackState())
    val playbackState = _playbackState.asStateFlow()
    
    private var currentPixelGrid: PixelGrid? = null
    private var currentBitmap: Bitmap? = null
    
    // Playback parameters
    private var isPlaying: Boolean = false
    private var selectionMode: PixelSelectionMode = PixelSelectionMode.SEQUENCE
    private var sequenceIndex: Int = 0
    private var autoAdvance: Boolean = true
    private var advanceInterval: Long = 500L // ms between pixel changes
    
    /**
     * Load a static image for playback
     */
    suspend fun loadImage(bitmap: Bitmap): Result<PixelGrid> {
        return try {
            currentBitmap = bitmap
            val pixelGrid = convertBitmapToPixelGrid(bitmap)
            currentPixelGrid = pixelGrid
            
            _playbackState.value = _playbackState.value.copy(
                isLoaded = true,
                pixelGrid = pixelGrid,
                selectedPixel = null,
                isLoading = false
            )
            
            Result.success(pixelGrid)
        } catch (e: Exception) {
            _playbackState.value = _playbackState.value.copy(
                isLoading = false,
                error = e.message
            )
            Result.failure(e)
        }
    }
    
    /**
     * Start audio synthesis playback
     */
    fun startPlayback() {
        if (currentPixelGrid == null) return
        
        isPlaying = true
        _playbackState.value = _playbackState.value.copy(
            isPlaying = true
        )
        
        if (autoAdvance) {
            startAutoAdvance()
        }
    }
    
    /**
     * Stop audio synthesis playback
     */
    fun stopPlayback() {
        isPlaying = false
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false
        )
    }
    
    /**
     * Select a specific pixel for audio synthesis
     */
    fun selectPixel(pixel: PixelData) {
        _playbackState.value = _playbackState.value.copy(
            selectedPixel = pixel
        )
    }
    
    /**
     * Select pixel by grid coordinates
     */
    fun selectPixelAt(gridX: Int, gridY: Int) {
        currentPixelGrid?.let { grid ->
            val pixel = grid.pixels.find { 
                it.gridX == gridX && it.gridY == gridY 
            }
            pixel?.let { selectPixel(it) }
        }
    }
    
    /**
     * Set pixel selection mode
     */
    fun setSelectionMode(mode: PixelSelectionMode) {
        selectionMode = mode
        _playbackState.value = _playbackState.value.copy(
            selectionMode = mode
        )
    }
    
    /**
     * Set auto-advance behavior
     */
    fun setAutoAdvance(enabled: Boolean, intervalMs: Long = 500L) {
        autoAdvance = enabled
        advanceInterval = intervalMs
        _playbackState.value = _playbackState.value.copy(
            autoAdvance = enabled,
            advanceInterval = intervalMs
        )
    }
    
    /**
     * Get next pixel based on selection mode
     */
    fun getNextPixel(): PixelData? {
        val grid = currentPixelGrid ?: return null
        
        return when (selectionMode) {
            PixelSelectionMode.SEQUENCE -> {
                val pixels = grid.pixels.sortedBy { it.gridY * grid.cols + it.gridX }
                sequenceIndex = (sequenceIndex + 1) % pixels.size
                pixels[sequenceIndex]
            }
            
            PixelSelectionMode.BRIGHTEST -> {
                grid.pixels.maxByOrNull { it.brightness }
            }
            
            PixelSelectionMode.CENTER -> {
                val centerX = grid.cols / 2
                val centerY = grid.rows / 2
                grid.pixels.find { 
                    it.gridX == centerX && it.gridY == centerY 
                }
            }
            
            PixelSelectionMode.POINTER -> {
                _playbackState.value.selectedPixel
            }
        }
    }
    
    /**
     * Get current pixel grid
     */
    fun getCurrentPixelGrid(): PixelGrid? {
        return currentPixelGrid
    }
    
    /**
     * Get current selected pixel
     */
    fun getCurrentPixel(): PixelData? {
        return _playbackState.value.selectedPixel
    }
    
    /**
     * Unload current image
     */
    fun unloadImage() {
        currentBitmap?.recycle()
        currentBitmap = null
        currentPixelGrid = null
        isPlaying = false
        
        _playbackState.value = StaticImagePlaybackState()
    }
    
    /**
     * Check if image is loaded
     */
    fun isImageLoaded(): Boolean {
        return _playbackState.value.isLoaded
    }
    
    /**
     * Check if playback is active
     */
    fun isPlaybackActive(): Boolean {
        return isPlaying
    }
    
    /**
     * Get image information
     */
    fun getImageInfo(): ImageInfo? {
        return currentBitmap?.let { bitmap ->
            ImageInfo(
                width = bitmap.width,
                height = bitmap.height,
                pixelCount = currentPixelGrid?.pixels?.size ?: 0,
                cols = currentPixelGrid?.cols ?: 0,
                rows = currentPixelGrid?.rows ?: 0
            )
        }
    }
    
    /**
     * Convert bitmap to pixel grid using same logic as camera analyzer
     */
    private suspend fun convertBitmapToPixelGrid(bitmap: Bitmap): PixelGrid {
        val blockSize = 20 // Same as camera analyzer
        val width = bitmap.width
        val height = bitmap.height
        val cols = width / blockSize
        val rows = height / blockSize
        
        val pixels = mutableListOf<PixelData>()
        
        // Extract pixel data (simplified version without temporal smoothing)
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
                
                val pixelData = PixelData(
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
        
        return PixelGrid(
            cols = cols,
            rows = rows,
            blockSize = blockSize,
            pixels = pixels,
            blobs = blobs
        )
    }
    
    /**
     * Start auto-advance timer for sequence mode
     */
    private fun startAutoAdvance() {
        // TODO: Implement auto-advance timer using coroutines
        // This would periodically call getNextPixel() and update selected pixel
    }
}

/**
 * Static image playback state
 */
data class StaticImagePlaybackState(
    val isLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val pixelGrid: PixelGrid? = null,
    val selectedPixel: PixelData? = null,
    val selectionMode: PixelSelectionMode = PixelSelectionMode.SEQUENCE,
    val autoAdvance: Boolean = true,
    val advanceInterval: Long = 500L,
    val error: String? = null
)

/**
 * Image information
 */
data class ImageInfo(
    val width: Int,
    val height: Int,
    val pixelCount: Int,
    val cols: Int,
    val rows: Int
)
