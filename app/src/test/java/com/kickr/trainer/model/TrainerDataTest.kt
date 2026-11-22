package com.kickr.trainer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainerDataTest {

    @Test
    fun distanceFollowsTheSpeedUntilTheNextMessage() {
        // 36 km/h is 10 m/s; the speed of the next message only counts from then on
        val riding = TrainerData(speed = 36f, timestamp = 1_000)
        assertEquals(10.0, riding.distanceUntil(TrainerData(speed = 0f, timestamp = 2_000)), 1e-9)
        assertEquals(15.0, riding.distanceUntil(TrainerData(speed = 72f, timestamp = 2_500)), 1e-9)
        assertEquals(0.0, TrainerData(speed = 0f, timestamp = 0).distanceUntil(riding), 0.0)
    }

    @Test
    fun glitchesAndClockStepsAddNothing() {
        assertEquals(0.0, TrainerData(speed = 100f).plausibleSpeedMps, 0.0)
        assertEquals(27.5, TrainerData(speed = 99f).plausibleSpeedMps, 1e-9)
        val next = TrainerData(timestamp = 1_000)
        assertEquals(0.0, TrainerData(speed = 150f, timestamp = 0).distanceUntil(next), 0.0)
        assertEquals(0.0, TrainerData(speed = 36f, timestamp = 2_000).distanceUntil(next), 0.0)
    }
}
