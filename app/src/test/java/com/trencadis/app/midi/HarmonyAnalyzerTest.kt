package com.trencadis.app.midi

import com.trencadis.app.audio.MusicConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HarmonyAnalyzerTest {

    private val analyzer = HarmonyAnalyzer()

    @Test
    fun `major triad in root position`() {
        // C major: C4 (60), E4 (64), G4 (67)
        analyzer.noteOn(60)
        analyzer.noteOn(64)
        analyzer.noteOn(67)

        val chord = analyzer.analyze()
        assertEquals(DetectedChord(0, 0), chord) // C Major
        assertEquals("C Maj", chord?.label)
    }

    @Test
    fun `minor triad first inversion`() {
        // A minor in first inversion: C4 (60), A3 (57), E4 (64)
        // Sorted by MIDI note: A3=57, C4=60, E4=64
        analyzer.noteOn(60)
        analyzer.noteOn(57)
        analyzer.noteOn(64)

        val chord = analyzer.analyze()
        // Should still resolve as A minor, not C6; root = A (9)
        assertEquals(DetectedChord(9, 1), chord) // A minor
    }

    @Test
    fun `dominant seventh`() {
        // G7: G3=55, B3=59, D4=62, F4=65
        analyzer.noteOn(55)
        analyzer.noteOn(59)
        analyzer.noteOn(62)
        analyzer.noteOn(65)

        val chord = analyzer.analyze()
        assertEquals(DetectedChord(7, 6), chord) // G 7
    }

    @Test
    fun `single note returns null and keeps last chord`() {
        analyzer.noteOn(60)
        assertNull(analyzer.analyze())
    }

    @Test
    fun `unrecognized dyad returns null`() {
        analyzer.noteOn(60)
        analyzer.noteOn(61) // C + C#
        assertNull(analyzer.analyze())
    }

    @Test
    fun `releasing notes clears state`() {
        analyzer.noteOn(60)
        analyzer.noteOn(64)
        analyzer.noteOn(67)
        assertEquals(DetectedChord(0, 0), analyzer.analyze())

        analyzer.noteOff(60)
        analyzer.noteOff(64)
        analyzer.noteOff(67)
        assertNull(analyzer.analyze())
    }

    @Test
    fun `all chord templates are indexed correctly`() {
        MusicConstants.CHORD_INTERVALS.forEachIndexed { index, intervals ->
            val root = 48 // C3
            val notes = intervals.map { root + it }
            val test = HarmonyAnalyzer()
            notes.forEach { test.noteOn(it) }
            val chord = test.analyze()
            assertEquals("Index $index failed for ${MusicConstants.CHORD_TYPE_SHORT_NAMES[index]}",
                DetectedChord(0, index), chord)
        }
    }

    @Test
    fun `sus4 chord across octaves`() {
        // C sus4: C4=60, F4=65, G4=67
        analyzer.noteOn(60)
        analyzer.noteOn(65)
        analyzer.noteOn(67)

        assertEquals(DetectedChord(0, 8), analyzer.analyze()) // C sus4
    }

    @Test
    fun `power chord`() {
        // C power: C4=60, G4=67
        analyzer.noteOn(60)
        analyzer.noteOn(67)

        assertEquals(DetectedChord(0, 9), analyzer.analyze()) // C pwr
    }
}
