package com.trencadis.app.midi

import android.media.midi.MidiInputPort
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object MidiBus {

    private val _midiInputEvents = MutableSharedFlow<Pair<ByteArray, Long>>(
        extraBufferCapacity = 256
    )
    val midiInputEvents: SharedFlow<Pair<ByteArray, Long>> = _midiInputEvents.asSharedFlow()

    /** Port belonging to our MidiDeviceService virtual device (service → DAW direction). */
    @Volatile var deviceOutputPort: MidiInputPort? = null
        private set

    /** Input port of the system USB MIDI device — what we write notes TO (phone → DAW). */
    @Volatile var usbNotePort: MidiInputPort? = null
        private set

    fun postInputEvent(data: ByteArray, timestampNanos: Long) {
        _midiInputEvents.tryEmit(data to timestampNanos)
    }

    fun setOutputPort(port: MidiInputPort?) {
        deviceOutputPort = port
    }

    fun setUsbNotePort(port: MidiInputPort?) {
        usbNotePort?.close()
        usbNotePort = port
    }

    fun closeOutputPort() {
        deviceOutputPort?.close()
        deviceOutputPort = null
    }

    fun closeUsbNotePort() {
        usbNotePort?.close()
        usbNotePort = null
    }

    /** Any writable port available for note output. USB device takes priority. */
    val notePort: MidiInputPort? get() = usbNotePort ?: deviceOutputPort
}
