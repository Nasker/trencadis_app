package com.example.trencadisapp.midi

import com.example.trencadisapp.audio.PdAudioEngine
import com.example.trencadisapp.sync.NoteDestination

class PdNoteDestination(private val pdEngine: PdAudioEngine) : NoteDestination {

    override val label = "Internal Pd"

    @Volatile private var active = true

    fun setActive(on: Boolean) {
        active = on
        if (!on) pdEngine.setGain(0f)
    }

    fun isActive() = active

    override fun noteOn(pitch: Int, velocity: Int, channel: Int) {}
    override fun noteOff(pitch: Int, channel: Int) {}
    override fun isAvailable() = active
}
