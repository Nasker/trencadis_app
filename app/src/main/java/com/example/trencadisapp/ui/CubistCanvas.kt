package com.example.trencadisapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
    onTouch: (Float, Float, Boolean) -> Unit = { _, _, _ -> }
) {
    // Create and remember the acid pattern generator
    val acidPattern = remember { AcidPattern() }
    
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
                    onPress = { offset ->
                        onTouch(offset.x, offset.y, true)
                        tryAwaitRelease()
                        onTouch(offset.x, offset.y, false)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onTouch(offset.x, offset.y, true)
                    },
                    onDrag = { change, _ ->
                        onTouch(change.position.x, change.position.y, true)
                    },
                    onDragEnd = {
                        onTouch(0f, 0f, false)
                    },
                    onDragCancel = {
                        onTouch(0f, 0f, false)
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
                drawCubistShape(
                    pixel = pixel,
                    blockWidth = blockWidth,
                    blockHeight = blockHeight,
                    selectionMode = selectionMode,
                    cutoffValue = cutoffValue,
                    acidPattern = acidPattern,
                    acidModulation = acidModulation
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

private fun DrawScope.drawCubistShape(
    pixel: PixelData,
    blockWidth: Float,
    blockHeight: Float,
    selectionMode: PixelSelectionMode,
    cutoffValue: Float,
    acidPattern: AcidPattern,
    acidModulation: AcidModulation
) {
    val x = pixel.gridX * blockWidth + blockWidth / 2
    val y = pixel.gridY * blockHeight + blockHeight / 2
    
    val brightness = pixel.brightness
    var hue = pixel.hue
    
    // Map brightness to size multiplier (0-10 like original)
    val brightMap = brightness * 10f
    
    // Get acid pattern angle for this pixel
    val acidAngle = if (acidModulation.enabled) {
        acidPattern.getAnimatedAngle(pixel.gridX, pixel.gridY, acidModulation.animationSpeed)
    } else 0f
    
    // Apply acid hue modulation - blend between original hue and acid-generated hue
    if (acidModulation.enabled && acidModulation.hueAmount > 0f) {
        val acidHue = acidPattern.getHueModulation(acidAngle)
        hue = hue * (1f - acidModulation.hueAmount) + acidHue * acidModulation.hueAmount
    }
    
    // More noticeable rotation based on hue + acid modulation
    val rotationFactor = when (selectionMode) {
        PixelSelectionMode.BRIGHTEST, PixelSelectionMode.CENTER -> 1.0f
        else -> 0.6f
    }
    var rotation = (hue / 360f) * 90f * rotationFactor
    
    // Add acid rotation modulation
    if (acidModulation.enabled && acidModulation.rotationAmount > 0f) {
        rotation += acidPattern.getRotationModulation(acidAngle, 90f * acidModulation.rotationAmount)
    }
    
    // Boost saturation for psychedelic/cubist look
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (pixel.red * 255).toInt(),
        (pixel.green * 255).toInt(),
        (pixel.blue * 255).toInt(),
        hsv
    )
    
    // Apply acid hue shift to HSV
    if (acidModulation.enabled && acidModulation.hueAmount > 0f) {
        hsv[0] = hue  // Use the modulated hue
    }
    
    // Boost saturation (1.6x) and slightly increase value for vibrance
    hsv[1] = (hsv[1] * 1.6f).coerceAtMost(1f)
    hsv[2] = (hsv[2] * 1.1f).coerceAtMost(1f)
    val boostedArgb = android.graphics.Color.HSVToColor(hsv)
    
    // Color with alpha - brighter pixels are more opaque for 3D depth effect
    var alpha = 0.5f + brightness * 0.45f
    
    // Apply acid alpha modulation
    if (acidModulation.enabled && acidModulation.alphaAmount > 0f) {
        val acidAlpha = acidPattern.getAlphaModulation(acidAngle, 0.3f, 1f)
        alpha = alpha * (1f - acidModulation.alphaAmount) + acidAlpha * acidModulation.alphaAmount
    }
    
    val color = Color(
        android.graphics.Color.red(boostedArgb) / 255f,
        android.graphics.Color.green(boostedArgb) / 255f,
        android.graphics.Color.blue(boostedArgb) / 255f,
        alpha
    )
    
    // Calculate acid size modulation
    val acidSizeMod = if (acidModulation.enabled && acidModulation.sizeAmount > 0f) {
        acidPattern.getSizeModulation(acidAngle, acidModulation.sizeAmount)
    } else 1f
    
    // Calculate shape size
    val baseSize = blockWidth.coerceAtMost(blockHeight)
    val baseShapeSize = baseSize * (0.5f + brightMap * 0.35f)
    val shapeSize = baseShapeSize * acidSizeMod
    
    // Determine shape type - use larger regions for coherence (divide by 4 to group shapes)
    // This creates "zones" of similar shapes rather than per-pixel noise
    val regionX = pixel.gridX / 4
    val regionY = pixel.gridY / 4
    val shapeSelector = if (acidModulation.enabled) {
        // Slow morphing based on acid angle (divide by larger number for slower change)
        ((regionX + regionY * 2 + (acidAngle / 5).toInt()) % 6).coerceIn(0, 5)
    } else {
        // Static regions based on position and hue band
        ((regionX + regionY + (hue / 120).toInt()) % 6).coerceIn(0, 5)
    }
    
    // Create gradient brush for psychedelic effect
    val gradientBrush = if (acidModulation.enabled && acidModulation.hueAmount > 0.3f) {
        // Complementary color for gradient
        val complementHue = (hue + 180f) % 360f
        val complementHsv = floatArrayOf(complementHue, hsv[1], hsv[2] * 0.8f)
        val complementArgb = android.graphics.Color.HSVToColor(complementHsv)
        val complementColor = Color(
            android.graphics.Color.red(complementArgb) / 255f,
            android.graphics.Color.green(complementArgb) / 255f,
            android.graphics.Color.blue(complementArgb) / 255f,
            alpha * 0.7f
        )
        
        // Radial gradient from center
        Brush.radialGradient(
            colors = listOf(color, complementColor),
            center = Offset(x, y),
            radius = shapeSize
        )
    } else null
    
    // Rotate around the shape's own center position using pivot
    rotate(degrees = rotation, pivot = Offset(x, y)) {
        // If multiShape is off, always use rectangle (shapeSelector 0)
        val effectiveShape = if (acidModulation.multiShape) shapeSelector else 0
        when (effectiveShape) {
            0 -> {
                // Rectangle
                if (gradientBrush != null) {
                    drawRect(
                        brush = gradientBrush,
                        topLeft = Offset(x - shapeSize / 2, y - shapeSize / 2),
                        size = Size(shapeSize, shapeSize)
                    )
                } else {
                    drawRect(
                        color = color,
                        topLeft = Offset(x - shapeSize / 2, y - shapeSize / 2),
                        size = Size(shapeSize, shapeSize)
                    )
                }
            }
            1 -> {
                // Ellipse/Circle
                if (gradientBrush != null) {
                    drawOval(
                        brush = gradientBrush,
                        topLeft = Offset(x - shapeSize / 2, y - shapeSize / 2),
                        size = Size(shapeSize, shapeSize)
                    )
                } else {
                    drawOval(
                        color = color,
                        topLeft = Offset(x - shapeSize / 2, y - shapeSize / 2),
                        size = Size(shapeSize, shapeSize)
                    )
                }
            }
            2 -> {
                // Triangle pointing up
                val trianglePath = Path().apply {
                    moveTo(x, y - shapeSize / 2)  // Top
                    lineTo(x - shapeSize / 2, y + shapeSize / 2)  // Bottom left
                    lineTo(x + shapeSize / 2, y + shapeSize / 2)  // Bottom right
                    close()
                }
                if (gradientBrush != null) {
                    drawPath(trianglePath, gradientBrush)
                } else {
                    drawPath(trianglePath, color)
                }
            }
            3 -> {
                // Diamond
                val diamondPath = Path().apply {
                    moveTo(x, y - shapeSize / 2)  // Top
                    lineTo(x + shapeSize / 2, y)  // Right
                    lineTo(x, y + shapeSize / 2)  // Bottom
                    lineTo(x - shapeSize / 2, y)  // Left
                    close()
                }
                if (gradientBrush != null) {
                    drawPath(diamondPath, gradientBrush)
                } else {
                    drawPath(diamondPath, color)
                }
            }
            4 -> {
                // Hexagon
                val hexPath = Path().apply {
                    val r = shapeSize / 2
                    for (i in 0..5) {
                        val angle = (PI / 3 * i - PI / 2).toFloat()
                        val px = x + r * cos(angle)
                        val py = y + r * sin(angle)
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                if (gradientBrush != null) {
                    drawPath(hexPath, gradientBrush)
                } else {
                    drawPath(hexPath, color)
                }
            }
            else -> {
                // Star/Cross shape
                val starPath = Path().apply {
                    val outer = shapeSize / 2
                    val inner = shapeSize / 4
                    for (i in 0..7) {
                        val r = if (i % 2 == 0) outer else inner
                        val angle = (PI / 4 * i - PI / 2).toFloat()
                        val px = x + r * cos(angle)
                        val py = y + r * sin(angle)
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                if (gradientBrush != null) {
                    drawPath(starPath, gradientBrush)
                } else {
                    drawPath(starPath, color)
                }
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
