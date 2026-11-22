package com.kickr.trainer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class VirtualSpeedTest {

    // 80 kg in total, Crr 0.004, Cw 0.2 kg/m, no wind
    private val settings = RiderBikeSettings(
        riderWeightKg = 71.0, bikeWeightKg = 9.0, rollingResistance = 0.004, windResistance = 0.2
    )

    private fun ride(power: Double, grade: Double, seconds: Int, from: Double = 0.0) =
        VirtualSpeed.advance(from, power, grade, settings, seconds)

    @Test
    fun steadyStateOnTheFlatMatchesTheRoadLoad() {
        // Root of P = v (m g Crr + ½ Cw v²) by bisection
        var low = 0.0
        var high = 30.0
        repeat(60) {
            val v = (low + high) / 2
            if (v * (80 * 9.81 * 0.004 + 0.5 * 0.2 * v * v) < 200) low = v else high = v
        }
        assertEquals(11.77, low, 0.01)
        assertEquals(low, ride(200.0, 0.0, 300), low * 0.01)
    }

    @Test
    fun coastingDownhillSpeedsUpTowardsTerminalVelocity() {
        val theta = atan(-0.06)
        val terminal = sqrt(80 * 9.81 * (-sin(theta) - 0.004 * cos(theta)) / (0.5 * 0.2))
        val after10s = ride(0.0, -6.0, 10, from = 5.0)
        val after300s = ride(0.0, -6.0, 300, from = 5.0)
        assertTrue(after10s > 5.0)
        assertTrue(after300s > after10s)
        assertEquals(terminal, after300s, terminal * 0.01)
    }

    @Test
    fun theBikeStopsWithoutPowerButDoesNotRollBack() {
        // From 8 m/s it takes about 140 s to roll to a stop
        assertEquals(0.0, ride(0.0, 0.0, 180, from = 8.0), 0.0)
        assertEquals(0.0, ride(0.0, 8.0, 60, from = 3.0), 0.0)
    }

    @Test
    fun theLowestWindResistanceStillLimitsTheSpeedOnDescents() {
        val slippery = settings.copy(windResistance = RiderBikeSettings.Parameter.WIND_RESISTANCE.range.start)
        val theta = atan(-0.15)
        val terminal = sqrt(80 * 9.81 * (-sin(theta) - 0.004 * cos(theta)) / (0.5 * slippery.windResistance))
        // About 170 km/h, reached and kept however long the descent lasts
        assertEquals(terminal, VirtualSpeed.advance(0.0, 0.0, -15.0, slippery, 600), terminal * 0.01)
        assertEquals(terminal, VirtualSpeed.advance(0.0, 0.0, -15.0, slippery, 3600), terminal * 0.01)
    }

    @Test
    fun headwindSlowsTheRider() {
        val headwind = settings.copy(windSpeedMps = 5.0)
        assertTrue(VirtualSpeed.advance(0.0, 200.0, 0.0, headwind, 300) < ride(200.0, 0.0, 300))
    }
}
