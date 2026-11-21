/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

/** The grade a GPX ride sends to the trainer, sampled along the route for the profile chart. */
object GradeProfile {
    // The grade is fitted over at least 20 m, so finer steps would show little new
    private const val MIN_STEP_M = 10.0
    private const val MAX_SAMPLES = 1000
    // Longer routes show this much around the rider, so climbs stay readable
    const val WINDOW_M = 10_000.0
    private const val WINDOW_BEHIND_M = 2_000.0
    // So bad data can't reach the trainer. The KICKR simulates climbs up to 20 %, the KICKR CORE up to
    // 16 %: on a CORE steeper grades feel like 16 %, while the virtual speed still follows the grade
    // sent, so the rider climbs them as slowly as on the road.
    const val MIN_GRADE = -15.0
    const val MAX_GRADE = 20.0
    // Percentage points per second, so grade changes don't jolt the rider or the trainer's control loop
    private const val MAX_GRADE_CHANGE_PER_S = 2.0

    /** The grade to send: the route's grade within MIN_GRADE..MAX_GRADE, changed by at most 2 points a second. */
    fun limit(routeGrade: Double, previous: Double?, seconds: Int): Double {
        val clamped = routeGrade.coerceIn(MIN_GRADE, MAX_GRADE)
        if (previous == null) return clamped
        val step = MAX_GRADE_CHANGE_PER_S * seconds
        return clamped.coerceIn(previous - step, previous + step)
    }

    /** (distance in m, grade in %) from start to end, including the end of the route. */
    fun sample(track: GpxTrack): List<Pair<Double, Double>> {
        val total = track.totalDistance
        if (total <= 0.0) return emptyList()
        val step = maxOf(MIN_STEP_M, total / MAX_SAMPLES)
        val distances = generateSequence(0.0) { it + step }.takeWhile { it < total } + total
        return distances.map { it to track.getGradientAtDistance(it).coerceIn(MIN_GRADE, MAX_GRADE) }.toList()
    }

    /** The whole route if it is short, otherwise a window that moves with the rider. */
    fun visibleRange(distance: Double, total: Double): ClosedFloatingPointRange<Double> {
        if (total <= WINDOW_M) return 0.0..total
        val start = (distance - WINDOW_BEHIND_M).coerceIn(0.0, total - WINDOW_M)
        return start..(start + WINDOW_M)
    }
}
