package com.kickr.trainer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.kickr.trainer.model.KickrDevice
import com.kickr.trainer.model.TrainerData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

@SuppressLint("MissingPermission")
class KickrBluetoothService(private val context: Context) {
    
    private val bluetoothAdapter: BluetoothAdapter? = 
        BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner: BluetoothLeScanner? = 
        bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _discoveredDevices = MutableStateFlow<List<KickrDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<KickrDevice>> = _discoveredDevices.asStateFlow()
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _trainerData = MutableStateFlow(TrainerData())
    val trainerData: StateFlow<TrainerData> = _trainerData.asStateFlow()
    
    private var currentPower = 0
    private var currentCadence = 0
    private var currentSpeed = 0f
    private var currentHeartRate = 0
    
    // Cycling Power Measurement parsing
    private var lastCrankRevolutions: Int? = null
    private var lastCrankEventTime: Int? = null
    private var lastWheelRevolutions: Long? = null
    private var lastWheelEventTime: Int? = null
    
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "Unknown Device"
            val rssi = result.rssi
            
            // Show all devices with names, or devices advertising cycling services
            if (name != "Unknown Device" || 
                result.scanRecord?.serviceUuids?.any { 
                    it.uuid == GattAttributes.CYCLING_POWER_SERVICE ||
                    it.uuid == GattAttributes.CYCLING_SPEED_CADENCE_SERVICE ||
                    it.uuid == GattAttributes.HEART_RATE_SERVICE
                } == true) {
                
                val kickrDevice = KickrDevice(device, name, rssi)
                val currentList = _discoveredDevices.value.toMutableList()
                
                // Update or add device
                val existingIndex = currentList.indexOfFirst { it.device.address == device.address }
                if (existingIndex >= 0) {
                    currentList[existingIndex] = kickrDevice
                } else {
                    currentList.add(kickrDevice)
                }
                
                _discoveredDevices.value = currentList
                Log.d(TAG, "Found device: $name (${device.address}) RSSI: $rssi Services: ${result.scanRecord?.serviceUuids}")
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _connectionState.value = ConnectionState.Connected
                    // Discover services
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _connectionState.value = ConnectionState.Disconnected
                    bluetoothGatt = null
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                enableNotifications(gatt)
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
                _connectionState.value = ConnectionState.Error("Service discovery failed")
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                GattAttributes.CYCLING_POWER_MEASUREMENT -> {
                    parseCyclingPowerMeasurement(value)
                }
                GattAttributes.CSC_MEASUREMENT -> {
                    parseCscMeasurement(value)
                }
                GattAttributes.HEART_RATE_MEASUREMENT -> {
                    parseHeartRateMeasurement(value)
                }
            }
            updateTrainerData()
        }
    }
    
    fun startScan() {
        if (_isScanning.value) return
        
        _discoveredDevices.value = emptyList()
        
        // Scan with no filters to find all BLE devices
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
        _isScanning.value = true
        Log.d(TAG, "Started BLE scan (no filters)")
    }
    
    fun stopScan() {
        if (!_isScanning.value) return
        
        bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
        Log.d(TAG, "Stopped BLE scan")
    }
    
    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = ConnectionState.Connecting
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }
    
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    fun setResistance(resistancePercent: Int) {
        val gatt = bluetoothGatt ?: return
        
        // Try Fitness Machine Service first (more common for resistance control)
        gatt.getService(GattAttributes.FITNESS_MACHINE_SERVICE)?.let { service ->
            service.getCharacteristic(GattAttributes.FITNESS_MACHINE_CONTROL_POINT)?.let { characteristic ->
                // Set Target Resistance Level command (opcode 0x04)
                // Resistance level is in 0.1 percent increments
                val resistanceValue = (resistancePercent * 10).toShort()
                val command = byteArrayOf(
                    0x04.toByte(), // Opcode: Set Target Resistance Level
                    (resistanceValue.toInt() and 0xFF).toByte(),
                    ((resistanceValue.toInt() shr 8) and 0xFF).toByte()
                )
                characteristic.value = command
                gatt.writeCharacteristic(characteristic)
                Log.d(TAG, "Set resistance to $resistancePercent%")
                return
            }
        }
        
        Log.w(TAG, "Resistance control not available on this device")
    }
    
    private fun enableNotifications(gatt: BluetoothGatt) {
        // Enable Cycling Power notifications
        gatt.getService(GattAttributes.CYCLING_POWER_SERVICE)?.let { service ->
            service.getCharacteristic(GattAttributes.CYCLING_POWER_MEASUREMENT)?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
        }
        
        // Enable CSC notifications
        gatt.getService(GattAttributes.CYCLING_SPEED_CADENCE_SERVICE)?.let { service ->
            service.getCharacteristic(GattAttributes.CSC_MEASUREMENT)?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
        }
        
        // Enable Heart Rate notifications if available
        gatt.getService(GattAttributes.HEART_RATE_SERVICE)?.let { service ->
            service.getCharacteristic(GattAttributes.HEART_RATE_MEASUREMENT)?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
        }
    }
    
    private fun parseCyclingPowerMeasurement(data: ByteArray) {
        if (data.size < 4) return
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val flags = buffer.getShort().toInt() and 0xFFFF
        
        // Instantaneous Power is always present (2 bytes)
        val power = buffer.getShort().toInt() and 0xFFFF
        currentPower = power
        
        Log.d(TAG, "Power: $power W")
        
        // Parse additional fields based on flags
        if (flags and 0x01 != 0) { // Pedal Power Balance Present
            buffer.get()
        }
        
        if (flags and 0x04 != 0) { // Accumulated Torque Present
            buffer.getShort()
        }
        
        if (flags and 0x10 != 0) { // Wheel Revolution Data Present
            if (buffer.remaining() >= 6) {
                val wheelRevolutions = buffer.getInt().toLong() and 0xFFFFFFFFL
                val wheelEventTime = buffer.getShort().toInt() and 0xFFFF
                
                if (lastWheelRevolutions != null && lastWheelEventTime != null) {
                    val revDelta = wheelRevolutions - lastWheelRevolutions!!
                    var timeDelta = wheelEventTime - lastWheelEventTime!!
                    if (timeDelta < 0) timeDelta += 65536
                    
                    if (timeDelta > 0) {
                        // Speed calculation (assuming 700c wheel: 2.105m circumference)
                        val wheelCircumference = 2.105f
                        val speed = (revDelta * wheelCircumference * 1024.0f / timeDelta) * 3.6f // km/h
                        currentSpeed = if (speed > 0) speed else 0f
                    }
                }
                
                lastWheelRevolutions = wheelRevolutions
                lastWheelEventTime = wheelEventTime
            }
        }
        
        if (flags and 0x20 != 0) { // Crank Revolution Data Present
            if (buffer.remaining() >= 4) {
                val crankRevolutions = buffer.getShort().toInt() and 0xFFFF
                val crankEventTime = buffer.getShort().toInt() and 0xFFFF
                
                if (lastCrankRevolutions != null && lastCrankEventTime != null) {
                    val revDelta = crankRevolutions - lastCrankRevolutions!!
                    var timeDelta = crankEventTime - lastCrankEventTime!!
                    if (timeDelta < 0) timeDelta += 65536
                    
                    if (timeDelta > 0) {
                        // Cadence calculation
                        val cadence = (revDelta * 1024 * 60) / timeDelta
                        currentCadence = if (cadence > 0) cadence else 0
                    }
                }
                
                lastCrankRevolutions = crankRevolutions
                lastCrankEventTime = crankEventTime
            }
        }
    }
    
    private fun parseCscMeasurement(data: ByteArray) {
        if (data.isEmpty()) return
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val flags = buffer.get().toInt() and 0xFF
        
        if (flags and 0x01 != 0) { // Wheel Revolution Data Present
            if (buffer.remaining() >= 6) {
                val wheelRevolutions = buffer.getInt().toLong() and 0xFFFFFFFFL
                val wheelEventTime = buffer.getShort().toInt() and 0xFFFF
                
                if (lastWheelRevolutions != null && lastWheelEventTime != null) {
                    val revDelta = wheelRevolutions - lastWheelRevolutions!!
                    var timeDelta = wheelEventTime - lastWheelEventTime!!
                    if (timeDelta < 0) timeDelta += 65536
                    
                    if (timeDelta > 0) {
                        val wheelCircumference = 2.105f
                        val speed = (revDelta * wheelCircumference * 1024.0f / timeDelta) * 3.6f
                        currentSpeed = if (speed > 0) speed else 0f
                    }
                }
                
                lastWheelRevolutions = wheelRevolutions
                lastWheelEventTime = wheelEventTime
            }
        }
        
        if (flags and 0x02 != 0) { // Crank Revolution Data Present
            if (buffer.remaining() >= 4) {
                val crankRevolutions = buffer.getShort().toInt() and 0xFFFF
                val crankEventTime = buffer.getShort().toInt() and 0xFFFF
                
                if (lastCrankRevolutions != null && lastCrankEventTime != null) {
                    val revDelta = crankRevolutions - lastCrankRevolutions!!
                    var timeDelta = crankEventTime - lastCrankEventTime!!
                    if (timeDelta < 0) timeDelta += 65536
                    
                    if (timeDelta > 0) {
                        val cadence = (revDelta * 1024 * 60) / timeDelta
                        currentCadence = if (cadence > 0) cadence else 0
                    }
                }
                
                lastCrankRevolutions = crankRevolutions
                lastCrankEventTime = crankEventTime
            }
        }
    }
    
    private fun parseHeartRateMeasurement(data: ByteArray) {
        if (data.isEmpty()) return
        
        val flags = data[0].toInt() and 0xFF
        val hrFormat = flags and 0x01
        
        currentHeartRate = if (hrFormat == 0) {
            // Heart rate is 8-bit
            data[1].toInt() and 0xFF
        } else {
            // Heart rate is 16-bit
            ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        }
        
        Log.d(TAG, "Heart Rate: $currentHeartRate BPM")
    }
    
    private fun updateTrainerData() {
        _trainerData.value = TrainerData(
            power = currentPower,
            speed = currentSpeed,
            cadence = currentCadence,
            heartRate = currentHeartRate,
            timestamp = System.currentTimeMillis()
        )
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    companion object {
        private const val TAG = "KickrBluetoothService"
    }
}
