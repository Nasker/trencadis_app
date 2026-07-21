package com.trencadis.app.midi

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
import com.trencadis.app.sync.ClockSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // Realtime callbacks, invoked synchronously on the MIDI delivery thread so
    // tick timing is not smeared by dispatcher hops. Keep the handlers cheap.
    @Volatile var onTick: (() -> Unit)? = null
    @Volatile var onStart: (() -> Unit)? = null
    @Volatile var onStop: (() -> Unit)? = null

    @Volatile private var lastTickNanos = 0L

    private val tickTimestamps = ArrayDeque<Long>(25)
    private var beatCount = 0
    private var ticksThisBeat = 0
    private var lastEmittedBpm = 0f
    private var watchdogJob: Job? = null

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
        // Listen for virtual-device path (MidiDeviceService). Handled synchronously
        // on the MIDI delivery thread — routing ticks through a SharedFlow +
        // coroutine dispatcher batches and jitters them.
        MidiBus.realtimeListener = { data, timestamp ->
            if (data.isNotEmpty()) handleMidiMessage(data, timestamp)
        }
        // Register for USB device plug/unplug
        midiManager?.registerDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        // Scan devices already connected
        midiManager?.devices?.forEach { openUsbDevice(it) }
        // If the DAW stops sending clock without a Stop message, drop the lock
        // so the internal metro can take over again.
        watchdogJob = scope.launch {
            while (true) {
                delay(500)
                if (_isConnected.value && lastTickNanos != 0L &&
                    System.nanoTime() - lastTickNanos > 1_500_000_000L
                ) {
                    _isConnected.value = false
                    onStop?.invoke()
                }
            }
        }
    }

    override fun disconnect() {
        MidiBus.realtimeListener = null
        watchdogJob?.cancel()
        watchdogJob = null
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
        // Realtime messages are single status bytes (>= 0xF8) and may arrive
        // interleaved with channel messages in the same packet, so scan every
        // byte. Data bytes are <= 0x7F and can never false-match.
        for (b in data) {
            when (b.toInt() and 0xFF) {
                0xF8 -> onClockTick(timestamp)
                0xFA -> {
                    _isConnected.value = true
                    beatCount = 0
                    ticksThisBeat = 0
                    tickTimestamps.clear()
                    onStart?.invoke()
                }
                0xFB -> _isConnected.value = true
                0xFC -> {
                    _isConnected.value = false
                    onStop?.invoke()
                }
            }
        }
    }

    private fun onClockTick(timestampNanos: Long) {
        _isConnected.value = true
        val now = System.nanoTime()
        lastTickNanos = now
        // Senders are allowed to pass timestamp 0 ("deliver now"); a zero span
        // would pin the computed BPM at the coerce bounds. Both MIDI timestamps
        // and nanoTime share the CLOCK_MONOTONIC base, so receipt time is a
        // consistent fallback.
        tickTimestamps.addLast(if (timestampNanos > 0) timestampNanos else now)
        if (tickTimestamps.size > 25) tickTimestamps.removeFirst()

        if (tickTimestamps.size >= 2) {
            val spanNanos = tickTimestamps.last() - tickTimestamps.first()
            val intervals = tickTimestamps.size - 1
            if (spanNanos > 0) {
                val avgIntervalMs = spanNanos.toDouble() / intervals / 1_000_000.0
                val bpm = (60_000.0 / (avgIntervalMs * 24)).toFloat().coerceIn(20f, 300f)
                // Re-emitting 24x per beat churns state and makes the tempo
                // display flicker; only publish meaningful changes.
                if (kotlin.math.abs(bpm - lastEmittedBpm) >= 0.5f) {
                    lastEmittedBpm = bpm
                    _bpmFlow.tryEmit(bpm)
                }
            }
        }

        ticksThisBeat++
        if (ticksThisBeat >= 24) {
            ticksThisBeat = 0
            _beatFlow.tryEmit(beatCount++)
        }

        onTick?.invoke()
    }
}
