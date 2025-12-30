package com.example.trencadisapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import com.example.trencadisapp.camera.PixelData
import com.example.trencadisapp.camera.PixelGrid
import com.example.trencadisapp.camera.PixelSelectionMode
import kotlin.math.PI

@Composable
fun CubistCanvas(
    pixelGrid: PixelGrid?,
    selectedPixel: PixelData?,
    selectionMode: PixelSelectionMode,
    cutoffValue: Float,
    modifier: Modifier = Modifier,
    onTouch: (Float, Float, Boolean) -> Unit = { _, _, _ -> }
) {
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
        
        // Draw pixel grid
        pixelGrid?.let { grid ->
            val blockWidth = canvasWidth / grid.cols
            val blockHeight = canvasHeight / grid.rows
            
            for (pixel in grid.pixels) {
                drawCubistShape(
                    pixel = pixel,
                    blockWidth = blockWidth,
                    blockHeight = blockHeight,
                    selectionMode = selectionMode,
                    cutoffValue = cutoffValue
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
    cutoffValue: Float
) {
    val x = pixel.gridX * blockWidth + blockWidth / 2
    val y = pixel.gridY * blockHeight + blockHeight / 2
    
    val brightness = pixel.brightness
    val hue = pixel.hue
    
    // Map brightness to size multiplier (0-10 like original)
    val brightMap = brightness * 10f
    
    // More noticeable rotation based on hue
    val rotationFactor = when (selectionMode) {
        PixelSelectionMode.BRIGHTEST, PixelSelectionMode.CENTER -> 1.0f  // Full rotation range
        else -> 0.6f  // Moderate in other modes
    }
    val rotation = (hue / 360f) * 90f * rotationFactor  // Max 90 or 54 degrees
    
    // Color with alpha
    val color = Color(pixel.red, pixel.green, pixel.blue, 0.78f)
    
    // Rotate around the shape's own center position using pivot
    rotate(degrees = rotation, pivot = Offset(x, y)) {
        when (selectionMode) {
            PixelSelectionMode.BRIGHTEST, PixelSelectionMode.CENTER -> {
                // Alternate between rect and ellipse based on position and cutoff
                val variation = (pixel.gridX * pixel.gridY) % ((cutoffValue * 10).toInt().coerceAtLeast(1) + 1)
                val shapeSize = blockWidth.coerceAtMost(blockHeight) * (1.15f + brightMap * 0.03f)  // Bigger shapes
                
                if (variation == 0) {
                    drawRect(
                        color = color,
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
            else -> {
                // Simple rect - slightly oversized for better visual
                val baseSize = blockWidth.coerceAtMost(blockHeight)
                val shapeSize = baseSize * 1.15f + 0.1f * brightness * baseSize  // Bigger shapes
                
                drawRect(
                    color = color,
                    topLeft = Offset(x - shapeSize / 2, y - shapeSize / 2),
                    size = Size(shapeSize, shapeSize)
                )
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
