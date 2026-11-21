/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class GpxTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val time: String? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    val distanceFromStart: Double = 0.0,  // Accumulated distance in meters
    val segment: Int = 0  // The <trkseg> or <rte> the point belongs to; gaps between them aren't ridden
)

data class GpxTrack(
    val name: String,
    val points: List<GpxTrackPoint>
) {
    // Once distances are computed the last point carries the total, also when points were left out
    val totalDistance: Double by lazy {
        points.lastOrNull()?.distanceFromStart?.takeIf { it > 0 } ?: calculateTotalDistance()
    }
    
    private fun calculateTotalDistance(): Double {
        var distance = 0.0
        for (i in 0 until points.size - 1) {
            distance += segmentLength(i)
        }
        return distance
    }

    // From point i to point i + 1, on the WGS-84 ellipsoid; 0 across the gap to the next segment (#6)
    private fun segmentLength(i: Int) = if (points[i + 1].segment != points[i].segment) 0.0 else Wgs84.distance(
        points[i].latitude, points[i].longitude, points[i + 1].latitude, points[i + 1].longitude
    )
    
    // Build track with accumulated distances
    fun withDistances(): GpxTrack {
        if (points.isEmpty()) return this
        
        val pointsWithDistance = mutableListOf<GpxTrackPoint>()
        var accumulatedDistance = 0.0
        
        pointsWithDistance.add(points[0].copy(distanceFromStart = 0.0))
        
        for (i in 1 until points.size) {
            accumulatedDistance += segmentLength(i - 1)
            pointsWithDistance.add(points[i].copy(distanceFromStart = accumulatedDistance))
        }
        
        return GpxTrack(name, pointsWithDistance)
    }
    
    // Get point at specific distance using interpolation
    fun getPointAtDistance(targetDistance: Double): GpxTrackPoint? {
        if (points.isEmpty()) return null
        if (targetDistance <= 0) return points.first()
        if (targetDistance >= totalDistance) return points.last()
        
        // Find the two points that bracket the target distance
        val index = firstPointAtOrAfter(targetDistance)
        // Before the first point, in a profile that starts after 0 m, or past the last one
        if (index == 0) return points.first()
        if (index == points.size) return points.last()
        val p1 = points[index - 1]
        val p2 = points[index]

        // Interpolate between p1 and p2
        val segmentDistance = p2.distanceFromStart - p1.distanceFromStart
        if (segmentDistance < 0.1) return p1 // Too close, return first point

        val ratio = (targetDistance - p1.distanceFromStart) / segmentDistance

        return GpxTrackPoint(
            latitude = p1.latitude + (p2.latitude - p1.latitude) * ratio,
            longitude = p1.longitude + (p2.longitude - p1.longitude) * ratio,
            elevation = interpolateElevation(p1.elevation, p2.elevation, ratio),
            distanceFromStart = targetDistance
        )
    }

    // Binary search for the first point at or beyond the distance; distanceFromStart never decreases
    private fun firstPointAtOrAfter(distance: Double): Int {
        var low = 0
        var high = points.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (points[mid].distanceFromStart < distance) low = mid + 1 else high = mid
        }
        return low
    }
    
    private fun interpolateElevation(e1: Double?, e2: Double?, ratio: Double): Double? {
        return when {
            e1 != null && e2 != null -> e1 + (e2 - e1) * ratio
            e1 != null -> e1
            e2 != null -> e2
            else -> null
        }
    }
    
    /**
     * Elevation noise in m (standard deviation), estimated from how far points lie off a curve through
     * their neighbours: the line through the nearest one on each side, or the polynomial through the
     * nearest two or three. Terrain the curve doesn't follow counts as noise, so the smallest estimate
     * is kept. The line suits sharp changes of grade, the polynomials hills between points 50–100 m
     * apart. The median ignores single bends and outliers, so clean files give about 0.
     */
    val elevationNoise: Double by lazy {
        (1..MAX_NOISE_REACH).mapNotNull { reach -> profile.noiseOffCurves(reach) }.minOrNull() ?: 0.0
    }

    // Standard deviation from the median of offCurve(), or null if no point has enough neighbours
    private fun noiseOffCurves(reach: Int): Double? {
        val deviations = (reach until points.size - reach).mapNotNull { i -> offCurve(i, reach) }.sorted()
        return if (deviations.isEmpty()) null else MAD_TO_SIGMA * deviations[deviations.size / 2]
    }

    // How far point i lies off the polynomial through its [reach] neighbours on each side, divided by how
    // much independent noise of 1 m on all of them moves it. With reach 1 this is residualAt().
    private fun offCurve(i: Int, reach: Int): Double? {
        val distances = (i - reach..i + reach).map { points[it].distanceFromStart }
        if (distances.zipWithNext().any { (a, b) -> b <= a }) return null
        val x = points[i].distanceFromStart
        val neighbours = (i - reach..i + reach).filter { it != i }
        var prediction = 0.0
        var squaredWeights = 0.0
        for (j in neighbours) {
            // Lagrange weight of neighbour j at x
            val xj = points[j].distanceFromStart
            val weight = neighbours.filter { it != j }.fold(1.0) { product, k ->
                val xk = points[k].distanceFromStart
                product * (x - xk) / (xj - xk)
            }
            prediction += weight * (points[j].elevation ?: return null)
            squaredWeights += weight * weight
        }
        val elevation = points[i].elevation ?: return null
        return abs(elevation - prediction) / sqrt(1 + squaredWeights)
    }

    /**
     * Length in m over which the grade is fitted: the minimum on clean elevations, so climbs are felt
     * where they start, and on noisy ones long enough that the grade noise stays near GRADE_NOISE.
     */
    val gradeWindow: Double by lazy {
        val elevated = profile.points
        val spacing = if (elevated.size > 1) {
            (elevated.last().distanceFromStart - elevated.first().distanceFromStart) / (elevated.size - 1)
        } else 0.0
        // A least-squares slope over W m with independent points every s m varies by σ·sqrt(12 s) / W^1.5
        (elevationNoise * sqrt(12 * spacing) / GRADE_NOISE).pow(2.0 / 3)
            .coerceIn(MIN_GRADE_WINDOW_M, MAX_GRADE_WINDOW_M)
    }

    // Only the points with an elevation, so the grade is interpolated linearly between the ones on
    // either side of a gap instead of holding one value (#7). Without any elevation the ride is flat.
    // Each segment continues at the elevation where the one before ended, so the climb or drop across
    // the gap between them isn't felt either (#6).
    private val profile: GpxTrack by lazy {
        var offset = 0.0
        var previous: GpxTrackPoint? = null
        copy(points = points.mapNotNull { point ->
            val elevation = point.elevation ?: return@mapNotNull null
            previous?.let { if (it.segment != point.segment) offset = (it.elevation ?: elevation) - elevation }
            point.copy(elevation = elevation + offset).also { previous = it }
        })
    }

    // The profile with single-point spikes moved onto the line through their neighbours: points further
    // off it than two road grades of up to 35 % could put them, than 3 σ of the noise, and than both
    // neighbours. Those are GPS outliers, not terrain. Moving ordinary noisy points would only add
    // noise to the fitted grade.
    private val despiked: GpxTrack by lazy {
        val elevated = profile.points
        val residuals = elevated.indices.map { i -> if (i in 1 until elevated.lastIndex) profile.residualAt(i) else null }
        profile.copy(points = elevated.mapIndexed { i, point ->
            val (residual, noiseScale, roadLimit) = residuals[i] ?: return@mapIndexed point
            val isSpike = abs(residual) > maxOf(roadLimit, SPIKE_SIGMAS * elevationNoise * noiseScale) &&
                abs(residual) >= abs(residuals[i - 1]?.first ?: 0.0) &&
                abs(residual) >= abs(residuals[i + 1]?.first ?: 0.0)
            if (isSpike) point.copy(elevation = point.elevation?.minus(residual)) else point
        })
    }

    // How far point i lies above the line through its neighbours; how much independent noise of 1 m on
    // all three points moves it, sqrt(1 + (1 - w)² + w²); and the most two road grades could
    private fun residualAt(i: Int): Triple<Double, Double, Double>? {
        val elevation = points[i].elevation ?: return null
        val elevationBefore = points[i - 1].elevation ?: return null
        val elevationAfter = points[i + 1].elevation ?: return null
        val before = points[i].distanceFromStart - points[i - 1].distanceFromStart
        val after = points[i + 1].distanceFromStart - points[i].distanceFromStart
        if (before <= 0 || after <= 0) return null
        val w = before / (before + after)
        val line = elevationBefore + (elevationAfter - elevationBefore) * w
        return Triple(
            elevation - line,
            sqrt(1 + (1 - w) * (1 - w) + w * w),
            2 * MAX_ROAD_GRADE * before * after / (before + after)
        )
    }

    /**
     * Grade in % at a distance: the least-squares slope of the elevation, sampled every 5 m over
     * gradeWindow centred on the distance and cut at the ends of the route.
     */
    fun getGradientAtDistance(distance: Double): Double {
        val start = (distance - gradeWindow / 2).coerceAtLeast(0.0)
        val end = (distance + gradeWindow / 2).coerceAtMost(totalDistance)
        val samples = generateSequence(start) { it + SAMPLE_STEP_M }.takeWhile { it <= end }
            .mapNotNull { x -> despiked.getPointAtDistance(x)?.elevation?.let { x to it } }
            .toList()
        if (samples.size < 2) return 0.0
        val meanX = samples.sumOf { it.first } / samples.size
        val meanElevation = samples.sumOf { it.second } / samples.size
        val slope = samples.sumOf { (x, h) -> (x - meanX) * (h - meanElevation) } /
            samples.sumOf { (x, _) -> (x - meanX) * (x - meanX) }
        return slope * 100
    }

    /**
     * The grade along the route for the profile chart, from GradeProfile.sample(). Up to 1000 fits, so
     * loading a file computes it in the background rather than starting a ride on the main thread.
     */
    val gradeProfile: List<Pair<Double, Double>> by lazy { GradeProfile.sample(this) }

    companion object {
        private const val SAMPLE_STEP_M = 5.0
        private const val GRADE_NOISE = 0.0075       // 0.75 % standard deviation on noisy elevations
        private const val MIN_GRADE_WINDOW_M = 20.0  // On clean elevations a climb is felt 10 m early
        private const val MAX_GRADE_WINDOW_M = 500.0
        private const val MAX_ROAD_GRADE = 0.35
        private const val SPIKE_SIGMAS = 3.0
        private const val MAD_TO_SIGMA = 1.4826      // Median absolute deviation to σ for normal noise
        private const val MAX_NOISE_REACH = 3        // Up to quintics through 3 points on each side
    }
}
