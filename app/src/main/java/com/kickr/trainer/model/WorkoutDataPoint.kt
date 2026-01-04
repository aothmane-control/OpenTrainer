package com.kickr.trainer.model

data class WorkoutDataPoint(
    val timestamp: Long,          // Timestamp in milliseconds
    val elapsedSeconds: Int,      // Elapsed time since workout start
    val power: Int,               // Power in watts
    val speed: Float,             // Speed in km/h
    val cadence: Int,             // Cadence in RPM
    val heartRate: Int,           // Heart rate in BPM
    val resistance: Int,          // Trainer resistance percentage
    val distance: Double          // Cumulative distance in meters
)
