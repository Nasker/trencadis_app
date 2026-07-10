package com.trencadis.app.ui

import androidx.compose.ui.geometry.Offset
import com.trencadis.app.camera.PixelData
import com.trencadis.app.midi.CursorVoice
import com.trencadis.app.midi.PolyphonyManager
import com.trencadis.app.midi.VoiceColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * UI manager for polyphony cursors in Trencadis 2.0
 * Handles visual representation and interaction of multiple cursors
 */
class PolyphonyCursorManager(private val polyphonyManager: PolyphonyManager) {
    
    private val _cursorState = MutableStateFlow(PolyphonyCursorState())
    val cursorState = _cursorState.asStateFlow()
    
    // Visual cursor positions on screen
    private var screenCursors: Map<Int, ScreenCursor> = emptyMap()
    
    init {
        // Observe polyphony state changes
        observePolyphonyState()
    }
    
    /**
     * Update cursor positions based on center pixel
     */
    fun updateCursorPositions(centerPixel: PixelData, canvasWidth: Float, canvasHeight: Float) {
        if (!polyphonyManager.polyphonyState.value.enabled) return
        
        val polyphonyState = polyphonyManager.polyphonyState.value
        val updatedScreenCursors = mutableMapOf<Int, ScreenCursor>()
        
        polyphonyState.voices.forEach { voice ->
            if (voice.active) {
                // Calculate screen position for this voice
                val screenOffset = calculateScreenOffset(
                    voice.offset,
                    canvasWidth,
                    canvasHeight,
                    polyphonyState.blockSize
                )
                
                val screenCursor = ScreenCursor(
                    voiceId = voice.id,
                    screenPosition = Offset(
                        x = (centerPixel.gridX * polyphonyState.blockSize) + screenOffset.x,
                        y = (centerPixel.gridY * polyphonyState.blockSize) + screenOffset.y
                    ),
                    color = getVoiceColor(voice.color),
                    isActive = voice.active,
                    lastNotePlayed = voice.lastNotePlayed,
                    velocity = voice.velocity
                )
                
                updatedScreenCursors[voice.id] = screenCursor
            }
        }
        
        screenCursors = updatedScreenCursors
        _cursorState.value = _cursorState.value.copy(
            cursors = updatedScreenCursors.values.toList(),
            centerPixel = centerPixel
        )
    }
    
    /**
     * Handle touch interaction for manual cursor positioning
     */
    fun handleTouch(touchX: Float, touchY: Float, canvasWidth: Float, canvasHeight: Float) {
        if (!polyphonyManager.polyphonyState.value.enabled) return
        
        val polyphonyState = polyphonyManager.polyphonyState.value
        
        if (polyphonyState.voiceDistribution == com.trencadis.app.midi.VoiceDistribution.MANUAL) {
            // Find closest cursor to touch and move it
            val touchOffset = Offset(touchX, touchY)
            val closestCursor = screenCursors.values.minByOrNull { cursor ->
                kotlin.math.sqrt(
                    (cursor.screenPosition.x - touchX).pow(2) + 
                    (cursor.screenPosition.y - touchY).pow(2)
                )
            }
            
            closestCursor?.let { cursor ->
                // Convert screen position back to grid offset
                val gridX = (touchX / polyphonyState.blockSize).toInt()
                val gridY = (touchY / polyphonyState.blockSize).toInt()
                val centerPixel = _cursorState.value.centerPixel
                
                centerPixel?.let { center ->
                    val offset = Offset(
                        x = (gridX - center.gridX).toFloat(),
                        y = (gridY - center.gridY).toFloat()
                    )
                    
                    polyphonyManager.setVoicePosition(cursor.voiceId, offset)
                }
            }
        }
    }
    
    /**
     * Get screen cursor for specific voice
     */
    fun getScreenCursor(voiceId: Int): ScreenCursor? {
        return screenCursors[voiceId]
    }
    
    /**
     * Get all active screen cursors
     */
    fun getActiveCursors(): List<ScreenCursor> {
        return screenCursors.values.filter { it.isActive }
    }
    
