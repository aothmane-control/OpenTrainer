package com.kickr.trainer.model

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sin

class Wgs84Test {

    private fun dms(degrees: Int, minutes: Int, seconds: Double) = degrees + minutes / 60.0 + seconds / 3600.0

    // Meridional radius of curvature M = a (1 - e²) / (1 - e² sin²φ)^1.5
    private fun meridionalRadius(latitude: Double): Double {
        val eSq = Wgs84.FLATTENING * (2 - Wgs84.FLATTENING)
        val sinPhi = sin(Math.toRadians(latitude))
        return Wgs84.SEMI_MAJOR_AXIS_M * (1 - eSq) / (1 - eSq * sinPhi * sinPhi).pow(1.5)
    }

    @Test
    fun vincentysExampleFromFlindersPeakToBuninyong() {
        // Published as 54 972.271 m on GRS80, which differs from WGS-84 by far less than 1 mm here
        val distance = Wgs84.distance(
            -dms(37, 57, 3.72030), dms(144, 25, 29.52440),
            -dms(37, 39, 10.15610), dms(143, 55, 35.38390)
        )
        assertEquals(54972.271, distance, 0.001)
    }

    @Test
    fun oneDegreeAlongTheEquator() {
        val expected = Wgs84.SEMI_MAJOR_AXIS_M * Math.toRadians(1.0)  // 111 319.491 m
        assertEquals(expected, Wgs84.distance(0.0, 10.0, 0.0, 11.0), 1e-4)
    }

    @Test
    fun meridianArcOfTheTestRoute() {
        // 0.009° due north from 49° N; over 1 km, M at the middle is exact to well below 1 µm
        val expected = meridionalRadius(49.0045) * Math.toRadians(0.009)
        assertEquals(expected, Wgs84.distance(49.0, 7.0, 49.009, 7.0), 1e-4)
        // The sphere with the equatorial radius gave 1001.875 m
        assertEquals(1000.888, expected, 0.001)
    }

    @Test
    fun crossingTheAntimeridianTakesTheShortWay() {
        val expected = Wgs84.SEMI_MAJOR_AXIS_M * Math.toRadians(0.001)
        assertEquals(expected, Wgs84.distance(0.0, 179.9995, 0.0, -179.9995), 1e-4)
    }

    @Test
    fun samePointAndDirection() {
        assertEquals(0.0, Wgs84.distance(49.0, 7.0, 49.0, 7.0), 0.0)
        val there = Wgs84.distance(49.0, 7.0, 49.01, 7.02)
        val back = Wgs84.distance(49.01, 7.02, 49.0, 7.0)
        assertEquals(there, back, 1e-6)
    }
}
