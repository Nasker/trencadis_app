package com.example.trencadisapp.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory

class CameraPixelAnalyzer(
    private val blockSize: Int = 20,
    private val mirrorHorizontally: Boolean = false,
    private val onPixelGridReady: (PixelGrid) -> Unit
) : ImageAnalysis.Analyzer {
    
    private var lastAnalysisTime = 0L
    private val analysisIntervalMs = 33L // ~30fps
    
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
        val cols = bitmap.width / blockSize
        val rows = bitmap.height / blockSize
        
        val pixels = mutableListOf<PixelData>()
        
        for (i in 0 until cols) {
            for (j in 0 until rows) {
                val x = i * blockSize + blockSize / 2
                val y = j * blockSize + blockSize / 2
                
                // Clamp to bitmap bounds
                val safeX = x.coerceIn(0, bitmap.width - 1)
                val safeY = y.coerceIn(0, bitmap.height - 1)
                
                val argb = bitmap.getPixel(safeX, safeY)
                pixels.add(PixelData.fromArgb(i, j, argb))
            }
        }
        
        return PixelGrid(
            cols = cols,
            rows = rows,
            blockSize = blockSize,
            pixels = pixels
        )
    }
}
