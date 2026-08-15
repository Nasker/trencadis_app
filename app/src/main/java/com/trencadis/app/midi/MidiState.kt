package com.trencadis.app.midi

data class MidiState(
    val enabled: Boolean = false,
    val outputMode: MidiOutputMode = MidiOutputMode.INTERNAL,
    val channel: Int = 1,
    val deviceName: String = "",
    // An external MIDI clock is currently sending ticks (USB or virtual device).
    val externalClockAvailable: Boolean = false,
    // The sequencer is actually following the external clock:
    // externalClockAvailable && syncSource == EXTERNAL.
    val isClockLocked: Boolean = false,
    val syncSource: SyncSource = SyncSource.EXTERNAL,
    val externalBpm: Float = 0f,
    val bleEnabled: Boolean = false,
    val bleConnected: Boolean = false,
    // Follow chords played on an external MIDI device: detected chords set the
    // key + chord mapping so the pixel sequencer arpeggiates over the chord.
    val chordFollowEnabled: Boolean = false,
    // Human-readable label of the last detected chord, e.g. "A min7".
    val detectedChordLabel: String = ""
)

enum class MidiOutputMode {
    INTERNAL,
    MIDI_OUT,
    BOTH
}

/**
 * Which clock drives the sequencer. EXTERNAL means "follow external MIDI clock
 * whenever one is available", falling back to the internal metro otherwise.
 */
enum class SyncSource {
    INTERNAL,
    EXTERNAL
}
