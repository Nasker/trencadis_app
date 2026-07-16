package com.trencadis.app.audio

import kotlin.math.pow

object MusicConstants {
    
    val SCALE_NAMES = listOf(
        "Ionian", "Dorian", "Phrygian", "Lydian", "Mixolydian", 
        "Aeolian", "Locrian", "HarmMin", "Gipsy", "Hawaiian", "Blues", "Japanese"
    )
    
    val KEY_NAMES = listOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )
    
    val OCTAVE_MULTIPLIERS = floatArrayOf(1f, 2f, 4f, 8f, 16f, 32f, 64f)
    
    val FIGURE_NAMES = listOf(
        "Redonda", "Blanca", "Negra", "Corchea", "SemiCorchea", "Fusa", "SemiFusa"
    )
    
    val FIGURE_SYMBOLS = listOf("𝅝", "𝅗𝅥", "♩", "♪", "𝅘𝅥𝅯", "𝅘𝅥𝅰", "𝅘𝅥𝅱")
    
    val CHORD_TYPE_SHORT_NAMES = listOf(
        "Maj", "min", "dim", "aug", "M7", "m7", "7", "sus2", "sus4", "pwr"
    )
    
    // Semitone offsets for each chord type above, used for mapping hue to pitch.
    val CHORD_INTERVALS = listOf(
        listOf(0, 4, 7),        // Major
        listOf(0, 3, 7),        // minor
        listOf(0, 3, 6),        // diminished
        listOf(0, 4, 8),        // augmented
        listOf(0, 4, 7, 11),    // major 7th
        listOf(0, 3, 7, 10),    // minor 7th
        listOf(0, 4, 7, 10),    // dominant 7th
        listOf(0, 2, 7),        // sus2
        listOf(0, 5, 7),        // sus4
        listOf(0, 7)            // power
    )

    // Frequency ratios for each chord type, unfolded across two octaves so hue
    // can select a chord grade the same way scales select scale degrees.
    val CHORD_STEPS = CHORD_INTERVALS.map { intervals ->
        val ratios = mutableListOf<Float>()
        for (octave in 0 until 2) {
            for (interval in intervals) {
                ratios.add(2f.pow((octave * 12 + interval) / 12f))
            }
        }
        ratios.add(2f.pow(24f / 12f)) // top root at 2 octaves
        ratios.toFloatArray()
    }.toTypedArray()

    val CHORD_NOTE_COUNT = CHORD_STEPS.map { it.size - 1 }.toIntArray()
    
    // Diatonic scale step ratios for each scale
    // Each row represents a scale, columns are the chromatic steps mapped to diatonic
    val DIATONIC_STEPS = arrayOf(
        // Ionian (Major)
        floatArrayOf(1f, 1.1225f, 1.2599f, 1.3348f, 1.4983f, 1.6818f, 1.8877f, 2f, 2.2449f, 2.5198f, 2.6697f, 2.9966f, 3.3636f, 3.7755f, 4f),
        // Dorian
        floatArrayOf(1f, 1.1225f, 1.1892f, 1.3348f, 1.4983f, 1.6818f, 1.7818f, 2f, 2.2449f, 2.3784f, 2.6697f, 2.9966f, 3.3636f, 3.5636f, 4f),
        // Phrygian
        floatArrayOf(1f, 1.0595f, 1.1892f, 1.3348f, 1.4983f, 1.5874f, 1.7818f, 2f, 2.1189f, 2.3784f, 2.6697f, 2.9966f, 3.1748f, 3.5636f, 4f),
        // Lydian
        floatArrayOf(1f, 1.1225f, 1.2599f, 1.4142f, 1.4983f, 1.6818f, 1.8877f, 2f, 2.2449f, 2.5198f, 2.8284f, 2.9966f, 3.3636f, 3.7755f, 4f),
        // Mixolydian
        floatArrayOf(1f, 1.1225f, 1.2599f, 1.3348f, 1.4983f, 1.6818f, 1.7818f, 2f, 2.2449f, 2.5198f, 2.6697f, 2.9966f, 3.3636f, 3.5636f, 4f),
        // Aeolian (Natural Minor)
        floatArrayOf(1f, 1.1225f, 1.1892f, 1.3348f, 1.4983f, 1.5874f, 1.7818f, 2f, 2.2449f, 2.3784f, 2.6697f, 2.9966f, 3.1748f, 3.5636f, 4f),
        // Locrian
        floatArrayOf(1f, 1.0595f, 1.1892f, 1.3348f, 1.4142f, 1.5874f, 1.7818f, 2f, 2.1189f, 2.3784f, 2.6697f, 2.8284f, 3.1748f, 3.5636f, 4f),
        // Harmonic Minor
        floatArrayOf(1f, 1.1225f, 1.1892f, 1.3348f, 1.4983f, 1.5874f, 1.8877f, 2f, 2.2449f, 2.3784f, 2.6697f, 2.9966f, 3.1748f, 3.7755f, 4f),
        // Spanish Gipsy
        floatArrayOf(1f, 1.0595f, 1.2599f, 1.3348f, 1.4983f, 1.5874f, 1.7818f, 2f, 2.1189f, 2.5198f, 2.6697f, 2.9966f, 3.1748f, 3.5636f, 4f),
        // Hawaiian
        floatArrayOf(1f, 1.1225f, 1.1892f, 1.3348f, 1.4983f, 1.6818f, 1.8877f, 2f, 2.2449f, 2.3784f, 2.6697f, 2.9966f, 3.3636f, 3.7755f, 4f),
        // Blues (11 notes)
        floatArrayOf(1f, 1.1892f, 1.3348f, 1.4142f, 1.4983f, 1.7818f, 2f, 2.3784f, 2.6697f, 2.9966f, 3.5636f, 4f),
        // Japanese (9 notes)
        floatArrayOf(1f, 1.0595f, 1.3348f, 1.4983f, 1.5874f, 2f, 2.1189f, 2.6697f, 2.9966f, 3.1748f, 4f)
    )
    
    // Number of notes per scale (for mapping hue)
    val NOTES_PER_SCALE = intArrayOf(13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 11, 9)
    
    // Calculate frequency from key index (0=C, 11=B) relative to A4=440Hz
    fun getRootFrequency(keyIndex: Int): Float {
        // A4 = 440Hz, C4 is 9 semitones below A4
        // keyIndex 0 = C, keyIndex 9 = A
        return 440f * Math.pow(2.0, (-45.0 + keyIndex) / 12.0).toFloat()
    }
    
    // Calculate note frequency from hue, scale/key, and octave.
    // When useChordMapping is true, hue maps to a chord grade across two octaves;
    // otherwise it maps to a scale degree as before.
    fun calculateFrequency(
        hue: Float,        // 0-360
        scaleIndex: Int,
        keyIndex: Int,
        octaveIndex: Int,
        chordTypeIndex: Int = 0,
        useChordMapping: Boolean = false
    ): Float {
        val rootFreq = getRootFrequency(keyIndex)

        if (useChordMapping && chordTypeIndex in CHORD_STEPS.indices) {
            val chordSteps = CHORD_STEPS[chordTypeIndex]
            val nNotes = CHORD_NOTE_COUNT[chordTypeIndex]
            val chromStep = Math.round((hue / 360f) * nNotes).coerceIn(0, chordSteps.size - 1)
            val stepRatio = chordSteps[chromStep]
            return OCTAVE_MULTIPLIERS[octaveIndex] * rootFreq * stepRatio
        }

        val nNotes = NOTES_PER_SCALE[scaleIndex]
        val scaleSteps = DIATONIC_STEPS[scaleIndex]
        // Round like the original (chromStep can reach nNotes, the top note of
        // the two-octave table) instead of flooring, which never played it.
        val chromStep = Math.round((hue / 360f) * nNotes).coerceIn(0, scaleSteps.size - 1)
        val stepRatio = scaleSteps[chromStep]
        return OCTAVE_MULTIPLIERS[octaveIndex] * rootFreq * stepRatio
    }
}
