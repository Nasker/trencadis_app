package com.example.trencadisapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import kotlin.math.cos
import com.example.trencadisapp.camera.PixelData
import com.example.trencadisapp.camera.PixelGrid
import com.example.trencadisapp.camera.PixelSelectionMode
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun CubistCanvas(
    pixelGrid: PixelGrid?,
    selectedPixel: PixelData?,
    selectionMode: PixelSelectionMode,
    cutoffValue: Float,
    acidModulation: AcidModulation = AcidModulation(),
    acidPatternIndex: Int = 9,
    modifier: Modifier = Modifier,
    onTouch: (Float, Float, Boolean, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onDoubleTap: () -> Unit = {},
    onEdgeDrag: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> }
) {
    // Create and remember the acid pattern generator
    val acidPattern = remember { AcidPattern() }
    
    // Pre-calculate and cache reusable Path objects for complex shapes
    val trianglePath = remember { Path() }
    val diamondPath = remember { Path() }
    val hexPath = remember { Path() }
    val starPath = remember { Path() }
    
    // Pre-calculated trig values for hexagon (6 points) and star (8 points)
    val hexCos = remember { FloatArray(6) { cos((PI / 3 * it - PI / 2).toFloat()) } }
    val hexSin = remember { FloatArray(6) { sin((PI / 3 * it - PI / 2).toFloat()) } }
    val starCos = remember { FloatArray(8) { cos((PI / 4 * it - PI / 2).toFloat()) } }
    val starSin = remember { FloatArray(8) { sin((PI / 4 * it - PI / 2).toFloat()) } }
    
    // Update pattern type when index changes
    remember(acidPatternIndex) {
        if (acidPatternIndex in AcidPattern.PATTERN_NAMES.indices) {
            acidPattern.setPattern(AcidPattern.PATTERN_NAMES[acidPatternIndex].second)
        }
        true
    }
    
    // Tick the animation if acid is enabled
    if (acidModulation.enabled) {
        acidPattern.tick(acidModulation.animationSpeed)
    }
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onPress = { offset ->
                        onTouch(offset.x, offset.y, true, size.width.toFloat(), size.height.toFloat())
                        tryAwaitRelease()
                        onTouch(offset.x, offset.y, false, size.width.toFloat(), size.height.toFloat())
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onTouch(offset.x, offset.y, true, size.width.toFloat(), size.height.toFloat())
                    },
                    onDrag = { change, _ ->
                        val x = change.position.x
                        val y = change.position.y
                        onTouch(x, y, true, size.width.toFloat(), size.height.toFloat())
                        // Also notify edge drag for panel detection
                        onEdgeDrag(x, y, size.width.toFloat(), size.height.toFloat())
                    },
                    onDragEnd = {
                        onTouch(0f, 0f, false, size.width.toFloat(), size.height.toFloat())
                    },
                    onDragCancel = {
                        onTouch(0f, 0f, false, size.width.toFloat(), size.height.toFloat())
                    }
                )
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // Draw background based on selected pixel
        val bgColor = if (selectedPixel != null) {
            when (selectionMode) {
                PixelSelectionMode.SEQUENCE, PixelSelectionMode.BRIGHTEST -> {
                    Color(
                        selectedPixel.red,
                        selectedPixel.green,
                        selectedPixel.blue,
                        0.22f
                    )
                }
                else -> {
                    Color(selectedPixel.hue / 360f, 0.3f, 0.2f, 1f)
                }
            }
        } else {
            Color.Black
        }
        drawRect(bgColor)
        
        // Draw pixel grid - sorted by brightness (dark first, bright on top for 3D effect)
        pixelGrid?.let { grid ->
            val blockWidth = canvasWidth / grid.cols
            val blockHeight = canvasHeight / grid.rows
            
            // Sort pixels by brightness: dark pixels drawn first (back), bright pixels last (front)
            val sortedPixels = grid.pixels.sortedBy { it.brightness }
            
            for (pixel in sortedPixels) {
                drawCubistShapeOptimized(
                    pixel = pixel,
                    blockWidth = blockWidth,
                    blockHeight = blockHeight,
                    acidPattern = acidPattern,
                    acidModulation = acidModulation,
                    trianglePath = trianglePath,
                    diamondPath = diamondPath,
                    hexPath = hexPath,
                    starPath = starPath,
                    hexCos = hexCos,
                    hexSin = hexSin,
                    starCos = starCos,
                    starSin = starSin
                )
            }
            
            // Draw selected pixel highlight
            selectedPixel?.let { selected ->
                drawSelectedHighlight(
                    pixel = selected,
                    blockWidth = blockWidth,
                    blockHeight = blockHeight
                )
            }
        }
    }
}

