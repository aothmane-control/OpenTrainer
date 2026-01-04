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
        // Gradient-based resistance mapping
        return when {
            gradientPercent < -1.0 -> 0 // Downhill > 1%
            gradientPercent < 0.0 -> 20 // Slight downhill
            gradientPercent < 2.0 -> 30 // Flat to slight incline
            gradientPercent < 4.0 -> 45 // Moderate incline
            gradientPercent < 6.0 -> 60 // Steep incline
            gradientPercent < 8.0 -> 75 // Very steep
            else -> 90 // Extremely steep (>8%)
        }.coerceIn(0, 100)
    }
}