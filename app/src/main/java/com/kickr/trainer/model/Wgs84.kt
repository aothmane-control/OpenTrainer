/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Distances on the WGS-84 ellipsoid, the datum of GPS and GPX coordinates.
 *
 * osmdroid's GeoPoint.distanceToAsDouble() uses a sphere with the equatorial radius, which made
 * routes about 0.1 % too long (1001.9 m instead of 1000.9 m for 0.009° of latitude at 49° N).
 */
object Wgs84 {
    const val SEMI_MAJOR_AXIS_M = 6378137.0
    const val FLATTENING = 1 / 298.257223563
    private const val SEMI_MINOR_AXIS_M = SEMI_MAJOR_AXIS_M * (1 - FLATTENING)
    private const val SECOND_ECCENTRICITY_SQ =
        (SEMI_MAJOR_AXIS_M * SEMI_MAJOR_AXIS_M - SEMI_MINOR_AXIS_M * SEMI_MINOR_AXIS_M) /
            (SEMI_MINOR_AXIS_M * SEMI_MINOR_AXIS_M)

    // Mean radius (2a + b) / 3, for the spherical fallback
    private const val MEAN_RADIUS_M = (2 * SEMI_MAJOR_AXIS_M + SEMI_MINOR_AXIS_M) / 3

    private const val MAX_ITERATIONS = 200
    private const val CONVERGENCE_RAD = 1e-12  // about 6 µm on the ground

    /**
     * Geodesic distance in metres between two points given in degrees, with Vincenty's inverse
     * formula (T. Vincenty, Survey Review 23(176), 1975), accurate to about 0.5 mm.
     */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val f = FLATTENING
        val l = Math.toRadians(longitudeDifference(lon1, lon2))
        val u1 = atan((1 - f) * tan(Math.toRadians(lat1)))
        val u2 = atan((1 - f) * tan(Math.toRadians(lat2)))
        val sinU1 = sin(u1)
        val cosU1 = cos(u1)
        val sinU2 = sin(u2)
        val cosU2 = cos(u2)

        var lambda = l
        repeat(MAX_ITERATIONS) {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)
            val crossTerm = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda
            val sinSigma = sqrt(cosU2 * sinLambda * cosU2 * sinLambda + crossTerm * crossTerm)
            if (sinSigma == 0.0) return 0.0  // Same point
            val cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            val sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            val cosSqAlpha = 1 - sinAlpha * sinAlpha
            // On the equator cos²α is 0 and the term drops out
            val cos2SigmaM = if (cosSqAlpha != 0.0) cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha else 0.0
            val c = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha))
            val previous = lambda
            lambda = l + (1 - c) * f * sinAlpha *
                (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))

            if (abs(lambda - previous) < CONVERGENCE_RAD) {
                val uSq = cosSqAlpha * SECOND_ECCENTRICITY_SQ
                val a = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
                val b = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
                val deltaSigma = b * sinSigma * (cos2SigmaM + b / 4 * (
                    cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                        b / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)
                    ))
                return SEMI_MINOR_AXIS_M * a * (sigma - deltaSigma)
            }
        }
        // Only nearly antipodal points fail to converge, never neighbouring track points
        return sphericalDistance(lat1, lon1, lat2, lon2)
    }

    // In (-180°, 180°], so tracks crossing the antimeridian take the short way
    private fun longitudeDifference(lon1: Double, lon2: Double): Double {
        val difference = (lon2 - lon1) % 360
        return when {
            difference > 180 -> difference - 360
            difference <= -180 -> difference + 360
            else -> difference
        }
    }

    // Haversine on the mean sphere, accurate to about 0.5 %
    private fun sphericalDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val sinHalfDPhi = sin((phi2 - phi1) / 2)
        val sinHalfDLambda = sin(Math.toRadians(longitudeDifference(lon1, lon2)) / 2)
        val h = sinHalfDPhi * sinHalfDPhi + cos(phi1) * cos(phi2) * sinHalfDLambda * sinHalfDLambda
        return 2 * MEAN_RADIUS_M * asin(sqrt(h.coerceAtMost(1.0)))
    }
}
