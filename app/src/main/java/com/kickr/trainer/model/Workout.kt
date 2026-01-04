package com.kickr.trainer.model

data class WorkoutInterval(
    val duration: Int,        // Duration in seconds
    val resistance: Int       // Resistance percentage (0-100)
)

data class Workout(
    val totalDuration: Int,   // Total workout duration in seconds
    val intervals: List<WorkoutInterval>
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
}
