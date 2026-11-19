/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

/** The grade a GPX ride sends to the trainer, sampled along the route for the profile chart. */
object GradeProfile {
    // The grade is averaged over 100 m, so finer steps would show nothing new
    private const val MIN_STEP_M = 10.0
    private const val MAX_SAMPLES = 1000
    // Longer routes show this much around the rider, so climbs stay readable
    const val WINDOW_M = 10_000.0
    private const val WINDOW_BEHIND_M = 2_000.0

    /** (distance in m, grade in %) from start to end, including the end of the route. */
    fun sample(track: GpxTrack): List<Pair<Double, Double>> {
        val total = track.totalDistance
        if (total <= 0.0) return emptyList()
        val step = maxOf(MIN_STEP_M, total / MAX_SAMPLES)
        val distances = generateSequence(0.0) { it + step }.takeWhile { it < total } + total
        return distances.map { it to track.getGradientAtDistance(it) }.toList()
    }

    /** The whole route if it is short, otherwise a window that moves with the rider. */
    fun visibleRange(distance: Double, total: Double): ClosedFloatingPointRange<Double> {
        if (total <= WINDOW_M) return 0.0..total
        val start = (distance - WINDOW_BEHIND_M).coerceIn(0.0, total - WINDOW_M)
        return start..(start + WINDOW_M)
    }
}
