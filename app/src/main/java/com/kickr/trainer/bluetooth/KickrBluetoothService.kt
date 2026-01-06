/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

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
    
    // Low-pass filter for smoothing speed and power (like Python alpha = 0.99)
    private var filteredSpeed: Float = 0f
    private var filteredPower: Int = 0
    private val smoothingAlpha = 0.85f  // Smoothing factor (0-1, higher = more smoothing)
    
    private var currentPower = 0
    private var currentCadence = 0
    private var currentSpeed = 0f
    private var currentHeartRate = 0
    
    // For callback chaining FTMS control commands
    private var pendingPowerCommand: ByteArray? = null
    private var ftmsControlPointCharacteristic: BluetoothGattCharacteristic? = null
    
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
            Log.d(TAG, "Characteristic changed: UUID=${characteristic.uuid}, size=${value.size} bytes")
            when (characteristic.uuid) {
                GattAttributes.INDOOR_BIKE_DATA -> {
                    Log.d(TAG, "→ Received FTMS Indoor Bike Data notification")
                    parseIndoorBikeData(value)
                }
                GattAttributes.CYCLING_POWER_MEASUREMENT -> {
                    Log.d(TAG, "→ Received Cycling Power Measurement notification")
                    parseCyclingPowerMeasurement(value)
                }
                GattAttributes.HEART_RATE_MEASUREMENT -> {
                    Log.d(TAG, "→ Received Heart Rate Measurement notification")
                    parseHeartRateMeasurement(value)
                }
                GattAttributes.FITNESS_MACHINE_CONTROL_POINT -> {
                    Log.d(TAG, "→ Received FTMS Control Point response")
                    val hexString = value.joinToString(" ") { "0x%02X".format(it) }
                    Log.d(TAG, "   Response bytes: $hexString")
                    if (value.isNotEmpty()) {
                        val responseCode = value[0].toInt() and 0xFF
                        Log.d(TAG, "   Response code: 0x${responseCode.toString(16)} (${
                            when (responseCode) {
                                0x80 -> "Response Code"
                                0x01 -> "Success"
                                0x02 -> "Not Supported"
                                0x03 -> "Invalid Parameter"
                                0x04 -> "Operation Failed"
                                else -> "Unknown"
                            }
                        })")
                    }
                }
                else -> {
                    Log.d(TAG, "→ Unknown characteristic: ${characteristic.uuid}")
                }
            }
            updateTrainerData()
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG, "Characteristic write completed: UUID=${characteristic.uuid}, status=$status (${
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> "SUCCESS"
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "WRITE_NOT_PERMITTED"
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "INVALID_ATTRIBUTE_LENGTH"
                    else -> "ERROR_$status"
                }
            })")
            
            // If this was the Request Control command and we have a pending power command, send it now
            if (status == BluetoothGatt.GATT_SUCCESS && 
                characteristic.uuid == GattAttributes.FITNESS_MACHINE_CONTROL_POINT &&
                pendingPowerCommand != null) {
                Log.d(TAG, "→ Control granted, sending pending power command")
                characteristic.value = pendingPowerCommand
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                val success = gatt.writeCharacteristic(characteristic)
                Log.d(TAG, "  Write initiated: ${if (success) "✓ SUCCESS" else "✗ FAILED"}")
                pendingPowerCommand = null  // Clear pending command
            }
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
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "FTMS RESISTANCE COMMAND")
        Log.d(TAG, "Input: $resistancePercent%")
        
        // Convert resistance percentage to target power using exponential curve
        // This provides better feel across the entire range
        // Formula: power = 20W + e^(resistancePercent/25) * 8W
        // 0% = 28W, 25% = ~42W, 50% = ~73W, 75% = ~124W, 100% = ~227W
        val targetPower = (20 + Math.exp(resistancePercent / 25.0) * 8).toInt().toShort()
        
        Log.d(TAG, "Formula: 20 + e^($resistancePercent/25) * 8")
        Log.d(TAG, "Output: ${targetPower}W")
        
        // Use FTMS (Fitness Machine Service) exclusively
        gatt.getService(GattAttributes.FITNESS_MACHINE_SERVICE)?.let { service ->
            service.getCharacteristic(GattAttributes.FITNESS_MACHINE_CONTROL_POINT)?.let { characteristic ->
                // Prepare the power command to send after control is granted
                pendingPowerCommand = byteArrayOf(
                    0x05.toByte(), // Opcode: Set Target Power
                    (targetPower.toInt() and 0xFF).toByte(),
                    ((targetPower.toInt() shr 8) and 0xFF).toByte()
                )
                
                Log.d(TAG, "→ FTMS Requesting Control (power command pending)")
                Log.d(TAG, "  Target power: ${targetPower}W")
                
                // Request control of the trainer (required before setting power)
                val requestControlCommand = byteArrayOf(0x00.toByte()) // Opcode 0x00: Request Control
                characteristic.value = requestControlCommand
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                
                Log.d(TAG, "  Command bytes: ${requestControlCommand.joinToString(" ") { "0x%02X".format(it) }}")
                val controlSuccess = gatt.writeCharacteristic(characteristic)
                Log.d(TAG, "  Write initiated: ${if (controlSuccess) "✓ SUCCESS" else "✗ FAILED"}")
                Log.d(TAG, "========================================")
                
                if (!controlSuccess) {
                    Log.e(TAG, "Failed to initiate FTMS Request Control write")
                    pendingPowerCommand = null  // Clear pending command on failure
                }
                // The power command will be sent automatically in onCharacteristicWrite callback
                return
            } ?: Log.e(TAG, "FTMS Control Point characteristic not found")
        } ?: Log.e(TAG, "Fitness Machine Service not found")
        
        Log.w(TAG, "FTMS resistance control not available")
    }
    
    private fun setWheelCircumference(circumferenceMm: Float) {
        val gatt = bluetoothGatt
        val characteristic = ftmsControlPointCharacteristic
        
        if (gatt == null || characteristic == null) {
            Log.w(TAG, "Cannot set wheel circumference: GATT or characteristic not available")
            return
        }
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "FTMS SET WHEEL CIRCUMFERENCE")
        Log.d(TAG, "Setting wheel circumference to ${circumferenceMm}mm")
        
        // FTMS Set Wheel Circumference (opcode 0x13)
        // Parameter: UINT16 in 0.1mm resolution
        val circumference01mm = (circumferenceMm * 10).toInt()
        val command = byteArrayOf(
            0x13.toByte(), // Opcode: Set Wheel Circumference
            (circumference01mm and 0xFF).toByte(),
            ((circumference01mm shr 8) and 0xFF).toByte()
        )
        
        Log.d(TAG, "Command bytes: ${command.joinToString(" ") { "0x%02X".format(it) }}")
        
        characteristic.value = command
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val success = gatt.writeCharacteristic(characteristic)
        
        Log.d(TAG, "Write initiated: ${if (success) "✓ SUCCESS" else "✗ FAILED"}")
        Log.d(TAG, "========================================")
        
        if (!success) {
            Log.e(TAG, "Failed to set wheel circumference")
        }
    }
    
    private fun sendStartCommand() {
        val gatt = bluetoothGatt
        val characteristic = ftmsControlPointCharacteristic
        
        if (gatt == null || characteristic == null) {
            Log.w(TAG, "Cannot send start command: GATT or characteristic not available")
            return
        }
        
        Log.d(TAG, "========================================")
        Log.d(TAG, "FTMS START/RESUME COMMAND")
        
        // FTMS Start or Resume (opcode 0x07)
        val command = byteArrayOf(0x07.toByte())
        
        Log.d(TAG, "Command bytes: ${command.joinToString(" ") { "0x%02X".format(it) }}")
        
        characteristic.value = command
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val success = gatt.writeCharacteristic(characteristic)
        
        Log.d(TAG, "Write initiated: ${if (success) "✓ SUCCESS" else "✗ FAILED"}")
        Log.d(TAG, "========================================")
        
        if (!success) {
            Log.e(TAG, "Failed to send start command")
        }
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
        
        // DON'T enable Cycling Power - it conflicts with FTMS on Wahoo devices
        // The Kickr can only broadcast on one service at a time
        // We prefer FTMS because it provides direct speed readings
        /*
        gatt.getService(GattAttributes.CYCLING_POWER_SERVICE)?.let { service ->
            Log.d(TAG, "Found Cycling Power Service (skipping - using FTMS instead)")
        } ?: Log.w(TAG, "Cycling Power Service NOT found")
        */
        
        // Enable FTMS Indoor Bike Data notifications (primary source for speed, power, cadence)
        gatt.getService(GattAttributes.FITNESS_MACHINE_SERVICE)?.let { service ->
            Log.d(TAG, "Found Fitness Machine Service")
            
            // Enable Indoor Bike Data notifications
            service.getCharacteristic(GattAttributes.INDOOR_BIKE_DATA)?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val success = gatt.writeDescriptor(descriptor)
                    Log.d(TAG, "Enabled FTMS Indoor Bike Data notifications: ${if (success) "SUCCESS" else "FAILED"}")
                }
            } ?: Log.w(TAG, "FTMS Indoor Bike Data characteristic NOT found")
            
            // Enable Control Point notifications to receive responses from resistance commands
            service.getCharacteristic(GattAttributes.FITNESS_MACHINE_CONTROL_POINT)?.let { characteristic ->
                ftmsControlPointCharacteristic = characteristic
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                    descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE // Control point uses indications
                    val success = gatt.writeDescriptor(descriptor)
                    Log.d(TAG, "Enabled FTMS Control Point indications: ${if (success) "SUCCESS" else "FAILED"}")
                }
                
                // Set wheel circumference for proper speed calculation (2100mm = 700c road bike)
                setWheelCircumference(2100.0f)
                
                // Send Start/Resume command to ensure trainer is in active mode
                sendStartCommand()
            } ?: Log.w(TAG, "FTMS Control Point characteristic NOT found")
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
                
                Log.d(TAG, "Wheel: revolutions=$wheelRevolutions, eventTime=$wheelEventTime, lastRev=$lastWheelRevolutions, lastTime=$lastWheelEventTime")
                
                if (lastWheelRevolutions != null && lastWheelEventTime != null) {
                    val revDelta = (wheelRevolutions - lastWheelRevolutions!!).toFloat()
                    var timeDelta = wheelEventTime - lastWheelEventTime!!
                    if (timeDelta < 0) timeDelta += 65536
                    
                    Log.d(TAG, "Deltas: revDelta=$revDelta, timeDelta=$timeDelta")
                    
                    // Only calculate speed if time delta is reasonable (between 100ms and 5s)
                    // This filters out stale data and prevents speed spikes
                    val timeInSeconds = timeDelta / 1024.0f
                    if (timeInSeconds >= 0.1f && timeInSeconds <= 5.0f) {
                        if (revDelta > 0) {
                            // Kickr uses a very small virtual wheel circumference
                            // Based on empirical testing: use 0.28m to get realistic speeds
                            val distanceMeters = revDelta * 0.28f
                            val speedMps = distanceMeters / timeInSeconds
                            val speedKmh = speedMps * 3.6f
                            
                            Log.d(TAG, "Speed calc: timeInSec=$timeInSeconds, distanceM=$distanceMeters, speedMps=$speedMps, speedKmh=$speedKmh")
                            
                            if (speedKmh >= 0 && speedKmh < 100) {
                                currentSpeed = speedKmh
                                Log.d(TAG, "✓ Speed SET: $speedKmh km/h")
                            } else {
                                Log.w(TAG, "✗ Speed out of range: $speedKmh km/h")
                            }
                        } else {
                            // No wheel movement = 0 speed
                            currentSpeed = 0f
                            Log.d(TAG, "Speed: 0 km/h (no wheel movement, revDelta=$revDelta)")
                        }
                    } else {
                        Log.d(TAG, "Time delta out of range: $timeInSeconds seconds (ignored)")
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
            Log.d(TAG, "========================================")
            Log.d(TAG, "FTMS INDOOR BIKE DATA RECEIVED")
            Log.d(TAG, "Raw bytes: $hexString")
            Log.d(TAG, "Size: ${data.size} bytes")
            
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val flags = buffer.getShort().toInt() and 0xFFFF
            
            Log.d(TAG, "Flags: 0x${flags.toString(16).padStart(4, '0')} (binary: ${flags.toString(2).padStart(16, '0')})")
            Log.d(TAG, "Flag breakdown:")
            Log.d(TAG, "Flag breakdown:")
            Log.d(TAG, "  Bit 0 (More Data): ${if (flags and 0x01 != 0) "SET (speed NOT present)" else "CLEAR (speed present)"}")
            Log.d(TAG, "  Bit 1 (Avg Speed): ${if (flags and 0x02 != 0) "present" else "absent"}")
            Log.d(TAG, "  Bit 2 (Cadence): ${if (flags and 0x04 != 0) "present" else "absent"}")
            Log.d(TAG, "  Bit 3 (Avg Cadence): ${if (flags and 0x08 != 0) "present" else "absent"}")
            Log.d(TAG, "  Bit 4 (Total Distance): ${if (flags and 0x10 != 0) "present" else "absent"}")
            Log.d(TAG, "  Bit 5 (Resistance): ${if (flags and 0x20 != 0) "present" else "absent"}")
            Log.d(TAG, "  Bit 6 (Power): ${if (flags and 0x40 != 0) "present" else "absent"}")
            Log.d(TAG, "  Bit 7 (Avg Power): ${if (flags and 0x80 != 0) "present" else "absent"}")
            
            // According to FTMS spec:
            // Bit 0 = 0: Instantaneous Speed is present (UINT16, 0.01 km/h resolution)
            // Bit 0 = 1: More Data flag (speed not present in this message)
            
            // Instantaneous Speed - present when bit 0 is CLEAR (0)
            if (flags and 0x01 == 0) {
                if (buffer.remaining() >= 2) {
                    val speedRaw = buffer.getShort().toInt() and 0xFFFF
                    currentSpeed = speedRaw / 100.0f // Convert from 0.01 km/h to km/h
                    Log.d(TAG, "→ Speed: raw=$speedRaw (0x${speedRaw.toString(16)}), converted=$currentSpeed km/h")
                } else {
                    Log.w(TAG, "→ Speed should be present but not enough data")
                }
            } else {
                Log.d(TAG, "→ Speed: NOT PRESENT (More Data flag set)")
            }
            
            // Average Speed - if bit 1 is set
            if (flags and 0x02 != 0 && buffer.remaining() >= 2) {
                val avgSpeedRaw = buffer.getShort().toInt() and 0xFFFF
                val avgSpeed = avgSpeedRaw / 100.0f
                Log.d(TAG, "→ Avg Speed: raw=$avgSpeedRaw (0x${avgSpeedRaw.toString(16)}), converted=$avgSpeed km/h")
            }
            
            // Instantaneous Cadence - if bit 2 is set
            if (flags and 0x04 != 0 && buffer.remaining() >= 2) {
                val cadenceRaw = buffer.getShort().toInt() and 0xFFFF
                currentCadence = cadenceRaw / 2 // 0.5 RPM resolution
                Log.d(TAG, "→ Cadence: raw=$cadenceRaw (0x${cadenceRaw.toString(16)}), converted=$currentCadence RPM")
            }
            
            // Skip Average Cadence if present (bit 3)
            if (flags and 0x08 != 0 && buffer.remaining() >= 2) {
                val avgCadenceRaw = buffer.getShort().toInt() and 0xFFFF
                Log.d(TAG, "→ Avg Cadence: raw=$avgCadenceRaw (skipped)")
            }
            
            // Skip Total Distance if present (bit 4)
            if (flags and 0x10 != 0 && buffer.remaining() >= 3) {
                val distanceByte1 = buffer.get().toInt() and 0xFF
                val distanceBytes2_3 = buffer.getShort().toInt() and 0xFFFF
                val totalDistance = distanceByte1 or (distanceBytes2_3 shl 8)
                Log.d(TAG, "→ Total Distance: raw=$totalDistance meters (skipped)")
            }
            
            // Skip Resistance Level if present (bit 5)
            if (flags and 0x20 != 0 && buffer.remaining() >= 2) {
                val resistanceRaw = buffer.getShort().toInt() and 0xFFFF
                Log.d(TAG, "→ Resistance Level: raw=$resistanceRaw (skipped)")
            }
            
            // Instantaneous Power - if bit 6 is set
            if (flags and 0x40 != 0 && buffer.remaining() >= 2) {
                val powerRaw = buffer.getShort().toInt() and 0xFFFF
                currentPower = powerRaw // Already in watts
                Log.d(TAG, "→ Power: raw=$powerRaw (0x${powerRaw.toString(16)}), watts=$currentPower W")
            }
            
            Log.d(TAG, "========================================")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Indoor Bike Data", e)
            e.printStackTrace()
        }
    }
    
    private fun updateTrainerData() {
        // Use FTMS speed directly (now working after sending Start command)
        val finalSpeed = currentSpeed
        
        // Apply low-pass filter only for power smoothing
        if (currentPower > 1 || filteredPower < 1) {
            filteredPower = (smoothingAlpha * filteredPower + (1 - smoothingAlpha) * currentPower).toInt()
        }
        
        _trainerData.value = TrainerData(
            power = filteredPower,
            speed = finalSpeed,
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
