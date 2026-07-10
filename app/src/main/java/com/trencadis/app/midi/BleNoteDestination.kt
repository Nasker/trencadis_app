package com.trencadis.app.midi

import com.trencadis.app.sync.NoteDestination

class BleNoteDestination(private val peripheral: BleMidiPeripheral) : NoteDestination {

    override val label = "BLE MIDI"

    override fun noteOn(pitch: Int, velocity: Int, channel: Int) {
        if (!peripheral.isAdvertising) return
        peripheral.sendMidi(byteArrayOf(
            (0x90 or ((channel - 1) and 0x0F)).toByte(),
            (pitch and 0x7F).toByte(),
            (velocity and 0x7F).toByte()
        ))
    }

    override fun noteOff(pitch: Int, channel: Int) {
        if (!peripheral.isAdvertising) return
        peripheral.sendMidi(byteArrayOf(
            (0x80 or ((channel - 1) and 0x0F)).toByte(),
            (pitch and 0x7F).toByte(),
            0x00
        ))
    }

    override fun isAvailable() = peripheral.isAdvertising
}
