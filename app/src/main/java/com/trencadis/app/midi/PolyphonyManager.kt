package com.trencadis.app.midi

import androidx.compose.ui.geometry.Offset
import com.trencadis.app.camera.PixelData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Polyphony manager for Trencadis 2.0
 * Handles multi-cursor management and voice distribution
 */
class PolyphonyManager {
    
    private val _polyphonyState = MutableStateFlow(PolyphonyState())
    val polyphonyState = _polyphonyState.asStateFlow()
    
    init {
        // Initialize default voices
        initializeVoices()
    }
    
    /**
     * Enable or disable polyphony mode
     */
    fun enablePolyphony(enabled: Boolean) {
        _polyphonyState.value = _polyphonyState.value.copy(enabled = enabled)
        
        if (enabled) {
            initializeVoices()
        }
    }
    
    /**
     * Set the number of active voices
     */
    fun setVoiceCount(count: Int) {
        val validCount = count.coerceIn(1, 8)
        val newVoices = (0 until validCount).map { index ->
            if (index < _polyphonyState.value.voices.size) {
                _polyphonyState.value.voices[index]
            } else {
                createVoice(index)
            }
        }
        
        _polyphonyState.value = _polyphonyState.value.copy(
            voiceCount = validCount,
            voices = newVoices
        )
    }
    
    /**
     * Update cursor positions based on center pixel and distribution mode
     */
    fun updateCursorPositions(centerPixel: PixelData) {
        val state = _polyphonyState.value
        if (!state.enabled) return
        
        val updatedVoices = state.voices.mapIndexed { index, voice ->
            val offset = calculateCursorOffset(index, state.voiceCount, state.voiceDistribution, state.cursorSpacing)
            voice.copy(
                lastTriggeredPixel = findPixelAtOffset(centerPixel, offset),
                active = true
            )
        }
        
        _polyphonyState.value = state.copy(voices = updatedVoices)
    }
    
    /**
     * Trigger a specific voice with a pixel
     */
    fun triggerVoice(voiceId: Int, pixel: PixelData, note: Int, velocity: Int = 127) {
        val state = _polyphonyState.value
        val updatedVoices = state.voices.map { voice ->
            if (voice.id == voiceId) {
                voice.copy(
                    lastTriggeredPixel = pixel,
                    lastNotePlayed = note,
                    velocity = velocity,
                    active = true
                )
            } else {
                voice
            }
        }
        
        _polyphonyState.value = state.copy(voices = updatedVoices)
    }
    
    /**
     * Set cursor spacing between voices
     */
    fun setCursorSpacing(spacing: Float) {
        _polyphonyState.value = _polyphonyState.value.copy(
            cursorSpacing = spacing.coerceIn(0.5f, 5.0f)
        )
    }
    
    /**
     * Set voice distribution pattern
     */
    fun setVoiceDistribution(distribution: VoiceDistribution) {
        _polyphonyState.value = _polyphonyState.value.copy(
            voiceDistribution = distribution
        )
    }
    
    /**
     * Set channel mode for polyphony
     */
    fun setChannelMode(mode: ChannelMode) {
        _polyphonyState.value = _polyphonyState.value.copy(
            channelMode = mode
        )
        
        // Update voice channel assignments based on mode
        updateVoiceChannels(mode)
    }
    
    /**
     * Get current active voices
     */
    fun getActiveVoices(): List<CursorVoice> {
        return _polyphonyState.value.voices.filter { it.active }
    }
    
    /**
     * Get voice by ID
     */
    fun getVoice(voiceId: Int): CursorVoice? {
        return _polyphonyState.value.voices.find { it.id == voiceId }
    }
    
    /**
     * Initialize default voices
     */
    private fun initializeVoices() {
        val defaultVoices = (0 until 4).map { index ->
            createVoice(index)
        }
        
        _polyphonyState.value = _polyphonyState.value.copy(
            voices = defaultVoices,
            voiceCount = 4
        )
    }
    
    /**
     * Create a new voice with default settings
     */
    private fun createVoice(index: Int): CursorVoice {
        val colors = VoiceColor.values()
        return CursorVoice(
            id = index,
            channel = index,
            offset = Offset.Zero,
            color = colors[index % colors.size],
            active = false,
            lastNotePlayed = 60 + index, // Different starting notes
            velocity = 127
        )
    }
    
