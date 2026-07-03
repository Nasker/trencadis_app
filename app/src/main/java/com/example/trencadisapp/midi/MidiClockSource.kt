package com.example.trencadisapp.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.trencadisapp.sync.ClockSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class MidiClockSource(
    private val context: Context,
    private val scope: CoroutineScope
) : ClockSource {

    private val TAG = "MidiClockSource"
    private val midiManager = context.getSystemService(MidiManager::class.java)

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _bpmFlow = MutableSharedFlow<Float>(replay = 1, extraBufferCapacity = 16)
    override val bpmFlow: Flow<Float> = _bpmFlow.asSharedFlow()

    private val _beatFlow = MutableSharedFlow<Int>(extraBufferCapacity = 32)
    override val beatFlow: Flow<Int> = _beatFlow.asSharedFlow()

    private val _keyFlow = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    override val keyFlow: Flow<Int> = _keyFlow.asSharedFlow()

    private val _scaleFlow = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    override val scaleFlow: Flow<Int> = _scaleFlow.asSharedFlow()

    private val tickTimestamps = ArrayDeque<Long>(25)
    private var beatCount = 0
    private var ticksThisBeat = 0
    private var collectJob: Job? = null

    // Tracks open devices: deviceId → (MidiDevice, MidiOutputPort, MidiInputPort?)
    private val openDevices = ConcurrentHashMap<Int, Triple<MidiDevice, MidiOutputPort, MidiInputPort?>>()

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            openUsbDevice(info)
        }
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            val id = info.id
            openDevices.remove(id)?.let { (device, outPort, inPort) ->
                outPort.close()
                inPort?.close()
                device.close()
                Log.d(TAG, "Disconnected: ${info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)}")
            }
            if (openDevices.isEmpty()) {
                MidiBus.setUsbNotePort(null)
                _isConnected.value = false
            }
        }
    }

    override fun connect() {
        // Listen for virtual-device path (MidiDeviceService)
        collectJob = scope.launch {
            MidiBus.midiInputEvents.collect { (data, timestamp) ->
                if (data.isNotEmpty()) handleMidiMessage(data, timestamp)
            }
        }
        // Register for USB device plug/unplug
        midiManager?.registerDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        // Scan devices already connected
        midiManager?.devices?.forEach { openUsbDevice(it) }
    }

    override fun disconnect() {
        collectJob?.cancel()
        collectJob = null
        midiManager?.unregisterDeviceCallback(deviceCallback)
        openDevices.values.forEach { (device, outPort, inPort) ->
            outPort.close(); inPort?.close(); device.close()
        }
        openDevices.clear()
        MidiBus.setUsbNotePort(null)
        tickTimestamps.clear()
        beatCount = 0
        ticksThisBeat = 0
        _isConnected.value = false
    }

    private fun openUsbDevice(info: MidiDeviceInfo) {
        // Skip our own virtual device — only care about hardware/system USB devices
        if (info.type == MidiDeviceInfo.TYPE_VIRTUAL) return
        if (info.outputPortCount == 0) return
        midiManager?.openDevice(info, { device ->
            if (device == null) {
                Log.w(TAG, "Failed to open ${info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)}")
                return@openDevice
            }
            val outPort = device.openOutputPort(0)
            if (outPort == null) { device.close(); return@openDevice }

            outPort.connect(object : MidiReceiver() {
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    val data = msg.copyOfRange(offset, offset + count)
                    handleMidiMessage(data, timestamp)
                }
            })

            val inPort: MidiInputPort? = if (info.inputPortCount > 0) device.openInputPort(0) else null
            if (inPort != null) MidiBus.setUsbNotePort(inPort)

            openDevices[info.id] = Triple(device, outPort, inPort)
            Log.d(TAG, "Connected: ${info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)}")
        }, Handler(Looper.getMainLooper()))
    }

    private fun handleMidiMessage(data: ByteArray, timestamp: Long) {
        when (data[0].toInt() and 0xFF) {
            0xF8 -> onClockTick(timestamp)
            0xFA -> {
                _isConnected.value = true
                beatCount = 0
                ticksThisBeat = 0
                tickTimestamps.clear()
            }
            0xFB -> _isConnected.value = true
            0xFC -> _isConnected.value = false
        }
    }

    private fun onClockTick(timestampNanos: Long) {
        _isConnected.value = true
        tickTimestamps.addLast(timestampNanos)
        if (tickTimestamps.size > 25) tickTimestamps.removeFirst()

        if (tickTimestamps.size >= 2) {
            val spanNanos = tickTimestamps.last() - tickTimestamps.first()
            val intervals = tickTimestamps.size - 1
            val avgIntervalMs = spanNanos.toDouble() / intervals / 1_000_000.0
            val bpm = (60_000.0 / (avgIntervalMs * 24)).toFloat().coerceIn(20f, 300f)
            _bpmFlow.tryEmit(bpm)
        }

        ticksThisBeat++
        if (ticksThisBeat >= 24) {
            ticksThisBeat = 0
            _beatFlow.tryEmit(beatCount++)
        }
    }
}
