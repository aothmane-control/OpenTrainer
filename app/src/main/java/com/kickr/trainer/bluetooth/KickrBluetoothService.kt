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
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    // Looked up on each use: the adapter returns null while Bluetooth is off,
    // so a value cached at construction would stay null after enabling it
    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner
    
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
    
    private var ftmsControlPointCharacteristic: BluetoothGattCharacteristic? = null
    private var lastResistanceCommandTime = 0L
    private val RESISTANCE_COMMAND_DELAY_MS = 200L // Minimum delay between resistance commands
    private val commandHandler = Handler(Looper.getMainLooper())
    private var deferredCommand: Runnable? = null

    // Android allows only one outstanding GATT operation at a time. Writes are queued
    // and the next one is started from the matching completion callback.
    private sealed class GattOperation {
        class WriteCharacteristic(
            val characteristic: BluetoothGattCharacteristic,
            val value: ByteArray
        ) : GattOperation()

        class WriteDescriptor(
            val descriptor: BluetoothGattDescriptor,
            val value: ByteArray
        ) : GattOperation()
    }

    private val gattOperationQueue = ArrayDeque<GattOperation>()
    private var gattOperationInProgress = false
    
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
                    Log.d(TAG, "Disconnected from GATT server (status=$status)")
                    // Release the client interface: Android only has a limited number of them,
                    // and failed connections (e.g. status 133) also end up here
                    gatt.close()
                    // Ignore late callbacks from a connection that has already been replaced
                    if (bluetoothGatt === gatt) {
                        bluetoothGatt = null
                        resetConnectionState()
                        _connectionState.value = ConnectionState.Disconnected
                    }
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
                GattAttributes.HEART_RATE_MEASUREMENT -> {
                    Log.d(TAG, "→ Received Heart Rate Measurement notification")
                    parseHeartRateMeasurement(value)
                }
                GattAttributes.FITNESS_MACHINE_CONTROL_POINT -> {
                    Log.d(TAG, "→ Received FTMS Control Point response")
                    val hexString = value.joinToString(" ") { "0x%02X".format(it) }
                    Log.d(TAG, "   Response bytes: $hexString")
                    // Response format: 0x80 (Response Code), request opcode, result code
                    if (value.size >= 3 && (value[0].toInt() and 0xFF) == 0x80) {
                        val requestOpcode = value[1].toInt() and 0xFF
                        val resultCode = value[2].toInt() and 0xFF
                        val result = when (resultCode) {
                            0x01 -> "Success"
                            0x02 -> "Op Code Not Supported"
                            0x03 -> "Invalid Parameter"
                            0x04 -> "Operation Failed"
                            0x05 -> "Control Not Permitted"
                            else -> "Unknown"
                        }
                        val message = "   Response to opcode 0x%02X: 0x%02X (%s)".format(requestOpcode, resultCode, result)
                        if (resultCode == 0x01) Log.d(TAG, message) else Log.w(TAG, message)
                    }
                }
                else -> {
                    Log.d(TAG, "→ Unknown characteristic: ${characteristic.uuid}")
                }
            }
            updateTrainerData()
        }

        // Android 12 (API 32) and below only call this deprecated overload
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val value = characteristic.value ?: return
                onCharacteristicChanged(gatt, characteristic, value)
            }
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

            completeGattOperation()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d(TAG, "Descriptor write completed: characteristic=${descriptor.characteristic.uuid}, status=$status")
            completeGattOperation()
        }
    }

    private fun enqueueGattOperation(operation: GattOperation) {
        synchronized(gattOperationQueue) {
            gattOperationQueue.addLast(operation)
        }
        executeNextGattOperation()
    }

    private fun executeNextGattOperation() {
        val gatt = bluetoothGatt ?: return
        val operation = synchronized(gattOperationQueue) {
            if (gattOperationInProgress) return
            val next = gattOperationQueue.removeFirstOrNull() ?: return
            gattOperationInProgress = true
            next
        }

        val started = when (operation) {
            is GattOperation.WriteCharacteristic -> {
                operation.characteristic.value = operation.value
                operation.characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                gatt.writeCharacteristic(operation.characteristic)
            }
            is GattOperation.WriteDescriptor -> {
                operation.descriptor.value = operation.value
                gatt.writeDescriptor(operation.descriptor)
            }
        }

        if (!started) {
            Log.e(TAG, "Failed to start GATT operation, skipping it")
            completeGattOperation()
        }
    }

    private fun completeGattOperation() {
        synchronized(gattOperationQueue) {
            gattOperationInProgress = false
        }
        executeNextGattOperation()
    }

    private fun clearGattOperations() {
        synchronized(gattOperationQueue) {
            gattOperationQueue.clear()
            gattOperationInProgress = false
        }
    }

    // FTMS requires Request Control (opcode 0x00) before any other control point command
    private fun sendControlPointCommand(command: ByteArray): Boolean {
        val characteristic = ftmsControlPointCharacteristic
            ?: bluetoothGatt?.getService(GattAttributes.FITNESS_MACHINE_SERVICE)
                ?.getCharacteristic(GattAttributes.FITNESS_MACHINE_CONTROL_POINT)

        if (characteristic == null) {
            Log.e(TAG, "FTMS Control Point characteristic not found")
            return false
        }

        Log.d(TAG, "  Command bytes: ${command.joinToString(" ") { "0x%02X".format(it) }}")
        enqueueGattOperation(GattOperation.WriteCharacteristic(characteristic, byteArrayOf(0x00)))
        enqueueGattOperation(GattOperation.WriteCharacteristic(characteristic, command))
        return true
    }
    
    fun startScan() {
        if (_isScanning.value) return
        
        _discoveredDevices.value = emptyList()
        
        // Scan with no filters to find all BLE devices
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        val scanner = bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "Cannot start scan: Bluetooth LE scanner not available (Bluetooth off?)")
            return
        }

        scanner.startScan(null, scanSettings, scanCallback)
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
        // Release any previous connection before opening a new one
        bluetoothGatt?.close()
        resetConnectionState()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        deferredCommand?.let { commandHandler.removeCallbacks(it) }
        deferredCommand = null
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        resetConnectionState()
        _connectionState.value = ConnectionState.Disconnected
    }

    // Prevent rapid successive commands - they can queue up and cause issues.
    // A command that comes too soon is deferred rather than dropped, and replaces any
    // command still waiting, so the latest target always reaches the trainer.
    private fun sendThrottled(label: String, send: () -> Unit) {
        deferredCommand?.let { commandHandler.removeCallbacks(it) }
        deferredCommand = null

        val waitMs = RESISTANCE_COMMAND_DELAY_MS - (System.currentTimeMillis() - lastResistanceCommandTime)
        if (waitMs <= 0) {
            lastResistanceCommandTime = System.currentTimeMillis()
            send()
            return
        }

        Log.d(TAG, "Deferring $label command by ${waitMs}ms - too soon after last command")
        val command = Runnable {
            deferredCommand = null
            lastResistanceCommandTime = System.currentTimeMillis()
            send()
        }
        deferredCommand = command
        commandHandler.postDelayed(command, waitMs)
    }

    // Clear everything tied to a single connection, so stale values (e.g. the last speed,
    // which workouts integrate into distance) don't outlive it and a reconnect starts clean
    private fun resetConnectionState() {
        ftmsControlPointCharacteristic = null
        clearGattOperations()
        currentPower = 0
        currentCadence = 0
        currentSpeed = 0f
        currentHeartRate = 0
        updateTrainerData()
    }
    
    fun setPower(powerWatts: Int) {
        sendThrottled("power") { sendPowerCommand(powerWatts) }
    }

    private fun sendPowerCommand(powerWatts: Int) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            Log.w(TAG, "Cannot set power: GATT not connected")
            return
        }

        Log.d(TAG, "========================================")
        Log.d(TAG, "FTMS POWER COMMAND")
        Log.d(TAG, "Input: ${powerWatts}W")
        
        // Convert power to FTMS format (SINT16, 1 watt resolution)
        val powerValue = powerWatts.toShort()
        
        Log.d(TAG, "Power Value: ${powerValue} watts")
        
        // Use FTMS (Fitness Machine Service) exclusively
        sendControlPointCommand(byteArrayOf(
            0x05.toByte(), // Opcode: Set Target Power
            (powerValue.toInt() and 0xFF).toByte(),
            ((powerValue.toInt() shr 8) and 0xFF).toByte()
        ))
        Log.d(TAG, "========================================")
    }
    
    fun setResistance(resistancePercent: Int) {
        sendThrottled("resistance") { sendResistanceCommand(resistancePercent) }
    }

    private fun sendResistanceCommand(resistancePercent: Int) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            Log.w(TAG, "Cannot set resistance: GATT not connected")
            return
        }

        Log.d(TAG, "========================================")
        Log.d(TAG, "FTMS RESISTANCE COMMAND")
        Log.d(TAG, "Input: $resistancePercent%")
        
        // Convert resistance percentage to resistance level (0.1% resolution)
        // FTMS uses SINT16 with 0.1% resolution
        // 0% = 0, 50% = 500, 100% = 1000
        val resistanceLevel = (resistancePercent * 10).toShort()
        
        Log.d(TAG, "Resistance Level: ${resistanceLevel} (0.1% units)")
        
        // Use FTMS (Fitness Machine Service) exclusively
        val queued = sendControlPointCommand(byteArrayOf(
            0x04.toByte(), // Opcode: Set Target Resistance Level
            (resistanceLevel.toInt() and 0xFF).toByte(),
            ((resistanceLevel.toInt() shr 8) and 0xFF).toByte()
        ))
        Log.d(TAG, "========================================")

        if (!queued) {
            Log.w(TAG, "FTMS resistance control not available")
        }
    }

    private fun setWheelCircumference(circumferenceMm: Float) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "FTMS SET WHEEL CIRCUMFERENCE")
        Log.d(TAG, "Setting wheel circumference to ${circumferenceMm}mm")

        // FTMS Set Wheel Circumference (opcode 0x12)
        // Parameter: UINT16 in 0.1mm resolution
        val circumference01mm = (circumferenceMm * 10).toInt()
        sendControlPointCommand(byteArrayOf(
            0x12.toByte(), // Opcode: Set Wheel Circumference
            (circumference01mm and 0xFF).toByte(),
            ((circumference01mm shr 8) and 0xFF).toByte()
        ))
        Log.d(TAG, "========================================")
    }
    
    fun setSimulationParameters(
        windSpeedMps: Double = 0.0,
        gradePercent: Double = 0.0,
        crr: Double = 0.004,  // Coefficient of rolling resistance (default: 0.004)
        cw: Double = 0.51     // Wind resistance coefficient kg/m (default: 0.51)
    ) {
        sendThrottled("simulation") { sendSimulationCommand(windSpeedMps, gradePercent, crr, cw) }
    }

    private fun sendSimulationCommand(
        windSpeedMps: Double,
        gradePercent: Double,
        crr: Double,
        cw: Double
    ) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            Log.w(TAG, "Cannot set simulation parameters: GATT not connected")
            return
        }

        Log.d(TAG, "========================================")
        Log.d(TAG, "FTMS SIMULATION PARAMETERS")
        Log.d(TAG, "Grade: %.2f%%, Wind: %.2f m/s, Crr: %.4f, Cw: %.2f".format(gradePercent, windSpeedMps, crr, cw))
        
        // Convert parameters to FTMS format
        // Wind Speed: SINT16, 0.001 m/s resolution
        val windSpeed = (windSpeedMps * 1000).toInt().coerceIn(-32768, 32767).toShort()
        
        // Grade: SINT16, 0.01% resolution, range: -100.00% to +100.00%
        val grade = (gradePercent * 100).toInt().coerceIn(-10000, 10000).toShort()
        
        // Crr: UINT8, 0.0001 resolution
        val crrByte = (crr * 10000).toInt().coerceIn(0, 255).toByte()
        
        // Cw: UINT8, 0.01 kg/m resolution
        val cwByte = (cw * 100).toInt().coerceIn(0, 255).toByte()
        
        Log.d(TAG, "  Wind Speed: $windSpeed (0.001 m/s units)")
        Log.d(TAG, "  Grade: $grade (0.01%% units)")
        Log.d(TAG, "  Crr: $crrByte")
        Log.d(TAG, "  Cw: $cwByte")
        
        // Use FTMS (Fitness Machine Service)
        val queued = sendControlPointCommand(byteArrayOf(
            0x11.toByte(), // Opcode: Set Indoor Bike Simulation Parameters
            (windSpeed.toInt() and 0xFF).toByte(),
            ((windSpeed.toInt() shr 8) and 0xFF).toByte(),
            (grade.toInt() and 0xFF).toByte(),
            ((grade.toInt() shr 8) and 0xFF).toByte(),
            crrByte,
            cwByte
        ))
        Log.d(TAG, "========================================")

        if (!queued) {
            Log.w(TAG, "FTMS simulation control not available")
        }
    }

    private fun sendStartCommand() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "FTMS START/RESUME COMMAND")

        // FTMS Start or Resume (opcode 0x07)
        sendControlPointCommand(byteArrayOf(0x07.toByte()))
        Log.d(TAG, "========================================")
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
                    enqueueGattOperation(GattOperation.WriteDescriptor(
                        descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    ))
                    Log.d(TAG, "Queued FTMS Indoor Bike Data notifications")
                }
            } ?: Log.w(TAG, "FTMS Indoor Bike Data characteristic NOT found")
            
            // Enable Control Point notifications to receive responses from resistance commands
            service.getCharacteristic(GattAttributes.FITNESS_MACHINE_CONTROL_POINT)?.let { characteristic ->
                ftmsControlPointCharacteristic = characteristic
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                    enqueueGattOperation(GattOperation.WriteDescriptor(
                        descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE // Control point uses indications
                    ))
                    Log.d(TAG, "Queued FTMS Control Point indications")
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
                    enqueueGattOperation(GattOperation.WriteDescriptor(
                        descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    ))
                    Log.d(TAG, "Queued Heart Rate notifications")
                }
            }
        } ?: Log.d(TAG, "Heart Rate Service not available (optional)")
    }
    
    private fun parseHeartRateMeasurement(data: ByteArray) {
        if (data.isEmpty()) return
        
        val flags = data[0].toInt() and 0xFF
        val hrFormat = flags and 0x01
        
        // 8-bit values need 2 bytes, 16-bit values 3; a short packet would throw on the binder thread
        val requiredSize = if (hrFormat == 0) 2 else 3
        if (data.size < requiredSize) {
            Log.w(TAG, "Heart Rate Measurement too short: ${data.size} bytes")
            return
        }
        
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
