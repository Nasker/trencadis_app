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
        if (lastPitch >= 0 && lastPitch != pitch) noteOff(lastPitch, lastChannel)
        lastPitch = pitch
        lastChannel = channel
        destinations.filter { it.isAvailable() }.forEach { it.noteOn(pitch, velocity, channel) }
    }

    fun noteOff(pitch: Int, channel: Int = 1) {
        destinations.filter { it.isAvailable() }.forEach { it.noteOff(pitch, channel) }
    }

    fun allNotesOff(channel: Int = 1) {
        if (lastPitch >= 0) noteOff(lastPitch, channel)
        lastPitch = -1
    }
}
