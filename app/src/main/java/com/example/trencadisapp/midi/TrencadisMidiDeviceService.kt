package com.example.trencadisapp.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceService
import android.media.midi.MidiDeviceStatus
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver

class TrencadisMidiDeviceService : MidiDeviceService() {

    private var server: MidiDevice? = null
    private var outputPort: MidiInputPort? = null

    override fun onCreate() {
        super.onCreate()
        val midiManager = getSystemService(Context.MIDI_SERVICE) as MidiManager
        midiManager.openDevice(deviceInfo, { device ->
            server = device
        }, null)
    }

    override fun onGetInputPortReceivers(): Array<MidiReceiver> {
        return arrayOf(object : MidiReceiver() {
            override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                MidiBus.postInputEvent(msg.copyOfRange(offset, offset + count), timestamp)
            }
        })
    }

    override fun onDeviceStatusChanged(status: MidiDeviceStatus) {
        val srv = server ?: return
        val openCount = status.getOutputPortOpenCount(0)
        if (openCount > 0 && outputPort == null) {
            outputPort = srv.openInputPort(0)
            MidiBus.setOutputPort(outputPort)
        } else if (openCount == 0 && outputPort != null) {
            outputPort?.close()
            outputPort = null
            MidiBus.setOutputPort(null)
        }
    }

    override fun onDestroy() {
        outputPort?.close()
        server?.close()
        MidiBus.closeOutputPort()
        super.onDestroy()
    }
}
