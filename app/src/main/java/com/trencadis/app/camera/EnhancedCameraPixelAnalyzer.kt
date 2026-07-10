package com.trencadis.app.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.trencadis.app.media.RawMediaCaptureManager
import com.trencadis.app.media.MediaMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import android.graphics.BitmapFactory
import com.trencadis.app.ui.BlobModulation

/**
 * Enhanced CameraPixelAnalyzer with media capture integration for Trencadis 2.0
 * Extends the original analyzer to support still image capture and video recording
 */
class EnhancedCameraPixelAnalyzer(
    private val blockSize: Int = 20,
    private val mirrorHorizontally: Boolean = false,
    private val screenAspectRatio: Float = 9f / 16f,
    @Volatile var blobModulation: BlobModulation? = null,
    private val onPixelGridReady: (PixelGrid) -> Unit,
    private val mediaCaptureManager: RawMediaCaptureManager,
    private val coroutineScope: CoroutineScope
) : ImageAnalysis.Analyzer {
    
    private var lastAnalysisTime = 0L
    private val analysisIntervalMs = 33L // ~30fps
    
    // Temporal smoothing: blend pixel RGB with previous frame's values
    private var smoothedColors: FloatArray? = null
    private val smoothFactor = 0.60f
    
    // Blob detection runs every 2 frames for CPU relief
    private var frameCount = 0
    private val blobFrameInterval = 2
    private var cachedBlobs: List<BlobDetector.PixelBlob> = emptyList()
    
    // Media capture state
    private var currentBitmap: Bitmap? = null
    private var videoRecordingSession: com.trencadis.app.media.VideoRecordingSession? = null
    
    // Capture request flags
    @Volatile var captureStillRequested: Boolean = false
    @Volatile var startRecordingRequested: Boolean = false
    @Volatile var stopRecordingRequested: Boolean = false
    
    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime < analysisIntervalMs) {
            image.close()
            return
        }
        lastAnalysisTime = currentTime
        
        try {
            val bitmap = imageProxyToBitmap(image)
            if (bitmap != null) {
                // Store current bitmap for capture operations
                currentBitmap = bitmap
                
                // Handle media capture requests
                handleMediaCaptureRequests(bitmap)
                
                // Only process pixel grid if not in media-only mode
                if (mediaCaptureManager.captureState.value.mode != MediaMode.VIDEO_RECORDING) {
                    val pixelGrid = extractPixelGrid(bitmap, blockSize)
                    onPixelGridReady(pixelGrid)
                } else {
                    // During video recording, still provide pixel grid for preview
                    val pixelGrid = extractPixelGrid(bitmap, blockSize)
                    onPixelGridReady(pixelGrid)
                }
                
                // Don't recycle if we might need it for capture
                if (!captureStillRequested && videoRecordingSession == null) {
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            // Silently handle errors to avoid crashing
        } finally {
            image.close()
        }
    }
    
    /**
     * Handle media capture requests (still image and video)
     */
    private fun handleMediaCaptureRequests(bitmap: Bitmap) {
        // Handle still image capture
        if (captureStillRequested) {
            captureStillRequested = false
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Create a copy for capture to avoid recycling issues
                    val captureBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                    mediaCaptureManager.captureStillFrame(captureBitmap)
                    captureBitmap.recycle()
                } catch (e: Exception) {
                    // Handle capture error
                }
            }
        }
        
        // Handle video recording start
        if (startRecordingRequested) {
            startRecordingRequested = false
            videoRecordingSession = mediaCaptureManager.startVideoRecording()
        }
        
        // Handle video recording stop
        if (stopRecordingRequested && videoRecordingSession != null) {
            stopRecordingRequested = false
            val session = videoRecordingSession
            videoRecordingSession = null
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    mediaCaptureManager.stopRecording(session!!)
                } catch (e: Exception) {
                    // Handle recording stop error
                }
            }
        }
        
        // Handle video frame recording (if actively recording)
        videoRecordingSession?.let { session ->
            // TODO: Implement video frame encoding
            // This would involve encoding the bitmap to the video file
            // For now, we'll just track that recording is happening
        }
    }
    
    /**
     * Request still image capture on next frame
     */
    fun captureStillImage() {
        captureStillRequested = true
    }
    
    /**
     * Start video recording
     */
    fun startVideoRecording() {
        startRecordingRequested = true
    }
    
    /**
     * Stop video recording
     */
    fun stopVideoRecording() {
        stopRecordingRequested = true
    }
    
    /**
     * Check if currently recording video
     */
    fun isRecordingVideo(): Boolean {
        return videoRecordingSession != null
    }
    
    /**
     * Get current frame bitmap (for preview/thumbnail purposes)
     */
    fun getCurrentFrame(): Bitmap? {
        return currentBitmap?.let { it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) }
    }
    
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            when (image.format) {
                ImageFormat.YUV_420_888 -> yuv420ToBitmap(image)
                ImageFormat.JPEG -> jpegToBitmap(image)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun yuv420ToBitmap(image: ImageProxy): Bitmap? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 80, out)
        
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    
    private fun jpegToBitmap(image: ImageProxy): Bitmap? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    
    private fun extractPixelGrid(bitmap: Bitmap, blockSize: Int): PixelGrid {
        val width = bitmap.width
        val height = bitmap.height
        val cols = width / blockSize
        val rows = height / blockSize
        
        val pixels = mutableListOf<PixelData>()
        
        // Initialize smoothed colors if needed
        if (smoothedColors == null) {
            smoothedColors = FloatArray(cols * rows * 3)
        }
        
        frameCount++
        val runBlobDetection = (frameCount % blobFrameInterval == 0)
        
        // Extract pixel data
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = col * blockSize
                val y = row * blockSize
                
                // Get average color for this block
                val blockColors = IntArray(blockSize * blockSize)
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
                
                // Apply temporal smoothing
                val colorIndex = (row * cols + col) * 3
                val smoothedR = smoothedColors!![colorIndex] * smoothFactor + avgR * (1 - smoothFactor)
                val smoothedG = smoothedColors!![colorIndex + 1] * smoothFactor + avgG * (1 - smoothFactor)
                val smoothedB = smoothedColors!![colorIndex + 2] * smoothFactor + avgB * (1 - smoothFactor)
                
                smoothedColors!![colorIndex] = smoothedR
                smoothedColors!![colorIndex + 1] = smoothedG
                smoothedColors!![colorIndex + 2] = smoothedB
                
                // Convert to HSB
                val max = maxOf(smoothedR, smoothedG, smoothedB)
                val min = minOf(smoothedR, smoothedG, smoothedB)
                val delta = max - min
                
                val brightness = max
                val saturation = if (max == 0f) 0f else delta / max
                
                var hue = 0f
                if (delta != 0f) {
                    hue = when (max) {
                        smoothedR -> (smoothedG - smoothedB) / delta + if (smoothedG < smoothedB) 6f else 0f
                        smoothedG -> (smoothedB - smoothedR) / delta + 2f
                        smoothedB -> (smoothedR - smoothedG) / delta + 4f
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
                    red = smoothedR,
                    green = smoothedG,
                    blue = smoothedB
                )
                
                pixels.add(pixelData)
            }
        }
        
        // Run blob detection if needed
        val blobs = if (runBlobDetection && blobModulation != null) {
            val grid = PixelGrid(cols, rows, blockSize, pixels, emptyList())
            cachedBlobs = BlobDetector.detectBlobs(grid, blobModulation!!)
            cachedBlobs
        } else {
            cachedBlobs
        }
        
        return PixelGrid(
            cols = cols,
            rows = rows,
            blockSize = blockSize,
            pixels = pixels,
            blobs = blobs
        )
    }
}
