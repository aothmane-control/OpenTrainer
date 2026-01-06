/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

data class WorkoutInterval(
    val duration: Int,        // Duration in seconds
    val resistance: Int       // Resistance percentage (0-100)
)

data class Workout(
    val totalDuration: Int,   // Total workout duration in seconds
    val intervals: List<WorkoutInterval>,
    val gpxTrack: GpxTrack? = null  // Optional GPX track for elevation-based workouts
) {
    fun isValid(): Boolean {
        val sumDuration = intervals.sumOf { it.duration }
        return sumDuration == totalDuration && intervals.all { it.resistance in 0..100 }
    }
    
    fun getCurrentInterval(elapsedSeconds: Int): WorkoutInterval? {
        var accumulatedTime = 0
        for (interval in intervals) {
            accumulatedTime += interval.duration
            if (elapsedSeconds < accumulatedTime) {
                return interval
            }
        }
        return null
    }
    
    fun getRemainingTime(elapsedSeconds: Int): Int {
        return (totalDuration - elapsedSeconds).coerceAtLeast(0)
    }
    
    // Get resistance based on distance traveled (for GPX workouts)
    fun getResistanceAtDistance(distanceMeters: Double): Int {
        if (gpxTrack == null) return 50 // Default if no GPX
        
        val gradient = gpxTrack.getGradientAtDistance(distanceMeters)
        return calculateResistanceFromGradient(gradient)
    }
    
    // Get current position on track based on distance
    fun getPositionAtDistance(distanceMeters: Double): GpxTrackPoint? {
        return gpxTrack?.getPointAtDistance(distanceMeters)
    }
    
    private fun calculateResistanceFromGradient(gradientPercent: Double): Int {
        // Physics-based resistance mapping inspired by real cycling physics
        // Smoother, more progressive curve based on power requirements
        // Formula considers that power increases non-linearly with gradient
        
        // Base resistance from gradient (scaled by 8 for better feel)
        val baseResistance = gradientPercent * 8.0
        
        // Add quadratic term for steep grades (gradient effect compounds)
        val quadraticTerm = if (gradientPercent > 0) {
            (gradientPercent * gradientPercent) * 0.5
        } else {
            0.0
        }
        
        // Minimum resistance on downhills, progressive on uphills
        val totalResistance = when {
            gradientPercent < -2.0 -> 5  // Steep downhill - minimal resistance
            gradientPercent < 0.0 -> 15 + gradientPercent * 5  // Gentle downhill
            else -> 25 + baseResistance + quadraticTerm  // Uphill with physics
        }
        
        return totalResistance.toInt().coerceIn(0, 100)
    }
}