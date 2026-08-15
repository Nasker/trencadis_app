package com.trencadis.app.midi

import com.trencadis.app.audio.MusicConstants

/** A parsed note-on/off from an external MIDI device. Channel is 1-16. */
data class NoteEvent(
    val note: Int,
    val velocity: Int,
    val isOn: Boolean,
    val channel: Int
)

/**
 * A chord recognized from held MIDI notes, expressed in the app's own musical
 * vocabulary: [rootPc] indexes MusicConstants.KEY_NAMES and [chordTypeIndex]
 * indexes MusicConstants.CHORD_INTERVALS / CHORD_TYPE_SHORT_NAMES, so a
 * detection can drive the existing key + chord-mapping state directly.
 */
data class DetectedChord(
    val rootPc: Int,
    val chordTypeIndex: Int
) {
    val label: String
        get() = "${MusicConstants.KEY_NAMES[rootPc]} ${MusicConstants.CHORD_TYPE_SHORT_NAMES[chordTypeIndex]}"
}

/**
 * Tracks the set of currently held external MIDI notes and matches their
 * pitch classes against the chord templates in MusicConstants.CHORD_INTERVALS.
 *
 * Detection is latching by design: partial sets (single notes, unrecognized
 * dyads, mid-strum states) return null so the caller keeps the last chord
 * until a new one is actually recognized. Inversions are handled by trying
 * every held pitch class as candidate root, preferring the bass note.
 */
class HarmonyAnalyzer {

    private val activeNotes = sortedSetOf<Int>()

    fun noteOn(note: Int) {
        activeNotes.add(note)
    }

    fun noteOff(note: Int) {
        activeNotes.remove(note)
    }

    fun reset() {
        activeNotes.clear()
    }

    fun analyze(): DetectedChord? {
        if (activeNotes.size < 2) return null

        val bassPc = activeNotes.first() % 12
        val pitchClasses = activeNotes.mapTo(sortedSetOf()) { it % 12 }

        // Root-position reading first, then the remaining pitch classes so
        // inversions (e.g. E-G-C) still resolve to the intended chord.
        val candidateRoots = listOf(bassPc) + (pitchClasses - bassPc)
        for (root in candidateRoots) {
            val intervals = pitchClasses.mapTo(mutableSetOf()) { ((it - root) % 12 + 12) % 12 }
            val typeIndex = MusicConstants.CHORD_INTERVALS.indexOfFirst { it.toSet() == intervals }
            if (typeIndex >= 0) return DetectedChord(root, typeIndex)
        }
        return null
    }
}
