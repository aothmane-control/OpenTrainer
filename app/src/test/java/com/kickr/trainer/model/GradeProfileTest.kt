package com.kickr.trainer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GradeProfileTest {

    @Test
    fun shortRoutesShowTheWholeRoute() {
        assertEquals(0.0..4007.6, GradeProfile.visibleRange(0.0, 4007.6))
        assertEquals(0.0..4007.6, GradeProfile.visibleRange(3500.0, 4007.6))
        assertEquals(0.0..GradeProfile.WINDOW_M, GradeProfile.visibleRange(5000.0, GradeProfile.WINDOW_M))
    }

    @Test
    fun longRoutesShowAWindowThatFollowsTheRider() {
        // 2 km behind, 8 km ahead
        assertEquals(3000.0..13000.0, GradeProfile.visibleRange(5000.0, 50_000.0))
    }

    @Test
    fun theWindowStaysWithinTheRoute() {
        assertEquals(0.0..10_000.0, GradeProfile.visibleRange(500.0, 50_000.0))
        assertEquals(40_000.0..50_000.0, GradeProfile.visibleRange(49_000.0, 50_000.0))
    }
}
