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
import com.trencadis.app.media.RawMediaCaptureManager
import com.trencadis.app.ui.BlobModulation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CameraPixelAnalyzer(
    @Volatile private var blockSize: Int = 20,
    private val mirrorHorizontally: Boolean = false,
    private val screenAspectRatio: Float = 9f / 16f, // width/height, portrait default
    @Volatile private var blobModulation: BlobModulation? = null,
    private val onPixelGridReady: (PixelGrid) -> Unit,
    // Optional raw still-image capture support. Both null by default so existing
    // call sites are unaffected; pass both to enable captureStillImage().
    private val mediaCaptureManager: RawMediaCaptureManager? = null,
    private val coroutineScope: CoroutineScope? = null
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

    // Set from the UI thread to request a still capture on the next analyzed frame.
    @Volatile private var captureStillRequested: Boolean = false

    // Once set, analyze() stops decoding new camera frames and keeps re-emitting
    // this same grid at the normal cadence, so the rest of the pipeline (audio
    // parameter mapping, sequencer stepping, visuals) keeps running off the
    // frozen frame exactly as it would off a live one.
    @Volatile private var frozenGrid: PixelGrid? = null

    // The raw bitmap behind the current freeze (camera capture or loaded
    // image), kept around so grid-resolution changes can re-extract the grid
    // at the new blockSize without losing the freeze. Cleared on resume.
    @Volatile private var frozenSourceBitmap: Bitmap? = null

    /**
     * Request that the next analyzed camera frame be saved as a still image
     * AND become the frozen source for the pixel grid (live camera pauses).
     */
    fun captureStillImage() {
        captureStillRequested = true
    }

    /** Resume reading from the live camera feed after a freeze. */
    fun resumeLiveCamera() {
        frozenGrid = null
        frozenSourceBitmap?.recycle()
        frozenSourceBitmap = null
    }

    /**
     * Load an externally picked (and already framed) bitmap as a still image,
     * replacing the live camera feed exactly like [captureStillImage] does for
     * a captured frame — the same crop-to-aspect-ratio, smoothing and blob
     * detection pipeline applies, so all downstream effects work unchanged.
     */
    fun loadStillImage(bitmap: Bitmap) {
        val grid = extractPixelGrid(bitmap, blockSize, resetSmoothing = true, forceBlobs = true)
        replaceFrozenSource(bitmap)
        frozenGrid = grid
        onPixelGridReady(grid)
    }

    /**
     * Change the grid resolution. Safe to call whether live or frozen — while
     * frozen it immediately re-extracts the held still at the new blockSize
     * (no camera rebind needed, since blockSize only affects the software
     * grid extraction below), so resolution changes never drop the freeze.
     */
    fun setBlockSize(newBlockSize: Int) {
        if (newBlockSize == blockSize) return
        blockSize = newBlockSize
        smoothedColors = null
        val bitmap = frozenSourceBitmap ?: return
        val grid = extractPixelGrid(bitmap, blockSize, resetSmoothing = true, forceBlobs = true)
        frozenGrid = grid
        onPixelGridReady(grid)
    }

    /**
     * Hot-swap the blob modulation params. While frozen, re-extracts the held
     * still so blob shapes reflect the new params instead of staying stuck
     * with whatever was detected (or skipped) at freeze time.
     */
    fun setBlobModulation(mod: BlobModulation?) {
        if (mod == blobModulation) return
        blobModulation = mod
        val bitmap = frozenSourceBitmap ?: return
        val grid = extractPixelGrid(bitmap, blockSize, resetSmoothing = true, forceBlobs = true)
        frozenGrid = grid
        onPixelGridReady(grid)
    }

    private fun replaceFrozenSource(bitmap: Bitmap) {
        if (frozenSourceBitmap !== bitmap) {
            frozenSourceBitmap?.recycle()
        }
        frozenSourceBitmap = bitmap
    }

    fun isFrozen(): Boolean = frozenGrid != null
    
    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime < analysisIntervalMs) {
            image.close()
            return
        }
        lastAnalysisTime = currentTime

        val frozen = frozenGrid
        if (frozen != null) {
            // Skip camera decoding entirely while frozen; just keep the
            // downstream pipeline fed with the same static grid.
            onPixelGridReady(frozen)
            image.close()
            return
        }
        
        try {
            val bitmap = imageProxyToBitmap(image)
            if (bitmap != null) {
                // If this frame is about to become the frozen still, force blob
                // detection now regardless of the live feed's CPU-relief cadence
                // — there's no next frame to catch a skipped detection later.
                val willFreeze = captureStillRequested
                val pixelGrid = extractPixelGrid(bitmap, blockSize, forceBlobs = willFreeze)
                onPixelGridReady(pixelGrid)

                if (willFreeze) {
                    captureStillRequested = false
                    captureStill(bitmap)
                    replaceFrozenSource(bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false))
                    frozenGrid = pixelGrid
                }

                bitmap.recycle()
            }
        } catch (e: Exception) {
            // Silently handle errors to avoid crashing
        } finally {
            image.close()
        }
    }

    private fun captureStill(bitmap: Bitmap) {
        val manager = mediaCaptureManager ?: return
        val scope = coroutineScope ?: return
        val stillBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        scope.launch(Dispatchers.IO) {
            try {
                manager.captureStillFrame(stillBitmap)
            } finally {
                stillBitmap.recycle()
            }
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
    
    private fun extractPixelGrid(
        bitmap: Bitmap,
        blockSize: Int,
        resetSmoothing: Boolean = false,
        forceBlobs: Boolean = false
    ): PixelGrid {
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

                // Exponential moving average — smooth across frames. Skipped for
                // one-shot still loads so the new image doesn't blend with
                // whatever the buffer held from the last live camera frame.
                val base = (i * rows + j) * 3
                val sr: Float
                val sg: Float
                val sb: Float
                if (resetSmoothing) {
                    sr = rawR; sg = rawG; sb = rawB
                } else {
                    sr = sc[base]     * smoothFactor + rawR * (1f - smoothFactor)
                    sg = sc[base + 1] * smoothFactor + rawG * (1f - smoothFactor)
                    sb = sc[base + 2] * smoothFactor + rawB * (1f - smoothFactor)
                }
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
            // One-shot still extractions (freeze/load/resolution or blob-param
            // change while frozen) always compute fresh blobs — there's no
            // next frame to catch up on a skipped detection like there is
            // for the live feed's every-Nth-frame CPU-relief cadence.
            val blobs = if (forceBlobs || frameCount % blobFrameInterval == 0) {
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
