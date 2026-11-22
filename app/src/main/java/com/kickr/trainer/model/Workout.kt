/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

enum class WorkoutType {
    RESISTANCE,  // Resistance-based (trainer-specific levels)
    POWER        // Power-based (watts)
}

data class WorkoutInterval(
    val duration: Int,        // Duration in seconds
    val resistance: Int,      // Resistance level of the trainer (0 = its minimum) - used when type is RESISTANCE
    val power: Int = 0        // Target power in watts - used when type is POWER
)

data class Workout(
    val totalDuration: Int,   // Total workout duration in seconds
    val intervals: List<WorkoutInterval>,
    val type: WorkoutType = WorkoutType.RESISTANCE,  // Workout type
    val gpxTrack: GpxTrack? = null  // Optional GPX track for elevation-based workouts
) {
    fun isValid(): Boolean {
        if (totalDuration <= 0 || intervals.isEmpty() || intervals.any { it.duration <= 0 }) return false
        val sumDuration = intervals.sumOf { it.duration.toLong() }
        val valueCheck = when (type) {
            // The upper limit depends on the trainer, which clamps levels to its own range
            WorkoutType.RESISTANCE -> intervals.all { it.resistance >= 0 }
            WorkoutType.POWER -> intervals.all { it.power > 0 && it.power <= 1000 }
        }
        return sumDuration == totalDuration.toLong() && valueCheck
    }
    
    fun getCurrentInterval(elapsedSeconds: Int): WorkoutInterval? {
        return intervals.getOrNull(getCurrentIntervalIndex(elapsedSeconds))
    }

    // Position of the current interval, or -1 when the workout is over.
    // Use this rather than intervals.indexOf(), which returns the first of several identical intervals.
    fun getCurrentIntervalIndex(elapsedSeconds: Int): Int {
        var accumulatedTime = 0
        intervals.forEachIndexed { index, interval ->
            accumulatedTime += interval.duration
            if (elapsedSeconds < accumulatedTime) {
                return index
            }
        }
        return -1
    }
    
    fun getRemainingTime(elapsedSeconds: Int): Int {
        return (totalDuration - elapsedSeconds).coerceAtLeast(0)
    }

    // Seconds left in the current interval, or 0 when the workout is over
    fun getIntervalRemainingTime(elapsedSeconds: Int): Int {
        val index = getCurrentIntervalIndex(elapsedSeconds)
        if (index < 0) return 0
        return intervals.take(index + 1).sumOf { it.duration } - elapsedSeconds
    }
    
    // Get current position on track based on distance
    fun getPositionAtDistance(distanceMeters: Double): GpxTrackPoint? {
        return gpxTrack?.getPointAtDistance(distanceMeters)
    }
}