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
import com.kickr.trainer.model.RiderBikeSettings
import com.kickr.trainer.model.TrainerData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

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
    private var powerReported = false
    private var currentCadence = 0
    private var currentSpeed = 0f
    private var currentHeartRate = 0
    
    private var ftmsControlPointCharacteristic: BluetoothGattCharacteristic? = null
    // Written from the GATT callback thread, read when sending resistance commands
    @Volatile private var resistanceRange = ResistanceRange.DEFAULT
    val minResistanceLevel get() = resistanceRange.minLevel
    val maxResistanceLevel get() = resistanceRange.maxLevel
    // The connected trainer with its resistance range, once the range is known (null until then),
    // so the app can remember it and set up workouts in its levels without a connection
    private val _connectedTrainer = MutableStateFlow<Trainer?>(null)
    val connectedTrainer: StateFlow<Trainer?> = _connectedTrainer.asStateFlow()
    // What the trainer says it supports, read once per connection (null until read)
    @Volatile var features: FitnessMachineFeatures? = null
        private set
    // The trainer's replies to control point commands and its Fitness Machine Status messages
    private val _controlPointResponses = MutableSharedFlow<ControlPointResponse>(extraBufferCapacity = 16)
    val controlPointResponses: SharedFlow<ControlPointResponse> = _controlPointResponses.asSharedFlow()
    private val _machineStatus = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    val machineStatus: SharedFlow<ByteArray> = _machineStatus.asSharedFlow()
    // The rider and bike the trainer simulates with, sent on every connection
    @Volatile var riderBikeSettings = RiderBikeSettings.DEFAULT
        private set
    // The weight the trainer simulates with (User Data Service), if it lets apps set it
    private val weightCharacteristic: BluetoothGattCharacteristic?
        get() = bluetoothGatt?.getService(GattAttributes.USER_DATA_SERVICE)
            ?.getCharacteristic(GattAttributes.WEIGHT)
    val hasWeightCharacteristic: Boolean get() = weightCharacteristic != null
    // The weight last written (raw), so the read-back shows whether the trainer kept it
    @Volatile private var sentWeightRaw = -1
    private var lastResistanceCommandTime = 0L
    private val RESISTANCE_COMMAND_DELAY_MS = 200L // Minimum delay between resistance commands
    private val commandHandler = Handler(Looper.getMainLooper())
    private var deferredCommand: Runnable? = null
    // The last simulation command logged, so an unchanged one isn't logged every second
    private var lastSimulationCommand: ByteArray? = null
    // Likewise the last status message, which a trainer may send for every simulation command
    private var lastMachineStatus: ByteArray? = null

    // Android allows only one outstanding GATT operation at a time. Operations are queued
    // and the next one is started from the matching completion callback.
    private sealed class GattOperation {
        class ReadCharacteristic(val characteristic: BluetoothGattCharacteristic) : GattOperation()

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
    
    data class ControlPointResponse(val requestOpcode: Int, val resultCode: Int) {
        val resultName get() = when (resultCode) {
            0x01 -> "Success"
            0x02 -> "Op Code Not Supported"
            0x03 -> "Invalid Parameter"
            0x04 -> "Operation Failed"
            0x05 -> "Control Not Permitted"
            else -> "Unknown"
        }
    }

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
                // Devices advertise several times a second; each is logged once
                if (existingIndex >= 0) {
                    currentList[existingIndex] = kickrDevice
                } else {
                    currentList.add(kickrDevice)
                    Log.d(TAG, "Found device: $name (${device.address}) RSSI: $rssi Services: ${result.scanRecord?.serviceUuids}")
                }
                
                _discoveredDevices.value = currentList
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
            verbose { "Characteristic changed: UUID=${characteristic.uuid}, bytes: ${value.toHex()}" }
            when (characteristic.uuid) {
                GattAttributes.INDOOR_BIKE_DATA -> parseIndoorBikeData(value)
                GattAttributes.HEART_RATE_MEASUREMENT -> parseHeartRateMeasurement(value)
                GattAttributes.FITNESS_MACHINE_CONTROL_POINT -> {
                    // Response format: 0x80 (Response Code), request opcode, result code
                    if (value.size >= 3 && (value[0].toInt() and 0xFF) == 0x80) {
                        val response = ControlPointResponse(value[1].toInt() and 0xFF, value[2].toInt() and 0xFF)
                        val message = {
                            "Response to opcode 0x%02X: 0x%02X (%s)".format(
                                response.requestOpcode, response.resultCode, response.resultName
                            )
                        }
                        // Every command is answered, so only the ones the trainer rejects are logged
                        if (response.resultCode == 0x01) verbose(message) else Log.w(TAG, message())
                        _controlPointResponses.tryEmit(response)
                    } else {
                        Log.w(TAG, "Unexpected control point indication: ${value.toHex()}")
                    }
                }
                GattAttributes.FITNESS_MACHINE_STATUS -> {
                    if (!value.contentEquals(lastMachineStatus)) {
                        Log.d(TAG, "Fitness Machine Status: ${value.toHex()}")
                        lastMachineStatus = value
                    }
                    _machineStatus.tryEmit(value)
                }
                else -> {
                    Log.d(TAG, "Unknown characteristic: ${characteristic.uuid}")
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

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (characteristic.uuid == GattAttributes.WEIGHT) {
                val raw = if (value.size >= 2) (value[0].toInt() and 0xFF) or ((value[1].toInt() and 0xFF) shl 8) else -1
                if (status == BluetoothGatt.GATT_SUCCESS && raw != sentWeightRaw) {
                    Log.w(TAG, "Trainer kept %.3f kg instead of %.3f kg: $WEIGHT_MISMATCH"
                        .format(raw * WEIGHT_RESOLUTION_KG, sentWeightRaw * WEIGHT_RESOLUTION_KG))
                } else {
                    Log.d(TAG, "Trainer weight (status=$status): %.3f kg".format(raw * WEIGHT_RESOLUTION_KG))
                }
            }
            if (characteristic.uuid == GattAttributes.FITNESS_MACHINE_FEATURE) {
                features = if (status == BluetoothGatt.GATT_SUCCESS) FitnessMachineFeatures.parse(value) else null
                Log.d(TAG, "Fitness machine features (status=$status): $features")
            }
            if (characteristic.uuid == GattAttributes.SUPPORTED_RESISTANCE_LEVEL_RANGE) {
                val range = if (status == BluetoothGatt.GATT_SUCCESS) ResistanceRange.parse(value) else null
                if (range != null) {
                    resistanceRange = range
                    Log.d(TAG, "Supported resistance level range: $range")
                    reportConnectedTrainer(gatt, range)
                } else {
                    // Not reported as the connected trainer, so the levels saved for it at an earlier
                    // connection aren't overwritten with these
                    Log.w(TAG, "Unusable resistance level range (status=$status), keeping levels 0-100")
                }
            }
            completeGattOperation()
        }

        // Android 12 (API 32) and below only call this deprecated overload
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                onCharacteristicRead(gatt, characteristic, characteristic.value ?: ByteArray(0), status)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // Every command is two writes, so only failed ones are logged
            if (status == BluetoothGatt.GATT_SUCCESS) {
                verbose { "Characteristic write completed: UUID=${characteristic.uuid}" }
            } else if (characteristic.uuid == GattAttributes.WEIGHT) {
                // 128 (0x80) is User Data Access Not Permitted: the trainer wants User Data Service consent
                Log.w(TAG, "Trainer refused the weight (status=$status): $WEIGHT_MISMATCH")
            } else {
                Log.w(TAG, "Characteristic write failed: UUID=${characteristic.uuid}, status=$status (${
                    when (status) {
                        BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "WRITE_NOT_PERMITTED"
                        BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "INVALID_ATTRIBUTE_LENGTH"
                        else -> "ERROR_$status"
                    }
                })")
            }

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
            is GattOperation.ReadCharacteristic -> gatt.readCharacteristic(operation.characteristic)
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

        verbose { "Command bytes: ${command.toHex()}" }
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
        resistanceRange = ResistanceRange.DEFAULT
        _connectedTrainer.value = null
        features = null
        lastSimulationCommand = null
        lastMachineStatus = null
        clearGattOperations()
        currentPower = 0
        powerReported = false
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
    
    fun setResistance(level: Int) {
        sendThrottled("resistance") { sendResistanceCommand(level) }
    }

    private fun sendResistanceCommand(level: Int) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            Log.w(TAG, "Cannot set resistance: GATT not connected")
            return
        }

        Log.d(TAG, "========================================")
        Log.d(TAG, "FTMS RESISTANCE COMMAND")
        Log.d(TAG, "Input: level $level")
        
        // One level is one increment of the trainer's own range (FTMS SINT16, 0.1 resolution)
        val range = resistanceRange
        val resistanceLevel = range.rawFor(level).toShort()
        
        Log.d(TAG, "Resistance Level: ${resistanceLevel} (0.1 units, trainer range $range)")
        
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

    // Changed settings reach the trainer right away when connected, otherwise on the next connection
    fun applyRiderBikeSettings(settings: RiderBikeSettings) {
        val previous = riderBikeSettings
        if (settings == previous) return
        riderBikeSettings = settings
        if (ftmsControlPointCharacteristic == null) return
        if (settings.wheelCircumferenceMm != previous.wheelCircumferenceMm) {
            setWheelCircumference(settings.wheelCircumferenceMm)
        }
        if (settings.totalWeightKg != previous.totalWeightKg) {
            setWeight(settings.totalWeightKg)
        }
    }

    // User Data Weight: UINT16 in 0.005 kg. The trainer uses it for the climbing force in simulation mode.
    private fun setWeight(weightKg: Double) {
        val characteristic = weightCharacteristic
        if (characteristic == null) {
            Log.w(TAG, "Cannot set weight: trainer has no User Data Weight characteristic")
            return
        }
        val raw = (weightKg / WEIGHT_RESOLUTION_KG).roundToInt().coerceIn(0, 0xFFFF)
        Log.d(TAG, "Setting trainer weight to %.1f kg (raw $raw)".format(weightKg))
        sentWeightRaw = raw
        enqueueGattOperation(GattOperation.WriteCharacteristic(
            characteristic, byteArrayOf((raw and 0xFF).toByte(), ((raw shr 8) and 0xFF).toByte())
        ))
        // Read it back, so the log shows what the trainer kept
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
            enqueueGattOperation(GattOperation.ReadCharacteristic(characteristic))
        }
    }

    private fun setWheelCircumference(circumferenceMm: Double) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "FTMS SET WHEEL CIRCUMFERENCE")
        Log.d(TAG, "Setting wheel circumference to ${circumferenceMm}mm")

        // FTMS Set Wheel Circumference (opcode 0x12)
        // Parameter: UINT16 in 0.1mm resolution
        val circumference01mm = (circumferenceMm * 10).roundToInt()
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

        val command = SimulationParameters(windSpeedMps, gradePercent, crr, cw).toCommand()
        // A ride sends this every second; it is logged when what the trainer gets changes
        if (!command.contentEquals(lastSimulationCommand)) {
            Log.d(TAG, "Grade: %.2f%%, Wind: %.2f m/s, Crr: %.4f, Cw: %.2f".format(gradePercent, windSpeedMps, crr, cw))
            lastSimulationCommand = command
        }

        if (!sendControlPointCommand(command)) {
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

            service.getCharacteristic(GattAttributes.FITNESS_MACHINE_FEATURE)?.let { characteristic ->
                enqueueGattOperation(GattOperation.ReadCharacteristic(characteristic))
            } ?: Log.w(TAG, "Fitness Machine Feature characteristic NOT found")

            // Read the trainer's resistance range first, so it is known before any resistance command
            service.getCharacteristic(GattAttributes.SUPPORTED_RESISTANCE_LEVEL_RANGE)?.let { characteristic ->
                enqueueGattOperation(GattOperation.ReadCharacteristic(characteristic))
                Log.d(TAG, "Queued read of supported resistance level range")
            } ?: run {
                Log.w(TAG, "Supported resistance level range NOT found - assuming levels 0-100")
                reportConnectedTrainer(gatt, ResistanceRange.DEFAULT)
            }
            
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

            // Status messages, e.g. the simulation parameters the trainer actually applied
            service.getCharacteristic(GattAttributes.FITNESS_MACHINE_STATUS)?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                    enqueueGattOperation(GattOperation.WriteDescriptor(
                        descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    ))
                }
            }
            
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
                
                // Set wheel circumference for proper speed calculation
                setWheelCircumference(riderBikeSettings.wheelCircumferenceMm)
                setWeight(riderBikeSettings.totalWeightKg)
                
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
    
    private fun reportConnectedTrainer(gatt: BluetoothGatt, range: ResistanceRange) {
        val device = gatt.device
        _connectedTrainer.value = Trainer(device.address, device.name ?: device.address, range)
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
        
        verbose { "Heart Rate: $currentHeartRate BPM" }
    }
    
    private fun parseIndoorBikeData(data: ByteArray) {
        if (data.size < 3) return
        
        try {
            // The raw bytes are in the verbose log of onCharacteristicChanged()
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val flags = buffer.getShort().toInt() and 0xFFFF
            
            // According to FTMS spec:
            // Bit 0 = 0: Instantaneous Speed is present (UINT16, 0.01 km/h resolution)
            // Bit 0 = 1: More Data flag (speed not present in this message)
            
            // Instantaneous Speed - present when bit 0 is CLEAR (0)
            if (flags and 0x01 == 0) {
                if (buffer.remaining() >= 2) {
                    val speedRaw = buffer.getShort().toInt() and 0xFFFF
                    currentSpeed = speedRaw / 100.0f // Convert from 0.01 km/h to km/h
                } else {
                    Log.w(TAG, "Speed should be present but not enough data")
                }
            }
            
            // Skip Average Speed if present (bit 1)
            if (flags and 0x02 != 0 && buffer.remaining() >= 2) buffer.getShort()
            
            // Instantaneous Cadence - if bit 2 is set
            if (flags and 0x04 != 0 && buffer.remaining() >= 2) {
                val cadenceRaw = buffer.getShort().toInt() and 0xFFFF
                currentCadence = cadenceRaw / 2 // 0.5 RPM resolution
            }
            
            // Skip Average Cadence if present (bit 3)
            if (flags and 0x08 != 0 && buffer.remaining() >= 2) buffer.getShort()
            
            // Skip Total Distance if present (bit 4, UINT24)
            if (flags and 0x10 != 0 && buffer.remaining() >= 3) {
                buffer.get()
                buffer.getShort()
            }
            
            // Skip Resistance Level if present (bit 5)
            if (flags and 0x20 != 0 && buffer.remaining() >= 2) buffer.getShort()
            
            // Instantaneous Power - if bit 6 is set
            if (flags and 0x40 != 0 && buffer.remaining() >= 2) {
                // SINT16 in watts; a trainer may report a few negative watts, e.g. when backpedalling
                currentPower = buffer.getShort().toInt().coerceAtLeast(0)
                powerReported = true
            }
            
            // One line per notification, about one per second
            Log.d(TAG, "Bike data (flags 0x%04X): %.2f km/h, %d rpm, %d W".format(
                flags, currentSpeed, currentCadence, currentPower
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Indoor Bike Data", e)
            e.printStackTrace()
        }
    }
    
    private fun updateTrainerData() {
        _trainerData.value = TrainerData(
            power = currentPower,
            hasPower = powerReported,
            speed = currentSpeed,
            cadence = currentCadence,
            heartRate = currentHeartRate,
            timestamp = System.currentTimeMillis()
        )
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    // Detail for every message, e.g. to debug the protocol. Off by default so it doesn't fill the
    // log buffer; enable with: adb shell setprop log.tag.KickrBluetoothService VERBOSE
    private fun verbose(message: () -> String) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, message())
    }

    private fun ByteArray.toHex() = joinToString(" ") { "0x%02X".format(it) }

    companion object {
        private const val WEIGHT_RESOLUTION_KG = 0.005
        private const val WEIGHT_MISMATCH =
            "it simulates climbs with its own mass, the virtual speed with rider plus bike weight"
        private const val TAG = "KickrBluetoothService"
    }
}
