/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GpxTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val time: String? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    val distanceFromStart: Double = 0.0  // Accumulated distance in meters
)

data class GpxTrack(
    val name: String,
    val points: List<GpxTrackPoint>
) {
    val totalDistance: Double by lazy {
        calculateTotalDistance()
    }
    
    private fun calculateTotalDistance(): Double {
        var distance = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = GeoPoint(points[i].latitude, points[i].longitude)
            val p2 = GeoPoint(points[i + 1].latitude, points[i + 1].longitude)
            distance += p1.distanceToAsDouble(p2)
        }
        return distance
    }
    
    // Build track with accumulated distances
    fun withDistances(): GpxTrack {
        if (points.isEmpty()) return this
        
        val pointsWithDistance = mutableListOf<GpxTrackPoint>()
        var accumulatedDistance = 0.0
        
        pointsWithDistance.add(points[0].copy(distanceFromStart = 0.0))
        
        for (i in 1 until points.size) {
            val p1 = GeoPoint(points[i - 1].latitude, points[i - 1].longitude)
            val p2 = GeoPoint(points[i].latitude, points[i].longitude)
            accumulatedDistance += p1.distanceToAsDouble(p2)
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
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            
            if (targetDistance >= p1.distanceFromStart && targetDistance <= p2.distanceFromStart) {
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
        }
        
        return points.last()
    }
    
    private fun interpolateElevation(e1: Double?, e2: Double?, ratio: Double): Double? {
        return when {
            e1 != null && e2 != null -> e1 + (e2 - e1) * ratio
            e1 != null -> e1
            e2 != null -> e2
            else -> null
        }
    }
    
    // Calculate gradient at a specific distance
    fun getGradientAtDistance(distance: Double): Double {
        val windowSize = 50.0 // meters
        val pointBefore = getPointAtDistance((distance - windowSize).coerceAtLeast(0.0))
        val pointAfter = getPointAtDistance((distance + windowSize).coerceAtMost(totalDistance))
        
        if (pointBefore?.elevation == null || pointAfter?.elevation == null) return 0.0
        
        val elevationChange = pointAfter.elevation - pointBefore.elevation
        val distanceChange = pointAfter.distanceFromStart - pointBefore.distanceFromStart
        
        if (distanceChange < 1.0) return 0.0
        
        return (elevationChange / distanceChange) * 100 // Return as percentage
    }
}
