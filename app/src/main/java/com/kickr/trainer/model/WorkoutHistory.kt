/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WorkoutHistory(
    val startTime: Long,                           // Workout start timestamp
    val endTime: Long,                             // Workout end timestamp
    val workoutType: String,                       // "RESISTANCE" or "GPX"
    val workoutName: String,                       // Name or description of workout
    val dataPoints: List<WorkoutDataPoint>,        // All recorded data points
    val totalDistance: Double,                     // Total distance in meters
    val averagePower: Double,                      // Average power in watts
    val maxPower: Int,                             // Maximum power in watts
    val averageSpeed: Double,                      // Average speed in km/h
    val maxSpeed: Float,                           // Maximum speed in km/h
    val averageCadence: Double,                    // Average cadence in RPM
    val averageHeartRate: Double                   // Average heart rate in BPM
) {
    fun getDurationSeconds(): Int {
        return ((endTime - startTime) / 1000).toInt()
    }
    
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(startTime))
    }
    
    fun getFormattedDuration(): String {
        val duration = getDurationSeconds()
        val hours = duration / 3600
        val minutes = (duration % 3600) / 60
        val seconds = duration % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
