/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/**
 * The speed a rider would have on the road at the measured power, from the same mass, Crr and Cw
 * the trainer simulates with. A direct-drive trainer's own speed is only its flywheel's, which
 * slows down on descents where a rider outdoors would coast faster.
 *
 * All of the measured power drives the bike: a direct-drive trainer measures it at the hub, after
 * the chain, and simulates the road with it without drivetrain losses.
 */
object VirtualSpeed {
    private const val G = 9.81
    // Wheels and tyres spinning up add about this much to the mass being accelerated
    private const val ROTATING_MASS_KG = 1.0
    // Bounds the pedalling force P/v when starting from standstill
    private const val MIN_SPEED_MPS = 0.5
    private const val STEPS_PER_S = 4

    /** The speed in m/s after [seconds] at [powerW] on [gradePercent]. */
    fun advance(speedMps: Double, powerW: Double, gradePercent: Double, settings: RiderBikeSettings, seconds: Int): Double {
        val mass = settings.totalWeightKg
        val theta = atan(gradePercent / 100)
        val slopeForce = mass * G * (settings.rollingResistance * cos(theta) + sin(theta))
        val dt = 1.0 / STEPS_PER_S
        var v = speedMps
        repeat(seconds * STEPS_PER_S) {
            // Air drag Cw·v² with the Cw in kg/m sent to the trainer. Whether the trainer applies Cw·v²
            // or ½·Cw·v² is still open (review #8); riding the flat at a steady 30 km/h or more, the
            // trainer speed in the ride log matches this speed only with Cw·v².
            val air = v + settings.windSpeedMps
            val force = powerW / maxOf(v, MIN_SPEED_MPS) - slopeForce - settings.windResistance * air * abs(air)
            // Rolling resistance and climbs stop the bike but don't roll it backwards
            v = maxOf(0.0, v + force / (mass + ROTATING_MASS_KG) * dt)
        }
        return v
    }
}
