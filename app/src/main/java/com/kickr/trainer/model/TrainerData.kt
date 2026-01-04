package com.kickr.trainer.model

data class TrainerData(
    val power: Int = 0,              // Instantaneous power in watts
    val speed: Float = 0f,           // Speed in km/h
    val cadence: Int = 0,            // Cadence in RPM
    val heartRate: Int = 0,          // Heart rate in BPM
    val distance: Float = 0f,        // Distance in km
    val timestamp: Long = System.currentTimeMillis()
)
