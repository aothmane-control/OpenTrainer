/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.model

/**
 * Rider and bike properties the trainer simulates with. The ranges are limited by what the
 * FTMS simulation parameters and the User Data weight can carry.
 */
data class RiderBikeSettings(
    val riderWeightKg: Double = Parameter.RIDER_WEIGHT.default,
    val bikeWeightKg: Double = Parameter.BIKE_WEIGHT.default,
    val wheelCircumferenceMm: Double = Parameter.WHEEL_CIRCUMFERENCE.default,
    val rollingResistance: Double = Parameter.ROLLING_RESISTANCE.default,
    val windResistance: Double = Parameter.WIND_RESISTANCE.default,
    val windSpeedMps: Double = Parameter.WIND_SPEED.default
) {
    // The trainer simulates the whole system, so climbing depends on rider plus bike
    val totalWeightKg get() = riderWeightKg + bikeWeightKg

    fun valueOf(parameter: Parameter): Double = when (parameter) {
        Parameter.RIDER_WEIGHT -> riderWeightKg
        Parameter.BIKE_WEIGHT -> bikeWeightKg
        Parameter.WHEEL_CIRCUMFERENCE -> wheelCircumferenceMm
        Parameter.ROLLING_RESISTANCE -> rollingResistance
        Parameter.WIND_RESISTANCE -> windResistance
        Parameter.WIND_SPEED -> windSpeedMps
    }

    enum class Parameter(val key: String, val default: Double, val range: ClosedFloatingPointRange<Double>) {
        RIDER_WEIGHT("rider_weight_kg", 75.0, 30.0..200.0),
        BIKE_WEIGHT("bike_weight_kg", 9.0, 3.0..30.0),
        // 2100 mm is a 700x25C road wheel; FTMS carries up to 6553.5 mm
        WHEEL_CIRCUMFERENCE("wheel_circumference_mm", 2100.0, 1000.0..3000.0),
        // Crr: good road tyres on asphalt; FTMS carries 0.0001 steps in one byte
        ROLLING_RESISTANCE("rolling_resistance", 0.004, 0.001..0.0255),
        // Cw in kg/m as sent in FTMS, 0.01 steps in one byte: ρ·CdA, so air drag is ½·Cw·v²
        WIND_RESISTANCE("wind_resistance", 0.51, 0.0..2.55),
        // Positive is headwind
        WIND_SPEED("wind_speed_mps", 0.0, -20.0..20.0)
    }

    companion object {
        val DEFAULT = RiderBikeSettings()

        // Parameters missing from the map keep their default
        fun of(values: Map<Parameter, Double>) = RiderBikeSettings(
            riderWeightKg = values[Parameter.RIDER_WEIGHT] ?: Parameter.RIDER_WEIGHT.default,
            bikeWeightKg = values[Parameter.BIKE_WEIGHT] ?: Parameter.BIKE_WEIGHT.default,
            wheelCircumferenceMm = values[Parameter.WHEEL_CIRCUMFERENCE] ?: Parameter.WHEEL_CIRCUMFERENCE.default,
            rollingResistance = values[Parameter.ROLLING_RESISTANCE] ?: Parameter.ROLLING_RESISTANCE.default,
            windResistance = values[Parameter.WIND_RESISTANCE] ?: Parameter.WIND_RESISTANCE.default,
            windSpeedMps = values[Parameter.WIND_SPEED] ?: Parameter.WIND_SPEED.default
        )
    }
}
