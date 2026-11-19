package com.kickr.trainer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTest {
    private val workout = Workout(600, listOf(
        WorkoutInterval(120, 20), WorkoutInterval(300, 40), WorkoutInterval(180, 60)
    ))

    @Test
    fun onlyCompletePositiveDurationWorkoutsAreValid() {
        assertTrue(workout.isValid())
        assertFalse(Workout(0, emptyList()).isValid())
        assertFalse(Workout(60, emptyList()).isValid())
        assertFalse(Workout(60, listOf(WorkoutInterval(0, 20), WorkoutInterval(60, 20))).isValid())
        assertFalse(Workout(60, listOf(WorkoutInterval(-1, 20), WorkoutInterval(61, 20))).isValid())
        assertFalse(Workout(60, listOf(WorkoutInterval(30, 20))).isValid())
        assertFalse(Workout(1, listOf(WorkoutInterval(Int.MAX_VALUE, 20),
            WorkoutInterval(Int.MAX_VALUE, 20), WorkoutInterval(3, 20))).isValid())
    }

    @Test
    fun validatesTargetsForTheSelectedWorkoutType() {
        assertFalse(Workout(60, listOf(WorkoutInterval(60, -1))).isValid())
        assertTrue("Trainers can have more than 100 levels", Workout(60, listOf(WorkoutInterval(60, 150))).isValid())
        assertFalse(Workout(60, listOf(WorkoutInterval(60, 0, 0)), WorkoutType.POWER).isValid())
        assertFalse(Workout(60, listOf(WorkoutInterval(60, 0, 1001)), WorkoutType.POWER).isValid())
        assertTrue(Workout(60, listOf(WorkoutInterval(60, 0, 200)), WorkoutType.POWER).isValid())
    }

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
