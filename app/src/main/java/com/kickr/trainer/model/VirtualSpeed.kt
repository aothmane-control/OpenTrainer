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
 */
object VirtualSpeed {
    private const val G = 9.81
    // Wheels and tyres spinning up add about this much to the mass being accelerated
    private const val ROTATING_MASS_KG = 1.0
    // Bounds the pedalling force P/v when starting from standstill
    private const val MIN_SPEED_MPS = 0.5
    private const val STEPS_PER_S = 4

    /**
     * The speed in m/s after [seconds] at [powerW] on [gradePercent]. Direct-drive trainers measure
     * power at the hub, after the drivetrain losses, so all of it drives the bike.
     */
    fun advance(speedMps: Double, powerW: Double, gradePercent: Double, settings: RiderBikeSettings, seconds: Int): Double {
        val mass = settings.totalWeightKg
        val theta = atan(gradePercent / 100)
        val slopeForce = mass * G * (settings.rollingResistance * cos(theta) + sin(theta))
        val dt = 1.0 / STEPS_PER_S
        var v = speedMps
        repeat(seconds * STEPS_PER_S) {
            val air = v + settings.windSpeedMps
            val force = powerW / maxOf(v, MIN_SPEED_MPS) - slopeForce - settings.windResistance * air * abs(air)
            // Rolling resistance and climbs stop the bike but don't roll it backwards
            v = maxOf(0.0, v + force / (mass + ROTATING_MASS_KG) * dt)
        }
        return v
    }
}
