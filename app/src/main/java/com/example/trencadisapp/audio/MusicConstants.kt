package com.example.trencadisapp.audio

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
    
    // Calculate note frequency from hue, scale, key, and octave
    fun calculateFrequency(
        hue: Float,        // 0-360
        scaleIndex: Int,
        keyIndex: Int,
        octaveIndex: Int
    ): Float {
        val rootFreq = getRootFrequency(keyIndex)
        val nNotes = NOTES_PER_SCALE[scaleIndex]
        val scaleSteps = DIATONIC_STEPS[scaleIndex]
        // Round like the original (chromStep can reach nNotes, the top note of
        // the two-octave table) instead of flooring, which never played it.
        val chromStep = Math.round((hue / 360f) * nNotes).coerceIn(0, scaleSteps.size - 1)
        val stepRatio = scaleSteps[chromStep]
        return OCTAVE_MULTIPLIERS[octaveIndex] * rootFreq * stepRatio
    }
}
