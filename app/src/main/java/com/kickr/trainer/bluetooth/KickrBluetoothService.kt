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
                Log.d(TAG, "Services discovered successfully")
                
                // Log all available services
                gatt.services.forEach { service ->
                    Log.d(TAG, "Service: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        Log.d(TAG, "  Characteristic: ${char.uuid}")
                    }
                }
                
                // Check for Fitness Machine Service
                if (gatt.getService(GattAttributes.FITNESS_MACHINE_SERVICE) != null) {
                    Log.d(TAG, "Fitness Machine Service is available")
                } else {
                    Log.w(TAG, "Fitness Machine Service NOT found - resistance control may not work")
                }
                
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
                GattAttributes.INDOOR_BIKE_DATA -> {
                    parseIndoorBikeData(value)
                }
                GattAttributes.CYCLING_POWER_MEASUREMENT -> {
                    parseCyclingPowerMeasurement(value)
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
        val gatt = bluetoothGatt
        if (gatt == null) {
            Log.w(TAG, "Cannot set resistance: GATT not connected")
            return
        }
        
        Log.d(TAG, "Attempting to set resistance to $resistancePercent%")
        
        // Try Wahoo Trainer Service first (Kickr-specific)
        gatt.getService(GattAttributes.WAHOO_TRAINER_SERVICE)?.let { service ->
            service.getCharacteristic(GattAttributes.WAHOO_TRAINER_CONTROL)?.let { characteristic ->
                // Wahoo ERG mode command: Set target power based on resistance percentage
                // Assuming max power ~400W, so resistance % maps to power
                val targetPower = (resistancePercent * 4).toShort() // 100% = 400W
                val command = byteArrayOf(
                    0x42.toByte(), // 'B' - Set ERG mode power
                    (targetPower.toInt() and 0xFF).toByte(),
                    ((targetPower.toInt() shr 8) and 0xFF).toByte()
                )
                
                Log.d(TAG, "Trying Wahoo service - Target power: ${targetPower}W, command: ${command.joinToString { "0x%02X".format(it) }}")
                characteristic.value = command
                val success = gatt.writeCharacteristic(characteristic)
                if (success) {
                    Log.d(TAG, "Successfully sent Wahoo resistance command")
                    return
                }
            }
        }
        
        // Try Fitness Machine Service (FTMS standard)
        gatt.getService(GattAttributes.FITNESS_MACHINE_SERVICE)?.let { service ->
            service.getCharacteristic(GattAttributes.FITNESS_MACHINE_CONTROL_POINT)?.let { characteristic ->
                // Try Set Target Power (opcode 0x05) - more commonly supported than resistance level
                val targetPower = (resistancePercent * 4).toShort() // 100% = 400W
                val powerCommand = byteArrayOf(
                    0x05.toByte(), // Opcode: Set Target Power
                    (targetPower.toInt() and 0xFF).toByte(),
                    ((targetPower.toInt() shr 8) and 0xFF).toByte()
                )
                
                Log.d(TAG, "Trying FTMS Target Power - ${targetPower}W, command: ${powerCommand.joinToString { "0x%02X".format(it) }}")
                characteristic.value = powerCommand
                if (gatt.writeCharacteristic(characteristic)) {
                    Log.d(TAG, "Successfully sent FTMS target power command")
                    return
                }
                
                // Fallback: Try Set Target Resistance Level (opcode 0x04)
                val resistanceValue = (resistancePercent * 10).toShort()
                val resistanceCommand = byteArrayOf(
                    0x04.toByte(), // Opcode: Set Target Resistance Level
                    (resistanceValue.toInt() and 0xFF).toByte(),
                    ((resistanceValue.toInt() shr 8) and 0xFF).toByte()
                )
                
                Log.d(TAG, "Trying FTMS Resistance Level - ${resistancePercent}%, command: ${resistanceCommand.joinToString { "0x%02X".format(it) }}")
                characteristic.value = resistanceCommand
                if (gatt.writeCharacteristic(characteristic)) {
                    Log.d(TAG, "Successfully sent FTMS resistance level command")
                    return
                }
            }
        }
        
        // Try Cycling Power Control Point as last resort
        gatt.getService(GattAttributes.CYCLING_POWER_SERVICE)?.let { service ->
            service.getCharacteristic(GattAttributes.CYCLING_POWER_CONTROL_POINT)?.let { characteristic ->
                // Request control
                val command = byteArrayOf(0x00.toByte()) // Request Control
                Log.d(TAG, "Trying Cycling Power Control Point")
                characteristic.value = command
                gatt.writeCharacteristic(characteristic)
            }
        }
        
        Log.w(TAG, "No resistance control method available or all methods failed")
    }
    
    private fun enableNotifications(gatt: BluetoothGatt) {
        Log.d(TAG, "=== Discovering services and enabling notifications ===")
        
        // Log all available services
        gatt.services.forEach { service ->
            Log.d(TAG, "Service: ${service.uuid}")
            service.characteristics.forEach { char ->
                Log.d(TAG, "  Characteristic: ${char.uuid}")
            }
        }
        
        // Enable Cycling Power notifications
        gatt.getService(GattAttributes.CYCLING_POWER_SERVICE)?.let { service ->
            Log.d(TAG, "Found Cycling Power Service")
            service.getCharacteristic(GattAttributes.CYCLING_POWER_MEASUREMENT)?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    Log.d(TAG, "Enabled Cycling Power Measurement notifications")
                }
            }
        } ?: Log.w(TAG, "Cycling Power Service NOT found")
        
        // Enable FTMS Indoor Bike Data notifications (primary source for speed)
        gatt.getService(GattAttributes.FITNESS_MACHINE_SERVICE)?.let { service ->
            Log.d(TAG, "Found Fitness Machine Service")
            service.getCharacteristic(GattAttributes.INDOOR_BIKE_DATA)?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    Log.d(TAG, "Enabled FTMS Indoor Bike Data notifications")
                }
            } ?: Log.w(TAG, "FTMS Indoor Bike Data characteristic NOT found")
        } ?: Log.w(TAG, "Fitness Machine Service NOT found")
        
        // Enable Heart Rate notifications if available
        gatt.getService(GattAttributes.HEART_RATE_SERVICE)?.let { service ->
            Log.d(TAG, "Found Heart Rate Service")
            service.getCharacteristic(GattAttributes.HEART_RATE_MEASUREMENT)?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    Log.d(TAG, "Enabled Heart Rate notifications")
                }
            }
        } ?: Log.d(TAG, "Heart Rate Service not available (optional)")
    }
    
    private fun parseCyclingPowerMeasurement(data: ByteArray) {
        if (data.size < 4) return
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val flags = buffer.getShort().toInt() and 0xFFFF
        
        // Instantaneous Power is always present (2 bytes)
        val power = buffer.getShort().toInt() and 0xFFFF
        currentPower = power
        
        Log.d(TAG, "Cycling Power: power=$power W, flags=0x${flags.toString(16)}, dataSize=${data.size}, bufferRemaining=${buffer.remaining()}, hasWheel=${flags and 0x10 != 0}, hasCrank=${flags and 0x20 != 0}")
        
        // Parse additional fields based on flags
        if (flags and 0x01 != 0) { // Pedal Power Balance Present
            buffer.get()
        }
        
        if (flags and 0x04 != 0) { // Accumulated Torque Present
            buffer.getShort()
        }
        
        if (flags and 0x10 != 0) { // Wheel Revolution Data Present
            Log.d(TAG, "Wheel data flag set, buffer remaining: ${buffer.remaining()}")
            if (buffer.remaining() >= 6) {
                val wheelRevolutions = buffer.getInt().toLong() and 0xFFFFFFFFL
                val wheelEventTime = buffer.getShort().toInt() and 0xFFFF
                
                if (lastWheelRevolutions != null && lastWheelEventTime != null) {
                    val revDelta = (wheelRevolutions - lastWheelRevolutions!!).toFloat()
                    var timeDelta = wheelEventTime - lastWheelEventTime!!
                    if (timeDelta < 0) timeDelta += 65536
                    
                    if (timeDelta > 0) {
                        if (revDelta > 0) {
                            val timeInSeconds = timeDelta / 1024.0f
                            val distanceMeters = revDelta * 2.105f
                            val speedMps = distanceMeters / timeInSeconds
                            val speedKmh = speedMps * 3.6f
                            
                            if (speedKmh >= 0 && speedKmh < 80) {
                                currentSpeed = speedKmh
                                Log.d(TAG, "Speed: $speedKmh km/h (revDelta=$revDelta, timeDelta=$timeDelta)")
                            }
                        } else {
                            // No wheel movement = 0 speed
                            currentSpeed = 0f
                            Log.d(TAG, "Speed: 0 km/h (no wheel movement)")
                        }
                    }
                } else {
                    Log.d(TAG, "First wheel data received: rev=$wheelRevolutions, time=$wheelEventTime")
                }
                
                lastWheelRevolutions = wheelRevolutions
                lastWheelEventTime = wheelEventTime
            } else {
                Log.w(TAG, "Wheel data flag set but not enough buffer data: ${buffer.remaining()} bytes")
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
                        Log.d(TAG, "Cadence: $currentCadence RPM (revDelta=$revDelta, timeDelta=$timeDelta)")
                    }
                } else {
                    Log.d(TAG, "First crank data received: rev=$crankRevolutions, time=$crankEventTime")
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
        
        Log.d(TAG, "CSC Measurement - flags: 0x${flags.toString(16)}")
        
        if (flags and 0x01 != 0) { // Wheel Revolution Data Present
            if (buffer.remaining() >= 6) {
                val wheelRevolutions = buffer.getInt().toLong() and 0xFFFFFFFFL
                val wheelEventTime = buffer.getShort().toInt() and 0xFFFF
                
                Log.d(TAG, "CSC Wheel: revolutions=$wheelRevolutions, eventTime=$wheelEventTime")
                
                if (lastWheelRevolutions != null && lastWheelEventTime != null) {
                    val revDelta = (wheelRevolutions - lastWheelRevolutions!!).toFloat()
                    var timeDelta = wheelEventTime - lastWheelEventTime!!
                    if (timeDelta < 0) timeDelta += 65536
                    
                    if (timeDelta > 0 && revDelta > 0) {
                        // Wheel event time is in 1/1024 seconds
                        val timeInSeconds = timeDelta / 1024.0f
                        val wheelCircumference = 2.105f // meters for 700c wheel
                        val distanceMeters = revDelta * wheelCircumference
                        val speedMps = distanceMeters / timeInSeconds
                        val speedKmh = speedMps * 3.6f
                        
                        // Only update if speed is reasonable (0-80 km/h)
                        if (speedKmh > 0 && speedKmh < 80) {
                            currentSpeed = speedKmh
                            Log.d(TAG, "CSC Speed: revDelta=$revDelta, timeDelta=$timeDelta, timeSeconds=$timeInSeconds, speed=$currentSpeed km/h")
                        } else {
                            Log.w(TAG, "CSC Speed out of range: $speedKmh km/h - ignoring")
                        }
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
    
    private fun parseIndoorBikeData(data: ByteArray) {
        if (data.size < 3) return
        
        try {
            // Log raw bytes for debugging
            val hexString = data.joinToString(" ") { "0x%02X".format(it) }
            Log.d(TAG, "Indoor Bike Data RAW: $hexString")
            
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val flags = buffer.getShort().toInt() and 0xFFFF
            
            Log.d(TAG, "Indoor Bike Data - flags: 0x${flags.toString(16).padStart(4, '0')}, size: ${data.size} bytes")
            
            // According to FTMS spec:
            // Bit 0 = 0: Instantaneous Speed is present (UINT16, 0.01 km/h resolution)
            // Bit 0 = 1: More Data flag (speed not present in this message)
            
            // Instantaneous Speed - present when bit 0 is CLEAR (0)
            if (flags and 0x01 == 0) {
                if (buffer.remaining() >= 2) {
                    val speedRaw = buffer.getShort().toInt() and 0xFFFF
                    currentSpeed = speedRaw / 100.0f // Convert from 0.01 km/h to km/h
                    Log.d(TAG, "FTMS Speed: raw=$speedRaw, speed=$currentSpeed km/h")
                } else {
                    Log.w(TAG, "Speed should be present but not enough data")
                }
            } else {
                Log.d(TAG, "Speed not present in this message (More Data flag set)")
            }
            
            // Average Speed - if bit 1 is set
            if (flags and 0x02 != 0 && buffer.remaining() >= 2) {
                val avgSpeedRaw = buffer.getShort().toInt() and 0xFFFF
                val avgSpeed = avgSpeedRaw / 100.0f
                Log.d(TAG, "Average Speed: raw=$avgSpeedRaw, converted=$avgSpeed km/h")
            }
            
            // Instantaneous Cadence - if bit 2 is set
            if (flags and 0x04 != 0 && buffer.remaining() >= 2) {
                val cadenceRaw = buffer.getShort().toInt() and 0xFFFF
                currentCadence = cadenceRaw / 2 // 0.5 RPM resolution
                Log.d(TAG, "Cadence: raw=$cadenceRaw, converted=$currentCadence RPM")
            }
            
            // Skip Average Cadence if present (bit 3)
            if (flags and 0x08 != 0 && buffer.remaining() >= 2) {
                buffer.getShort() // Skip
            }
            
            // Skip Total Distance if present (bit 4)
            if (flags and 0x10 != 0 && buffer.remaining() >= 3) {
                buffer.get() // Skip 3 bytes
                buffer.getShort()
            }
            
            // Skip Resistance Level if present (bit 5)
            if (flags and 0x20 != 0 && buffer.remaining() >= 2) {
                buffer.getShort() // Skip
            }
            
            // Instantaneous Power - if bit 6 is set
            if (flags and 0x40 != 0 && buffer.remaining() >= 2) {
                val powerRaw = buffer.getShort().toInt() and 0xFFFF
                currentPower = powerRaw // Already in watts
                Log.d(TAG, "Power: raw=$powerRaw, watts=$currentPower W")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Indoor Bike Data", e)
            e.printStackTrace()
        }
    }
    
    private fun updateTrainerData() {
        // Calculate speed from power (smart trainers often don't provide accurate speed data)
        // Using physics-based approximation: speed ≈ ∛(power) * constant
        // This gives: 10W≈11km/h, 50W≈18km/h, 100W≈23km/h, 200W≈29km/h, 300W≈33km/h
        val calculatedSpeed = if (currentPower > 0) {
            Math.pow(currentPower.toDouble(), 1.0/3.0).toFloat() * 5.0f
        } else {
            0f
        }
        
        _trainerData.value = TrainerData(
            power = currentPower,
            speed = calculatedSpeed,
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
