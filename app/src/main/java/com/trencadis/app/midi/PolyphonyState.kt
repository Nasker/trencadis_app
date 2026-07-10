package com.trencadis.app.midi

import androidx.compose.ui.geometry.Offset
import com.trencadis.app.camera.PixelData

/**
 * Polyphony and multitimbrality state for Trencadis 2.0
 */
data class PolyphonyState(
    val enabled: Boolean = false,
    val voiceCount: Int = 4,
    val voices: List<CursorVoice> = emptyList(),
    val channelMode: ChannelMode = ChannelMode.MONOPHONIC,
    val cursorSpacing: Float = 1.5f, // Grid units between cursors
    val voiceDistribution: VoiceDistribution = VoiceDistribution.RADIAL
)

data class CursorVoice(
    val id: Int,
    val channel: Int = 0,
    val offset: Offset = Offset.Zero,
    val color: VoiceColor = VoiceColor.BLUE,
    val active: Boolean = false,
    val lastTriggeredPixel: PixelData? = null,
    val lastNotePlayed: Int = 60, // MIDI note number
    val velocity: Int = 127
)

enum class ChannelMode {
    MONOPHONIC,        // All voices on same channel
    MULTITIMBRAL,      // Each voice on different channel
    CHORD_MODE         // Voices form chord on single channel
}

enum class VoiceDistribution {
    RADIAL,            // Voices arranged in circle around center
    LINEAR,            // Voices arranged in line
    RANDOM,            // Random distribution
    MANUAL             // User-defined positions
}

enum class VoiceColor {
    BLUE, GREEN, RED, YELLOW, PURPLE, ORANGE, CYAN, MAGENTA
}

data class MidiChannelState(
    val enabled: Boolean = false,
    val channelMappings: Map<Int, Int> = emptyMap(), // Voice ID -> MIDI Channel
    val externalSynthProfiles: List<ExternalSynthProfile> = emptyList(),
    val learnMode: Boolean = false, // MIDI learn for parameter mapping
    val activeChannels: Set<Int> = emptySet()
)

data class ExternalSynthProfile(
    val name: String,
    val midiChannel: Int,
    val preferredVoiceCount: Int,
    val ccMappings: Map<Int, Int> = emptyMap(), // CC number -> Parameter ID
    val isActive: Boolean = false
)
