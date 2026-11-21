package com.kickr.trainer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class GpxTrackTest {

    // Points due north along 7° E, given as (latitude, elevation)
    private fun trackOf(vararg points: Pair<Double, Double?>) = GpxTrack(
        "test", points.map { (latitude, elevation) -> GpxTrackPoint(latitude, 7.0, elevation) }
    ).withDistances()

    // 3 % for 250 m: 0.00045° of latitude is about 50 m
    private val climb = trackOf(*Array(6) { 49.0 + it * 0.00045 to it * 1.5 })

    // Points 0.00009° of latitude apart (about 10 m), elevation by point index
    private fun trackOfElevations(count: Int, elevation: (Int) -> Double) =
        trackOf(*Array(count) { i -> 49.0 + i * 0.00009 to elevation(i) })

    // 20 km on a constant grade with Gaussian elevation noise of σ = 5 m
    private fun noisyTrack(grade: Double, seed: Long = 1): GpxTrack {
        val random = Random(seed)
        return trackOfElevations(2000) { i -> 100 + grade * i * SPACING_M + 5 * random.nextGaussian() }
    }

    // Every 10 m, at least one grade window away from the ends
    private fun gradesAwayFromTheEnds(track: GpxTrack): List<Double> =
        generateSequence(track.gradeWindow) { it + 10.0 }
            .takeWhile { it <= track.totalDistance - track.gradeWindow }
            .map { track.getGradientAtDistance(it) }.toList()

    private fun List<Double>.standardDeviation(): Double {
        val mean = average()
        return sqrt(sumOf { (it - mean) * (it - mean) } / size)
    }

    private companion object {
        const val SPACING_M = 10.009
    }

    // getPointAtDistance() as it was before the binary search, as the reference
    private fun linearPointAtDistance(track: GpxTrack, targetDistance: Double): GpxTrackPoint? {
        val points = track.points
        if (points.isEmpty()) return null
        if (targetDistance <= 0) return points.first()
        if (targetDistance >= track.totalDistance) return points.last()
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            if (targetDistance >= p1.distanceFromStart && targetDistance <= p2.distanceFromStart) {
                val segmentDistance = p2.distanceFromStart - p1.distanceFromStart
                if (segmentDistance < 0.1) return p1
                val ratio = (targetDistance - p1.distanceFromStart) / segmentDistance
                val e1 = p1.elevation
                val e2 = p2.elevation
                return GpxTrackPoint(
                    latitude = p1.latitude + (p2.latitude - p1.latitude) * ratio,
                    longitude = p1.longitude + (p2.longitude - p1.longitude) * ratio,
                    elevation = if (e1 != null && e2 != null) e1 + (e2 - e1) * ratio else e1 ?: e2,
                    distanceFromStart = targetDistance
                )
            }
        }
        return points.last()
    }

    @Test
    fun endsReturnTheFirstAndLastPoint() {
        assertSame(climb.points.first(), climb.getPointAtDistance(-5.0))
        assertSame(climb.points.first(), climb.getPointAtDistance(0.0))
        assertSame(climb.points.last(), climb.getPointAtDistance(climb.totalDistance))
        assertSame(climb.points.last(), climb.getPointAtDistance(climb.totalDistance + 5.0))
        assertNull(GpxTrack("empty", emptyList()).getPointAtDistance(10.0))
    }

    @Test
    fun interpolatesBetweenPoints() {
        val second = climb.points[1]
        val third = climb.points[2]
        val middle = climb.getPointAtDistance((second.distanceFromStart + third.distanceFromStart) / 2)!!
        assertEquals(2.25, middle.elevation!!, 1e-9)
        assertEquals((second.latitude + third.latitude) / 2, middle.latitude, 1e-12)
        assertEquals(3.0, climb.getPointAtDistance(third.distanceFromStart)!!.elevation!!, 1e-9)
    }

    @Test
    fun gradeOfAConstantClimb() {
        // 1.5 m per 50.04 m
        assertEquals(3.0, climb.getGradientAtDistance(125.0), 0.01)
    }

    @Test
    fun noisyFlatTrackGivesASteadyGrade() {
        val track = noisyTrack(grade = 0.0)
        assertEquals(5.0, track.elevationNoise, 0.5)
        val grades = gradesAwayFromTheEnds(track)
        assertTrue("mean ${grades.average()} %", abs(grades.average()) < 0.5)
        assertTrue("σ ${grades.standardDeviation()} %", grades.standardDeviation() < 1.5)
    }

    @Test
    fun noisyClimbKeepsItsGrade() {
        val grades = gradesAwayFromTheEnds(noisyTrack(grade = 0.06))
        assertEquals(6.0, grades.average(), 0.5)
        assertTrue("σ ${grades.standardDeviation()} %", grades.standardDeviation() < 1.0)
    }

    @Test
    fun aSingleOutlierBarelyChangesTheGrade() {
        val cleanClimb = trackOfElevations(300) { i -> 100 + 0.06 * i * SPACING_M }
        for (track in listOf(noisyTrack(grade = 0.06), cleanClimb)) {
            val outlier = track.points.size / 2
            val withOutlier = GpxTrack(track.name, track.points.mapIndexed { i, point ->
                if (i == outlier) point.copy(elevation = point.elevation!! + 30) else point
            })
            val largestChange = generateSequence(0.0) { it + 5.0 }.takeWhile { it <= track.totalDistance }
                .maxOf { abs(withOutlier.getGradientAtDistance(it) - track.getGradientAtDistance(it)) }
            assertTrue("changed by $largestChange points", largestChange < 2.0)
        }
    }

    @Test
    fun aClimbIsFeltAtMost10mBeforeItStarts() {
        // 0 % to 8 % at point 50
        val track = trackOfElevations(100) { i -> 0.08 * SPACING_M * maxOf(0, i - 50) }
        assertEquals(20.0, track.gradeWindow, 0.0)
        val start = track.points[50].distanceFromStart
        var distance = 0.0
        while (distance <= start - 10) {
            assertTrue("at $distance m", track.getGradientAtDistance(distance) < 0.8)
            distance += 1.0
        }
        assertEquals(8.0, track.getGradientAtDistance(start + 10), 0.05)
    }

    @Test
    fun aShortSteepRampIsFelt() {
        // 30 m at 12 % (points 50 to 53) between flat sections
        val track = trackOfElevations(100) { i -> 0.12 * SPACING_M * (i - 50).coerceIn(0, 3) }
        val start = track.points[50].distanceFromStart
        val steepest = (0..30).maxOf { track.getGradientAtDistance(start + it) }
        assertTrue("steepest $steepest %", steepest >= 8.0)
    }

    @Test
    fun missingElevationsAreInterpolatedFromBothSides() {
        // Hills of ±20 m every 1 km, and the same track with every second elevation missing
        val full = trackOfElevations(301) { i -> 100 + 20 * sin(2 * PI * i * SPACING_M / 1000) }
        val sparse = GpxTrack(full.name, full.points.mapIndexed { i, point ->
            if (i % 2 == 1) point.copy(elevation = null) else point
        })
        val largestDifference = generateSequence(0.0) { it + 5.0 }.takeWhile { it <= full.totalDistance }
            .maxOf { abs(sparse.getGradientAtDistance(it) - full.getGradientAtDistance(it)) }
        assertTrue("differs by $largestDifference points", largestDifference < 0.5)
    }

    @Test
    fun segmentsAreJoinedWithoutTheGapBetweenThem() {
        // 500 m at +3 %, then 5 km further north and 285 m higher 500 m at -3 %
        val climb = trackOfElevations(51) { i -> 100 + 0.03 * i * SPACING_M }.points
        val descent = trackOfElevations(51) { i -> 400 - 0.03 * i * SPACING_M }.points
            .map { it.copy(latitude = it.latitude + 0.045, segment = 1) }
        val joined = GpxTrack("joined", climb + descent).withDistances()

        val segmentLengths = climb.last().distanceFromStart + GpxTrack("descent", descent).withDistances().totalDistance
        assertEquals(segmentLengths, joined.totalDistance, 1e-6)
        val steepest = generateSequence(0.0) { it + 1.0 }.takeWhile { it <= joined.totalDistance }
            .maxOf { abs(joined.getGradientAtDistance(it)) }
        assertTrue("steepest $steepest %", steepest <= 3.01)
    }

    @Test
    fun aTrackWithoutElevationIsFlat() {
        val track = GpxTrack(climb.name, climb.points.map { it.copy(elevation = null) })
        assertEquals(0.0, track.getGradientAtDistance(0.0), 0.0)
        assertEquals(0.0, track.getGradientAtDistance(100.0), 0.0)
    }

    @Test
    fun findsTheSamePointsAsTheLinearSearch() {
        // A repeated point, two points 0.06 m apart and missing elevations
        val track = trackOf(
            49.0 to 100.0, 49.00045 to 101.5, 49.00045 to 101.5, 49.0009 to null,
            49.0009005 to 103.0, 49.00135 to null, 49.0018 to 104.0
        )
        // Without distances every lookup inside the route falls back to the last point
        val withoutDistances = GpxTrack("raw", track.points.map { it.copy(distanceFromStart = 0.0) })

        for (candidate in listOf(track, withoutDistances)) {
            var distance = -1.0
            while (distance <= candidate.totalDistance + 1.0) {
                assertEquals("at $distance m", linearPointAtDistance(candidate, distance), candidate.getPointAtDistance(distance))
                distance += 0.05
            }
            for (point in candidate.points) {
                val atPoint = point.distanceFromStart
                assertEquals("at $atPoint m", linearPointAtDistance(candidate, atPoint), candidate.getPointAtDistance(atPoint))
            }
        }
    }
}
