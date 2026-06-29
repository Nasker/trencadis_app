package com.example.trencadisapp.ui

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Acid pattern generator inspired by Processing sketch.
 * Generates trippy modulation values for shape rendering based on grid position and time.
 */
class AcidPattern {
    
    private var globalAngle = 0f
    private var patternType = PatternType.WAVE_INTERFERENCE
    
    enum class PatternType {
        GRID_MULTIPLY,      // i*j - diagonal waves
        TAN_ROWS,           // tan(j) - horizontal distortion
        TAN_COLS,           // tan(i) - vertical distortion  
        WAVE_DIAGONAL,      // sin(i+j)+j - diagonal sine waves
        TAN_DIAGONAL_SHIFT, // tan((i+j)+2) - shifted tangent
        TAN_MULTIPLY,       // tan(i*j) - complex interference
        SUBTRACT_IJ,        // i-j - linear gradient
        SUBTRACT_JI,        // j-i - reverse gradient
        ADD_IJ,             // i+j - diagonal gradient
        WAVE_INTERFERENCE   // sin(tan(i+j)+tan(i-j)-1) - complex interference
    }
    
    /**
     * Get the base angle for a grid position based on current pattern type
     */
    fun getBaseAngle(gridX: Int, gridY: Int): Float {
        val i = gridX.toFloat()
        val j = gridY.toFloat()
        
        return when (patternType) {
            PatternType.GRID_MULTIPLY -> i * j
            PatternType.TAN_ROWS -> tan(j)
            PatternType.TAN_COLS -> tan(i)
            PatternType.WAVE_DIAGONAL -> sin(i + j) + j
            PatternType.TAN_DIAGONAL_SHIFT -> tan((i + j) + 2)
            PatternType.TAN_MULTIPLY -> tan(i * j)
            PatternType.SUBTRACT_IJ -> i - j
            PatternType.SUBTRACT_JI -> j - i
            PatternType.ADD_IJ -> i + j
            PatternType.WAVE_INTERFERENCE -> sin(tan(i + j) + tan(i - j) - 1)
        }
    }
    
    /**
     * Get the animated angle including time-based rotation
     */
    fun getAnimatedAngle(gridX: Int, gridY: Int, rotationSpeed: Float = 0.02f): Float {
        return getBaseAngle(gridX, gridY) + globalAngle * rotationSpeed
    }
    
    /**
     * Get color hue modulation (0-360) based on angle - creates the acid color cycling
     */
    fun getHueModulation(angle: Float): Float {
        // 127 + 127 * sin(angle) maps to 0-254, scale to 0-360
        return ((127f + 127f * sin(angle)) / 254f) * 360f
    }
    
    /**
     * Get size modulation factor based on angle
     */
    fun getSizeModulation(angle: Float, amount: Float = 0.5f): Float {
        // Returns 1.0 +/- amount based on sin of angle
        return 1f + sin(angle) * amount
    }
    
    /**
     * Get rotation modulation in degrees
     */
    fun getRotationModulation(angle: Float, maxRotation: Float = 45f): Float {
        return sin(angle) * maxRotation
    }
    
    /**
     * Get alpha modulation
     */
    fun getAlphaModulation(angle: Float, minAlpha: Float = 0.4f, maxAlpha: Float = 1f): Float {
        val range = maxAlpha - minAlpha
        return minAlpha + ((sin(angle) + 1f) / 2f) * range
    }
    
    /**
     * Advance the global animation
     */
    fun tick(increment: Float = 1f) {
        globalAngle += increment
    }
    
    /**
     * Set the pattern type
     */
    fun setPattern(type: PatternType) {
        patternType = type
    }
    
    fun getPattern(): PatternType = patternType
    
    /**
     * Reset animation
     */
    fun reset() {
        globalAngle = 0f
    }
    
    companion object {
        val PATTERN_NAMES = listOf(
            "GRID" to PatternType.GRID_MULTIPLY,
            "TAN-H" to PatternType.TAN_ROWS,
            "TAN-V" to PatternType.TAN_COLS,
            "WAVE" to PatternType.WAVE_DIAGONAL,
            "TAN-D" to PatternType.TAN_DIAGONAL_SHIFT,
            "TAN-X" to PatternType.TAN_MULTIPLY,
            "GRAD1" to PatternType.SUBTRACT_IJ,
            "GRAD2" to PatternType.SUBTRACT_JI,
            "GRAD3" to PatternType.ADD_IJ,
            "ACID" to PatternType.WAVE_INTERFERENCE
        )
    }
}

/**
 * Configuration for how much the acid pattern affects different shape properties
 */
data class AcidModulation(
    val enabled: Boolean = false,
    val multiShape: Boolean = false,   // true: use varied shapes, false: rectangles only
    val hueAmount: Float = 0.5f,      // 0-1: how much pattern affects hue
    val sizeAmount: Float = 0.3f,      // 0-1: how much pattern affects size
    val rotationAmount: Float = 0.5f,  // 0-1: how much pattern affects rotation
    val alphaAmount: Float = 0.2f,     // 0-1: how much pattern affects alpha
    val animationSpeed: Float = 0.5f,  // Animation speed multiplier
    val brightnessSizeBoost: Float = 0.08f  // 0-0.5+: how much brightness grows shape size
)
