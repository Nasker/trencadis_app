package com.trencadis.app.midi

/**
 * MIDI input and arpeggiation state for Trencadis 2.0
 */
data class MidiInputState(
    val enabled: Boolean = false,
    val harmonyMode: HarmonyMode = HarmonyMode.INTERNAL_SCALES,
    val detectedChord: Chord? = null,
    val arpeggioPattern: ArpeggioPattern = ArpeggioPattern.UP,
    val activeNotes: Set<Int> = emptySet(),
    val arpeggioState: ArpeggioState = ArpeggioState(),
    val inputDevice: String? = null
)

enum class HarmonyMode {
    INTERNAL_SCALES,     // Use built-in scales only
    EXTERNAL_CHORD,      // Follow incoming MIDI chords completely
    HYBRID               // Blend internal scales with external chords
}

data class Chord(
    val root: Int,              // MIDI note number (0-127)
    val type: ChordType,
    val notes: Set<Int>,        // All notes in chord
    val inversion: Int = 0,     // Chord inversion (0=root position)
    val timestamp: Long = System.currentTimeMillis()
)

enum class ChordType {
    MAJOR, MINOR, DIMINISHED, AUGMENTED,
    MAJOR_SEVENTH, MINOR_SEVENTH, DOMINANT_SEVENTH,
    SUS2, SUS4, POWER_CHORD, UNKNOWN
}

data class ArpeggioState(
    val isPlaying: Boolean = false,
    val currentStep: Int = 0,
    val tempo: Float = 120f,        // BPM
    val gateLength: Float = 0.5f,   // Note duration ratio (0.5 = 50% of beat)
    val octaveRange: Int = 1,        // Octave range for arpeggio
    val pattern: ArpeggioPattern = ArpeggioPattern.UP,
    val holdNotes: Boolean = false   // Hold notes until next chord
)

enum class ArpeggioPattern {
    UP,                 // Ascending notes
    DOWN,               // Descending notes
    UP_DOWN,            // Ascending then descending
    DOWN_UP,            // Descending then ascending
    RANDOM,             // Random order
    PATTERN_1,          // Custom pattern 1
    PATTERN_2,          // Custom pattern 2
    PATTERN_3           // Custom pattern 3
}

// Arpeggio step definitions for custom patterns
data class ArpeggioStep(
    val noteOffset: Int,     // Semitone offset from chord root
    val octaveOffset: Int,   // Octave offset
    val duration: Float      // Relative duration
)

val ARPEGGIO_PATTERNS = mapOf(
    ArpeggioPattern.PATTERN_1 to listOf(
        ArpeggioStep(0, 0, 1f),   // Root
        ArpeggioStep(2, 0, 0.5f), // Third
        ArpeggioStep(4, 0, 1f),   // Fifth
        ArpeggioStep(7, 0, 0.5f)  // Octave
    ),
    ArpeggioPattern.PATTERN_2 to listOf(
        ArpeggioStep(0, 0, 0.5f),   // Root
        ArpeggioStep(7, -1, 0.5f),  // Lower octave
        ArpeggioStep(0, 0, 1f),     // Root
        ArpeggioStep(4, 0, 0.5f)    // Fifth
    ),
    ArpeggioPattern.PATTERN_3 to listOf(
        ArpeggioStep(0, 1, 0.33f),  // Upper octave root
        ArpeggioStep(4, 0, 0.33f),  // Fifth
        ArpeggioStep(0, 0, 0.33f),  // Root
        ArpeggioStep(2, 0, 1f)      // Third
    )
)
