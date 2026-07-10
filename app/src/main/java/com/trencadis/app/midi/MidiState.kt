package com.trencadis.app.midi

data class MidiState(
    val enabled: Boolean = false,
    val outputMode: MidiOutputMode = MidiOutputMode.INTERNAL,
    val channel: Int = 1,
    val deviceName: String = "",
    val isClockLocked: Boolean = false,
    val externalBpm: Float = 0f,
    val bleEnabled: Boolean = false,
    val bleConnected: Boolean = false
)

enum class MidiOutputMode {
    INTERNAL,
    MIDI_OUT,
    BOTH
}
