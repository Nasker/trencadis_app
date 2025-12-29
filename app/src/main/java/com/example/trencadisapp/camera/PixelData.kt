package com.example.trencadisapp.camera

import android.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor

data class PixelData(
    val gridX: Int,
    val gridY: Int,
    val red: Float,
    val green: Float,
    val blue: Float,
    val brightness: Float,
    val hue: Float,
    val saturation: Float
) {
    val color: ComposeColor
        get() = ComposeColor(red, green, blue, 1f)
    
    val invertedColor: ComposeColor
        get() = ComposeColor(1f - red, 1f - green, 1f - blue, 1f)
    
    companion object {
        fun fromArgb(gridX: Int, gridY: Int, argb: Int): PixelData {
            val r = Color.red(argb) / 255f
            val g = Color.green(argb) / 255f
            val b = Color.blue(argb) / 255f
            
            val hsv = FloatArray(3)
            Color.RGBToHSV((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), hsv)
            
            return PixelData(
                gridX = gridX,
                gridY = gridY,
                red = r,
                green = g,
                blue = b,
                brightness = hsv[2],  // Value component
                hue = hsv[0],         // 0-360
                saturation = hsv[1]   // 0-1
            )
        }
    }
}

data class PixelGrid(
    val cols: Int,
    val rows: Int,
    val blockSize: Int,
    val pixels: List<PixelData>
) {
    fun getPixelAt(col: Int, row: Int): PixelData? {
        val index = col * rows + row
        return if (index in pixels.indices) pixels[index] else null
    }
    
    fun findBrightest(): PixelData? {
        return pixels.maxByOrNull { it.brightness }
    }
    
    fun getCenter(): PixelData? {
        return getPixelAt(cols / 2, rows / 2)
    }
    
    fun getAtPosition(x: Float, y: Float, width: Float, height: Float): PixelData? {
        val col = ((x / width) * cols).toInt().coerceIn(0, cols - 1)
        val row = ((y / height) * rows).toInt().coerceIn(0, rows - 1)
        return getPixelAt(col, row)
    }
    
    fun getSequential(index: Int): PixelData? {
        val wrappedIndex = index % pixels.size
        return if (wrappedIndex in pixels.indices) pixels[wrappedIndex] else null
    }
}

enum class PixelSelectionMode {
    SEQUENCE,   // Step through pixels sequentially
    BRIGHTEST,  // Find brightest pixel
    CENTER,     // Always use center pixel
    POINTER     // Use touch position
}
