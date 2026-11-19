package com.kickr.trainer.model

import com.kickr.trainer.model.RiderBikeSettings.Parameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderBikeSettingsTest {

    @Test
    fun defaultsAreWithinTheirRanges() {
        for (parameter in Parameter.entries) {
            assertTrue(parameter.name, parameter.default in parameter.range)
            assertEquals(parameter.name, parameter.default, RiderBikeSettings.DEFAULT.valueOf(parameter), 0.0)
        }
    }

    @Test
    fun keepsTheValuesTheAppUsedBefore() {
        // Unchanged settings must not change how existing rides feel
        assertEquals(2100.0, RiderBikeSettings.DEFAULT.wheelCircumferenceMm, 0.0)
        assertEquals(0.004, RiderBikeSettings.DEFAULT.rollingResistance, 0.0)
        assertEquals(0.51, RiderBikeSettings.DEFAULT.windResistance, 0.0)
        assertEquals(0.0, RiderBikeSettings.DEFAULT.windSpeedMps, 0.0)
    }

    @Test
    fun totalWeightIsRiderPlusBike() {
        assertEquals(84.0, RiderBikeSettings(riderWeightKg = 75.0, bikeWeightKg = 9.0).totalWeightKg, 1e-9)
    }

    @Test
    fun buildsFromValuesAndKeepsDefaultsForMissingOnes() {
        val settings = RiderBikeSettings.of(mapOf(Parameter.RIDER_WEIGHT to 62.5, Parameter.WIND_SPEED to -3.0))
        assertEquals(62.5, settings.riderWeightKg, 0.0)
        assertEquals(-3.0, settings.windSpeedMps, 0.0)
        assertEquals(Parameter.BIKE_WEIGHT.default, settings.bikeWeightKg, 0.0)
        assertEquals(RiderBikeSettings.DEFAULT, RiderBikeSettings.of(emptyMap()))
    }

    @Test
    fun everyParameterRoundTripsThroughValueOf() {
        val values = Parameter.entries.associateWith { it.range.endInclusive }
        val settings = RiderBikeSettings.of(values)
        for (parameter in Parameter.entries) {
            assertEquals(parameter.name, values.getValue(parameter), settings.valueOf(parameter), 0.0)
        }
    }

    @Test
    fun rangesFitTheTrainerFields() {
        // FTMS: wheel circumference UINT16 in 0.1 mm, Crr UINT8 in 0.0001, Cw UINT8 in 0.01 kg/m,
        // wind speed SINT16 in 0.001 m/s. User Data weight: UINT16 in 0.005 kg.
        assertTrue(Parameter.WHEEL_CIRCUMFERENCE.range.endInclusive * 10 <= 65535)
        assertTrue(Parameter.ROLLING_RESISTANCE.range.endInclusive * 10000 <= 255 + 1e-9)
        assertTrue(Parameter.WIND_RESISTANCE.range.endInclusive * 100 <= 255 + 1e-9)
        assertTrue(Parameter.WIND_SPEED.range.endInclusive * 1000 <= 32767)
        assertTrue(Parameter.WIND_SPEED.range.start * 1000 >= -32768)
        val maxTotal = Parameter.RIDER_WEIGHT.range.endInclusive + Parameter.BIKE_WEIGHT.range.endInclusive
        assertTrue(maxTotal / 0.005 <= 65535)
    }
}
