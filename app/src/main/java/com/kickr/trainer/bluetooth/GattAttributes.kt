package com.kickr.trainer.bluetooth

import java.util.*

object GattAttributes {
    // Standard Bluetooth SIG UUIDs
    
    // Cycling Power Service
    val CYCLING_POWER_SERVICE: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
    val CYCLING_POWER_MEASUREMENT: UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
    val CYCLING_POWER_FEATURE: UUID = UUID.fromString("00002a65-0000-1000-8000-00805f9b34fb")
    val CYCLING_POWER_CONTROL_POINT: UUID = UUID.fromString("00002a66-0000-1000-8000-00805f9b34fb")
    
    // FitnessMachine Service (for resistance control on some trainers)
    val FITNESS_MACHINE_SERVICE: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    val FITNESS_MACHINE_CONTROL_POINT: UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")
    val INDOOR_BIKE_DATA: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
    
    // Cycling Speed and Cadence Service
    val CYCLING_SPEED_CADENCE_SERVICE: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
    val CSC_MEASUREMENT: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
    
    // Heart Rate Service
    val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    
    // Client Characteristic Configuration Descriptor (for enabling notifications)
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // Device Information Service
    val DEVICE_INFORMATION_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val MANUFACTURER_NAME: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    val MODEL_NUMBER: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
}
