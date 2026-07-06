package com.example.trencadisapp.midi

import com.example.trencadisapp.sync.NoteDestination

class NoteRouter {

    private val destinations = mutableListOf<NoteDestination>()
    private var lastPitch = -1
    private var lastChannel = 1

    fun add(destination: NoteDestination) {
        if (!destinations.contains(destination)) destinations.add(destination)
    }

    fun remove(destination: NoteDestination) {
        destinations.remove(destination)
    }

    fun noteOn(pitch: Int, velocity: Int, channel: Int = 1) {
        // Always release the previous note first — including same-pitch repeats.
        // Re-sending note-on without a note-off stacks voices on the receiver,
        // and the eventual single note-off leaves one instance hanging forever.
        if (lastPitch >= 0) noteOff(lastPitch, lastChannel)
        lastPitch = pitch
        lastChannel = channel
        destinations.filter { it.isAvailable() }.forEach { it.noteOn(pitch, velocity, channel) }
    }

    fun noteOff(pitch: Int, channel: Int = 1) {
        destinations.filter { it.isAvailable() }.forEach { it.noteOff(pitch, channel) }
        if (pitch == lastPitch) lastPitch = -1
    }

    fun allNotesOff(channel: Int = 1) {
        // Release on the channel the note was actually sent on — the caller's
        // channel may already reflect a new selection.
        if (lastPitch >= 0) noteOff(lastPitch, lastChannel)
        lastPitch = -1
    }
}
