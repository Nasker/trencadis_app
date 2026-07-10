package com.trencadis.app.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import com.trencadis.app.ui.BlobModulation

class CameraPixelAnalyzer(
    private val blockSize: Int = 20,
    private val mirrorHorizontally: Boolean = false,
    private val screenAspectRatio: Float = 9f / 16f, // width/height, portrait default
    @Volatile var blobModulation: BlobModulation? = null,
    private val onPixelGridReady: (PixelGrid) -> Unit
) : ImageAnalysis.Analyzer {
    
    private var lastAnalysisTime = 0L
    private val analysisIntervalMs = 33L // ~30fps

    // Temporal smoothing: blend pixel RGB with previous frame's values.
    // Shapes evolve gradually rather than jumping, removing twitchiness.
    private var smoothedColors: FloatArray? = null  // R,G,B interleaved per cell
    private val smoothFactor = 0.60f               // 0=no smoothing, 1=fully frozen

    // Blob detection runs every 2 frames for CPU relief (smoothing keeps transitions fluid).
    private var frameCount = 0
    private val blobFrameInterval = 2
    private var cachedBlobs: List<BlobDetector.PixelBlob> = emptyList()
    
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
                val pixelGrid = extractPixelGrid(bitmap, blockSize)
                onPixelGridReady(pixelGrid)
                bitmap.recycle()
            }
        } catch (e: Exception) {
            // Silently handle errors to avoid crashing
        } finally {
            image.close()
        }
    }
    
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            when (image.format) {
                ImageFormat.YUV_420_888 -> yuvToBitmap(image)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun yuvToBitmap(image: ImageProxy): Bitmap? {
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
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
        val imageBytes = out.toByteArray()
        
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        
        // Apply rotation from camera sensor and optional horizontal mirror for front camera
        val rotationDegrees = image.imageInfo.rotationDegrees
        val matrix = Matrix().apply {
            // Rotate to correct orientation
            postRotate(rotationDegrees.toFloat())
            // Mirror horizontally for front camera (after rotation)
            if (mirrorHorizontally) {
                postScale(-1f, 1f)
            }
        }
        
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    private fun extractPixelGrid(bitmap: Bitmap, blockSize: Int): PixelGrid {
        // blockSize controls grid density along the longer screen axis to minimise
        // integer rounding error. screenAspectRatio = screenWidth / screenHeight.
        // Portrait  (ratio < 1): height is longer → blockSize = rows, cols = rows * ratio
        // Landscape (ratio > 1): width  is longer → blockSize = cols, rows = cols / ratio
        val cols: Int
        val rows: Int
        if (screenAspectRatio <= 1f) {
            // Portrait / square: height is the longer axis
            rows = blockSize.coerceAtLeast(1)
            cols = (rows * screenAspectRatio + 0.5f).toInt().coerceAtLeast(1)
        } else {
            // Landscape: width is the longer axis
            cols = blockSize.coerceAtLeast(1)
            rows = (cols / screenAspectRatio + 0.5f).toInt().coerceAtLeast(1)
        }

        // Center-crop the bitmap to match the grid (canvas) aspect ratio so the image
        // is never stretched or squashed. We sample only from a centered sub-rectangle
        // whose proportions equal cols/rows, cropping the sides or top/bottom as needed.
        val gridRatio = cols.toFloat() / rows.toFloat()           // width/height of grid
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

        val cropWidth: Float
        val cropHeight: Float
        if (bitmapRatio > gridRatio) {
            // Bitmap is wider than the grid → crop the sides (reduce width)
            cropHeight = bitmap.height.toFloat()
            cropWidth = cropHeight * gridRatio
        } else {
            // Bitmap is taller than the grid → crop top/bottom (reduce height)
            cropWidth = bitmap.width.toFloat()
            cropHeight = cropWidth / gridRatio
        }
        val cropOffsetX = (bitmap.width - cropWidth) / 2f
        val cropOffsetY = (bitmap.height - cropHeight) / 2f

        val cellCount = cols * rows
        // Allocate (or reset) the smoothing buffer when grid dimensions change
        val sc = smoothedColors?.takeIf { it.size == cellCount * 3 }
            ?: FloatArray(cellCount * 3).also { smoothedColors = it }

        val pixels = mutableListOf<PixelData>()

        for (i in 0 until cols) {
            for (j in 0 until rows) {
                // Map grid cell centre into the centered crop rectangle of the bitmap
                val bx = (cropOffsetX + (i + 0.5f) / cols * cropWidth).toInt()
                    .coerceIn(0, bitmap.width - 1)
                val by = (cropOffsetY + (j + 0.5f) / rows * cropHeight).toInt()
                    .coerceIn(0, bitmap.height - 1)

                val argb = bitmap.getPixel(bx, by)
                val rawR = android.graphics.Color.red(argb)   / 255f
                val rawG = android.graphics.Color.green(argb) / 255f
                val rawB = android.graphics.Color.blue(argb)  / 255f

                // Exponential moving average — smooth across frames
                val base = (i * rows + j) * 3
                val sr = sc[base]     * smoothFactor + rawR * (1f - smoothFactor)
                val sg = sc[base + 1] * smoothFactor + rawG * (1f - smoothFactor)
                val sb = sc[base + 2] * smoothFactor + rawB * (1f - smoothFactor)
                sc[base] = sr; sc[base + 1] = sg; sc[base + 2] = sb

                val smoothedArgb = android.graphics.Color.rgb(
                    (sr * 255).toInt(), (sg * 255).toInt(), (sb * 255).toInt()
                )
                pixels.add(PixelData.fromArgb(i, j, smoothedArgb))
            }
        }

        val grid = PixelGrid(
            cols = cols,
            rows = rows,
            blockSize = blockSize,
            pixels = pixels
        )

        val mod = blobModulation
        return if (mod != null) {
            frameCount++
            val blobs = if (frameCount % blobFrameInterval == 0) {
                BlobDetector.detectBlobs(grid, mod).also { cachedBlobs = it }
            } else {
                cachedBlobs
            }
            grid.copy(blobs = blobs)
        } else {
            if (cachedBlobs.isNotEmpty()) cachedBlobs = emptyList()
            grid
        }
    }
}
