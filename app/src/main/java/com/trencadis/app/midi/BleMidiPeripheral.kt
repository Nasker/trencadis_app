package com.trencadis.app.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

@SuppressLint("MissingPermission")
class BleMidiPeripheral(private val context: Context) {

    companion object {
        val MIDI_SERVICE_UUID: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        val MIDI_CHAR_UUID: UUID    = UUID.fromString("7772E5DB-3868-4112-A1A9-F2669D106BF3")
        val CCCD_UUID: UUID         = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val TAG = "BleMidiPeripheral"
    }

    private val btManager  = context.getSystemService(BluetoothManager::class.java)
    private val btAdapter  = btManager?.adapter

    @Volatile private var gattServer: BluetoothGattServer? = null
    private var midiChar: BluetoothGattCharacteristic? = null
    private val connected = CopyOnWriteArraySet<BluetoothDevice>()
    private val notifyEnabled = CopyOnWriteArraySet<BluetoothDevice>()
    private var originalBtName: String? = null

    @Volatile var isAdvertising = false
    @Volatile private var isStarting = false

    var onConnectionChanged: ((connected: Boolean) -> Unit)? = null

    // ── Public API ─────────────────────────────────────────────────────────────

    fun startAdvertising() {
        if (isAdvertising || isStarting) {
            Log.d(TAG, "Already advertising/starting — ignoring duplicate call")
            return
        }
        if (btAdapter == null || !btAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available/enabled")
            return
        }
        if (btAdapter.bluetoothLeAdvertiser == null) {
            Log.w(TAG, "BLE advertising not supported on this device")
            return
        }
        isStarting = true
        // Pin a short name so it fits in the advertising packet alongside the service UUID.
        // A 128-bit service UUID + flags already uses 21 bytes of the 31-byte limit;
        // "Trencadis" (9 chars) + 2 overhead = 11 bytes → total 32 — just over. We'll
        // keep it in the scan response and set adapter name to ≤8 chars to guarantee fit.
        originalBtName = btAdapter.name
        btAdapter.setName("Trencadis")
        setupGattServer()
        // startLeAdvertising() is called from onServiceAdded once the GATT service is registered
    }

    fun stopAdvertising() {
        isStarting = false
        try { btAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
        isAdvertising = false
        gattServer?.close()
        gattServer = null
        midiChar = null
        connected.clear()
        notifyEnabled.clear()
        onConnectionChanged?.invoke(false)
        originalBtName?.let { btAdapter?.setName(it) }
        originalBtName = null
        Log.d(TAG, "Stopped advertising")
    }

    fun sendMidi(midiBytes: ByteArray) {
        val ch = midiChar ?: return
        if (notifyEnabled.isEmpty()) return
        val packet = encodeBlePacket(midiBytes)
        ch.value = packet
        notifyEnabled.forEach { device ->
            try { gattServer?.notifyCharacteristicChanged(device, ch, false) }
            catch (e: Exception) { Log.w(TAG, "notify failed: ${e.message}") }
        }
    }

    // ── GATT server setup ──────────────────────────────────────────────────────

    private fun setupGattServer() {
        val service = BluetoothGattService(MIDI_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val char = BluetoothGattCharacteristic(
            MIDI_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
            BluetoothGattCharacteristic.PROPERTY_WRITE or   // some centrals use regular write
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        ).also { it.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE }
        char.addDescriptor(cccd)
        service.addCharacteristic(char)
        midiChar = char

        gattServer = btManager?.openGattServer(context, gattCallback)
        val added = gattServer?.addService(service)
        if (added == false) {
            Log.e(TAG, "addService() rejected — BLE stack busy, stopping")
            stopAdvertising()
        }
    }

    private fun startLeAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        // Service UUID in the adv packet; device name in scan response to avoid packet overflow
        val advData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(MIDI_SERVICE_UUID))
            .setIncludeTxPowerLevel(false)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
        btAdapter?.bluetoothLeAdvertiser?.startAdvertising(settings, advData, scanResponse, advertiseCallback)
    }

    // ── Callbacks ──────────────────────────────────────────────────────────────

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            Log.d(TAG, "BLE MIDI advertising started")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "BLE advertising failed errorCode=$errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            isStarting = false
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "GATT service registered, starting advertising")
                startLeAdvertising()
            } else {
                Log.w(TAG, "GATT service add failed: status=$status — clearing up")
                stopAdvertising()
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.add(device)
                Log.d(TAG, "Connected: ${device.address}")
            } else {
                connected.remove(device)
                notifyEnabled.remove(device)
                val reason = when (status) {
                    BluetoothGatt.GATT_SUCCESS -> "remote disconnect"
                    8   -> "connection timeout"
                    19  -> "remote terminated (19)"
                    22  -> "local terminated (22)"
                    34  -> "LMP timeout (34)"
                    133 -> "GATT_ERROR / timeout (133) \u2014 try shorter conn interval"
                    else -> "status=$status"
                }
                Log.w(TAG, "Disconnected: ${device.address} \u2014 $reason")
            }
            onConnectionChanged?.invoke(connected.isNotEmpty())
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, byteArrayOf())
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (responseNeeded)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            if (value.size >= 3) decodeBlePacket(value)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    notifyEnabled.add(device)
                    Log.d(TAG, "Notifications enabled: ${device.address}")
                } else {
                    notifyEnabled.remove(device)
                }
            }
            if (responseNeeded)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            val value = if (notifyEnabled.contains(device))
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }
    }

    // ── BLE MIDI packet codec ──────────────────────────────────────────────────

    /**
     * BLE MIDI framing:
     *   Byte 0: Header  — bit7=1, bit6=0, bits5..0 = timestamp[13..8]
     *   Byte 1: TS byte — bit7=1,           bits6..0 = timestamp[6..0]
     *   Byte 2+: MIDI bytes (one or more messages, each optionally preceded by a new TS byte)
     */
    private fun decodeBlePacket(packet: ByteArray) {
        if (packet.size < 3) return
        var i = 1                           // skip header
        while (i < packet.size) {
            if (packet[i].toInt() and 0x80 != 0) i++   // optional interleaved timestamp
            if (i >= packet.size) break
            val status = packet[i].toInt() and 0xFF
            val len = midiMsgLen(status, packet.size - i)
            if (len > 0 && i + len <= packet.size) {
                MidiBus.postInputEvent(packet.copyOfRange(i, i + len), System.nanoTime())
                i += len
            } else break
        }
    }

    private fun encodeBlePacket(midi: ByteArray): ByteArray {
        val ts = System.currentTimeMillis()
        val header = (0x80 or ((ts shr 7) and 0x3F).toInt()).toByte()
        val tsByte = (0x80 or (ts and 0x7F).toInt()).toByte()
        return byteArrayOf(header, tsByte) + midi
    }

    private fun midiMsgLen(status: Int, available: Int): Int = when (status and 0xF0) {
        0x80, 0x90, 0xA0, 0xB0, 0xE0 -> 3
        0xC0, 0xD0                    -> 2
        0xF0 -> when (status) {
            0xF8, 0xFA, 0xFB, 0xFC, 0xFE, 0xFF -> 1
            0xF2                                -> 3
            0xF3                                -> 2
            else                                -> available
        }
        else -> 1
    }
}