    /**
     * Calculate cursor offset based on distribution mode
     */
    private fun calculateCursorOffset(
        voiceIndex: Int, 
        totalVoices: Int, 
        distribution: VoiceDistribution, 
        spacing: Float
    ): Offset {
        return when (distribution) {
            VoiceDistribution.RADIAL -> {
                val angle = (2 * PI * voiceIndex / totalVoices).toFloat()
                Offset(
                    x = spacing * cos(angle),
                    y = spacing * sin(angle)
                )
            }
            
            VoiceDistribution.LINEAR -> {
                val xOffset = spacing * (voiceIndex - (totalVoices - 1) / 2f)
                Offset(x = xOffset, y = 0f)
            }
            
            VoiceDistribution.RANDOM -> {
                // Use voice index as seed for consistent random positions
                val random = kotlin.random.Random(voiceIndex.toLong())
                Offset(
                    x = spacing * (random.nextFloat() - 0.5f) * 2,
                    y = spacing * (random.nextFloat() - 0.5f) * 2
                )
            }
            
            VoiceDistribution.MANUAL -> {
                // Return current offset for manual positioning
                val currentVoice = _polyphonyState.value.voices.getOrNull(voiceIndex)
                currentVoice?.offset ?: Offset.Zero
            }
        }
    }
    
    /**
     * Find pixel at offset from center pixel
     */
    private fun findPixelAtOffset(centerPixel: PixelData, offset: Offset): PixelData {
        // This would need access to the pixel grid to find the actual pixel
        // For now, return the center pixel as a placeholder
        // In the actual implementation, this would:
        // 1. Calculate target grid position
        // 2. Look up the pixel at that position
        // 3. Return the found pixel or nearest valid pixel
        
        return centerPixel.copy(
            gridX = (centerPixel.gridX + offset.x).toInt(),
            gridY = (centerPixel.gridY + offset.y).toInt()
        )
    }
    
    /**
     * Update voice channel assignments based on channel mode
     */
    private fun updateVoiceChannels(mode: ChannelMode) {
        val state = _polyphonyState.value
        val updatedVoices = when (mode) {
            ChannelMode.MONOPHONIC -> {
                state.voices.map { voice ->
                    voice.copy(channel = 0) // All on channel 0
                }
            }
            
            ChannelMode.MULTITIMBRAL -> {
                state.voices.mapIndexed { index, voice ->
                    voice.copy(channel = index) // Each on different channel
                }
            }
            
            ChannelMode.CHORD_MODE -> {
                state.voices.map { voice ->
                    voice.copy(channel = 0) // All on same channel for chord
                }
            }
        }
        
        _polyphonyState.value = state.copy(voices = updatedVoices)
    }
    
    /**
     * Set manual position for a voice
     */
    fun setVoicePosition(voiceId: Int, offset: Offset) {
        val state = _polyphonyState.value
        val updatedVoices = state.voices.map { voice ->
            if (voice.id == voiceId) {
                voice.copy(offset = offset)
            } else {
                voice
            }
        }
        
        _polyphonyState.value = state.copy(voices = updatedVoices)
    }
    
    /**
     * Get polyphony parameters for preset saving
     */
    fun getParameters(): Map<String, Any> {
        val state = _polyphonyState.value
        return mapOf(
            "polyphony_enabled" to state.enabled,
            "voice_count" to state.voiceCount,
            "channel_mode" to state.channelMode.name,
            "cursor_spacing" to state.cursorSpacing,
            "voice_distribution" to state.voiceDistribution.name,
            "voice_positions" to state.voices.map { 
                mapOf(
                    "id" to it.id,
                    "channel" to it.channel,
                    "offset_x" to it.offset.x,
                    "offset_y" to it.offset.y,
                    "color" to it.color.name
                )
            }
        )
    }
    
    /**
     * Load polyphony parameters from map
     */
    fun loadParameters(parameters: Map<String, Any>) {
        parameters["polyphony_enabled"]?.let { 
            enablePolyphony(it as Boolean) 
        }
        parameters["voice_count"]?.let { 
            setVoiceCount(it as Int) 
        }
        parameters["cursor_spacing"]?.let { 
            setCursorSpacing(it as Float) 
        }
        
        (parameters["channel_mode"] as? String)?.let { modeName ->
            try {
                val mode = ChannelMode.valueOf(modeName)
                setChannelMode(mode)
            } catch (e: IllegalArgumentException) {
                // Invalid mode, ignore
            }
        }
        
        (parameters["voice_distribution"] as? String)?.let { distName ->
            try {
                val distribution = VoiceDistribution.valueOf(distName)
                setVoiceDistribution(distribution)
            } catch (e: IllegalArgumentException) {
                // Invalid distribution, ignore
            }
        }
        
        // Load voice positions
        (parameters["voice_positions"] as? List<Map<String, Any>>)?.let { positions ->
            val updatedVoices = _polyphonyState.value.voices.map { voice ->
                val positionData = positions.find { it["id"] == voice.id }
                positionData?.let { pos ->
                    voice.copy(
                        channel = pos["channel"] as? Int ?: voice.channel,
                        offset = Offset(
                            x = (pos["offset_x"] as? Float) ?: voice.offset.x,
                            y = (pos["offset_y"] as? Float) ?: voice.offset.y
                        )
                    )
                } ?: voice
            }
            
            _polyphonyState.value = _polyphonyState.value.copy(voices = updatedVoices)
        }
    }
}