    /**
     * Check if a position is near any cursor
     */
    fun isNearCursor(position: Offset, threshold: Float = 50f): Boolean {
        return screenCursors.values.any { cursor ->
            val distance = kotlin.math.sqrt(
                (cursor.screenPosition.x - position.x).pow(2) + 
                (cursor.screenPosition.y - position.y).pow(2)
            )
            distance <= threshold
        }
    }
    
    /**
     * Get cursor at specific position
     */
    fun getCursorAt(position: Offset, threshold: Float = 50f): ScreenCursor? {
        return screenCursors.values.minByOrNull { cursor ->
            val distance = kotlin.math.sqrt(
                (cursor.screenPosition.x - position.x).pow(2) + 
                (cursor.screenPosition.y - position.y).pow(2)
            )
            if (distance <= threshold) distance else Float.MAX_VALUE
        }
    }
    
    /**
     * Animate cursor movement
     */
    fun animateCursorToPosition(voiceId: Int, targetPosition: Offset) {
        // TODO: Implement cursor animation
        // This would interpolate cursor position smoothly
    }
    
    /**
     * Show/hide cursor indicators
     */
    fun setCursorsVisible(visible: Boolean) {
        _cursorState.value = _cursorState.value.copy(
            cursorsVisible = visible
        )
    }
    
    /**
     * Set cursor size
     */
    fun setCursorSize(size: Float) {
        _cursorState.value = _cursorState.value.copy(
            cursorSize = size.coerceIn(0.5f, 3f)
        )
    }
    
    /**
     * Set cursor opacity
     */
    fun setCursorOpacity(opacity: Float) {
        _cursorState.value = _cursorState.value.copy(
            cursorOpacity = opacity.coerceIn(0f, 1f)
        )
    }
    
    /**
     * Calculate screen offset for cursor
     */
    private fun calculateScreenOffset(
        gridOffset: Offset,
        canvasWidth: Float,
        canvasHeight: Float,
        blockSize: Int
    ): Offset {
        return Offset(
            x = gridOffset.x * blockSize,
            y = gridOffset.y * blockSize
        )
    }
    
    /**
     * Get color for voice
     */
    private fun getVoiceColor(voiceColor: VoiceColor): androidx.compose.ui.graphics.Color {
        return when (voiceColor) {
            VoiceColor.BLUE -> androidx.compose.ui.graphics.Color.Blue
            VoiceColor.GREEN -> androidx.compose.ui.graphics.Color.Green
            VoiceColor.RED -> androidx.compose.ui.graphics.Color.Red
            VoiceColor.YELLOW -> androidx.compose.ui.graphics.Color.Yellow
            VoiceColor.PURPLE -> androidx.compose.ui.graphics.Color.Magenta
            VoiceColor.ORANGE -> androidx.compose.ui.graphics.Color(0xFFFFA500)
            VoiceColor.CYAN -> androidx.compose.ui.graphics.Color.Cyan
            VoiceColor.MAGENTA -> androidx.compose.ui.graphics.Color.Magenta
        }
    }
    
    /**
     * Observe polyphony state changes
     */
    private fun observePolyphonyState() {
        // TODO: Implement state observation
        // This would observe the polyphony manager's state flow
        // and update the cursor state accordingly
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        screenCursors = emptyMap()
        _cursorState.value = PolyphonyCursorState()
    }
}

/**
 * Polyphony cursor state for UI
 */
data class PolyphonyCursorState(
    val cursors: List<ScreenCursor> = emptyList(),
    val centerPixel: com.trencadis.app.camera.PixelData? = null,
    val cursorsVisible: Boolean = true,
    val cursorSize: Float = 1f,
    val cursorOpacity: Float = 0.8f
)

/**
 * Screen cursor representation
 */
data class ScreenCursor(
    val voiceId: Int,
    val screenPosition: Offset,
    val color: androidx.compose.ui.graphics.Color,
    val isActive: Boolean,
    val lastNotePlayed: Int,
    val velocity: Int,
    val animationProgress: Float = 1f
)
