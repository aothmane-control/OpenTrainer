package com.kickr.trainer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GpxTrackTest {

    // Points due north along 7° E, given as (latitude, elevation)
    private fun trackOf(vararg points: Pair<Double, Double?>) = GpxTrack(
        "test", points.map { (latitude, elevation) -> GpxTrackPoint(latitude, 7.0, elevation) }
    ).withDistances()

    // 3 % for 250 m: 0.00045° of latitude is about 50 m
    private val climb = trackOf(*Array(6) { 49.0 + it * 0.00045 to it * 1.5 })

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
