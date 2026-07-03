package com.example.trencadisapp.midi

import android.util.Log
import com.example.trencadisapp.sync.NoteDestination

class MidiNoteDestination : NoteDestination {

    override val label = "USB MIDI"
    private val TAG = "MidiNoteDestination"

    override fun noteOn(pitch: Int, velocity: Int, channel: Int) {
        val port = MidiBus.notePort ?: return
        try {
            val msg = byteArrayOf(
                (0x90 or ((channel - 1) and 0x0F)).toByte(),
                (pitch and 0x7F).toByte(),
                (velocity and 0x7F).toByte()
            )
            port.send(msg, 0, msg.size)
        } catch (e: Exception) {
            Log.w(TAG, "noteOn failed: ${e.message}")
        }
    }

    override fun noteOff(pitch: Int, channel: Int) {
        val port = MidiBus.notePort ?: return
        try {
            val msg = byteArrayOf(
                (0x80 or ((channel - 1) and 0x0F)).toByte(),
                (pitch and 0x7F).toByte(),
                0x00
            )
            port.send(msg, 0, msg.size)
        } catch (e: Exception) {
            Log.w(TAG, "noteOff failed: ${e.message}")
        }
    }

    override fun isAvailable() = MidiBus.notePort != null
}
