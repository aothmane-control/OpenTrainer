package com.kickr.trainer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutTest {
    private val workout = Workout(600, listOf(
        WorkoutInterval(120, 20), WorkoutInterval(300, 40), WorkoutInterval(180, 60)
    ))

    @Test
    fun intervalRemainingTimeCountsDownWithinEachInterval() {
        assertEquals(120, workout.getIntervalRemainingTime(0))
        assertEquals(1, workout.getIntervalRemainingTime(119))
        assertEquals(300, workout.getIntervalRemainingTime(120))
        assertEquals(46, workout.getIntervalRemainingTime(374))
        assertEquals(1, workout.getIntervalRemainingTime(599))
    }

    @Test
    fun intervalRemainingTimeIsZeroAfterTheWorkout() {
        assertEquals(0, workout.getIntervalRemainingTime(600))
        assertEquals(0, workout.getIntervalRemainingTime(1000))
    }
}
