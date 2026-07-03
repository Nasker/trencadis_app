package com.example.trencadisapp.sync

interface NoteDestination {
    val label: String
    fun noteOn(pitch: Int, velocity: Int, channel: Int = 1)
    fun noteOff(pitch: Int, channel: Int = 1)
    fun isAvailable(): Boolean
}