// Optimized version - no gradient brushes, reuses paths, uses pre-calculated trig
private fun DrawScope.drawCubistShapeOptimized(
    pixel: PixelData,
    blockWidth: Float,
    blockHeight: Float,
    acidPattern: AcidPattern,
    acidModulation: AcidModulation,
    trianglePath: Path,
    diamondPath: Path,
    hexPath: Path,
    starPath: Path,
    hexCos: FloatArray,
    hexSin: FloatArray,
    starCos: FloatArray,
    starSin: FloatArray
) {
    val x = pixel.gridX * blockWidth + blockWidth / 2
    val y = pixel.gridY * blockHeight + blockHeight / 2
    
    val brightness = pixel.brightness
    var hue = pixel.hue
    
    // Map brightness to size multiplier (0-10 like original)
    val brightMap = brightness * 10f
    
    // Get acid pattern angle for this pixel (only if enabled)
    val acidAngle = if (acidModulation.enabled) {
        acidPattern.getAnimatedAngle(pixel.gridX, pixel.gridY, acidModulation.animationSpeed)
    } else 0f
    
    // Apply acid hue modulation - blend between original hue and acid-generated hue
    if (acidModulation.enabled && acidModulation.hueAmount > 0f) {
        val acidHue = acidPattern.getHueModulation(acidAngle)
        hue = hue * (1f - acidModulation.hueAmount) + acidHue * acidModulation.hueAmount
    }
    
    // Rotation based on hue + acid modulation
    var rotation = (hue / 360f) * 54f  // Simplified: always 0.6 factor, max 54 degrees
    
    // Add acid rotation modulation
    if (acidModulation.enabled && acidModulation.rotationAmount > 0f) {
        rotation += acidPattern.getRotationModulation(acidAngle, 90f * acidModulation.rotationAmount)
    }
    
    // OPTIMIZED: Skip HSV conversion when acid hue modulation is off
    // Just boost saturation directly in RGB space (approximate but fast)
    val r: Float
    val g: Float
    val b: Float
    var alpha = 0.5f + brightness * 0.45f
    
    if (acidModulation.enabled && acidModulation.hueAmount > 0f) {
        // Need HSV for hue shift - use it
        val hsv = floatArrayOf(hue, 0f, 0f)
        android.graphics.Color.RGBToHSV(
            (pixel.red * 255).toInt(),
            (pixel.green * 255).toInt(),
            (pixel.blue * 255).toInt(),
            hsv
        )
        hsv[0] = hue
        hsv[1] = (hsv[1] * 1.6f).coerceAtMost(1f)
        hsv[2] = (hsv[2] * 1.1f).coerceAtMost(1f)
        val boostedArgb = android.graphics.Color.HSVToColor(hsv)
        r = android.graphics.Color.red(boostedArgb) / 255f
        g = android.graphics.Color.green(boostedArgb) / 255f
        b = android.graphics.Color.blue(boostedArgb) / 255f
    } else {
        // Fast path: approximate saturation boost in RGB
        val avg = (pixel.red + pixel.green + pixel.blue) / 3f
        val satBoost = 1.6f
        r = (avg + (pixel.red - avg) * satBoost).coerceIn(0f, 1f)
        g = (avg + (pixel.green - avg) * satBoost).coerceIn(0f, 1f)
        b = (avg + (pixel.blue - avg) * satBoost).coerceIn(0f, 1f)
    }
    
    // Apply acid alpha modulation
    if (acidModulation.enabled && acidModulation.alphaAmount > 0f) {
        val acidAlpha = acidPattern.getAlphaModulation(acidAngle, 0.3f, 1f)
        alpha = alpha * (1f - acidModulation.alphaAmount) + acidAlpha * acidModulation.alphaAmount
    }
    
    val color = Color(r, g, b, alpha)
    
    // Calculate acid size modulation
    val acidSizeMod = if (acidModulation.enabled && acidModulation.sizeAmount > 0f) {
        acidPattern.getSizeModulation(acidAngle, acidModulation.sizeAmount)
    } else 1f
    
    // Calculate shape size
    val baseSize = blockWidth.coerceAtMost(blockHeight)
    val baseShapeSize = baseSize * (0.5f + brightMap * 0.35f)
    val shapeSize = baseShapeSize * acidSizeMod
    val halfSize = shapeSize / 2
    
    // Determine shape type - use larger regions for coherence
    val effectiveShape = if (acidModulation.multiShape) {
        val regionX = pixel.gridX / 4
        val regionY = pixel.gridY / 4
        if (acidModulation.enabled) {
            ((regionX + regionY * 2 + (acidAngle / 5).toInt()) % 6).coerceIn(0, 5)
        } else {
            ((regionX + regionY + (hue / 120).toInt()) % 6).coerceIn(0, 5)
        }
    } else 0
    
    // Rotate around the shape's own center position using pivot
    rotate(degrees = rotation, pivot = Offset(x, y)) {
        when (effectiveShape) {
            0 -> {
                // Rectangle - fastest
                drawRect(
                    color = color,
                    topLeft = Offset(x - halfSize, y - halfSize),
                    size = Size(shapeSize, shapeSize)
                )
            }
            1 -> {
                // Ellipse/Circle
                drawOval(
                    color = color,
                    topLeft = Offset(x - halfSize, y - halfSize),
                    size = Size(shapeSize, shapeSize)
                )
            }
            2 -> {
                // Triangle - reuse path
                trianglePath.reset()
                trianglePath.moveTo(x, y - halfSize)
                trianglePath.lineTo(x - halfSize, y + halfSize)
                trianglePath.lineTo(x + halfSize, y + halfSize)
                trianglePath.close()
                drawPath(trianglePath, color)
            }
            3 -> {
                // Diamond - reuse path
                diamondPath.reset()
                diamondPath.moveTo(x, y - halfSize)
                diamondPath.lineTo(x + halfSize, y)
                diamondPath.lineTo(x, y + halfSize)
                diamondPath.lineTo(x - halfSize, y)
                diamondPath.close()
                drawPath(diamondPath, color)
            }
            4 -> {
                // Hexagon - reuse path and pre-calculated trig
                hexPath.reset()
                val r = halfSize
                hexPath.moveTo(x + r * hexCos[0], y + r * hexSin[0])
                for (i in 1..5) {
                    hexPath.lineTo(x + r * hexCos[i], y + r * hexSin[i])
                }
                hexPath.close()
                drawPath(hexPath, color)
            }
            else -> {
                // Star - reuse path and pre-calculated trig
                starPath.reset()
                val outer = halfSize
                val inner = halfSize / 2
                starPath.moveTo(x + outer * starCos[0], y + outer * starSin[0])
                for (i in 1..7) {
                    val rad = if (i % 2 == 0) outer else inner
                    starPath.lineTo(x + rad * starCos[i], y + rad * starSin[i])
                }
                starPath.close()
                drawPath(starPath, color)
            }
        }
    }
}

private fun DrawScope.drawSelectedHighlight(
    pixel: PixelData,
    blockWidth: Float,
    blockHeight: Float
) {
    val x = pixel.gridX * blockWidth + blockWidth / 2
    val y = pixel.gridY * blockHeight + blockHeight / 2
    
    val brightMap = pixel.brightness * 3f
    val highlightSize = blockWidth.coerceAtMost(blockHeight) * brightMap
    
    // Inverted color for contrast
    val highlightColor = Color(
        1f - pixel.red,
        1f - pixel.green,
        1f - pixel.blue,
        0.86f
    )
    
    drawRect(
        color = highlightColor,
        topLeft = Offset(x - highlightSize / 2, y - highlightSize / 2),
        size = Size(highlightSize, highlightSize)
    )
}
