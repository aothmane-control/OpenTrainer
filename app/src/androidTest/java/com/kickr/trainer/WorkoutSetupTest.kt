package com.kickr.trainer

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.kickr.trainer.bluetooth.ResistanceRange
import com.kickr.trainer.model.Workout
import com.kickr.trainer.model.WorkoutInterval
import com.kickr.trainer.bluetooth.Trainer
import com.kickr.trainer.utils.SavedTrainerStorage
import com.kickr.trainer.utils.SavedWorkoutStorage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutSetupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("saved_trainers", 0)
    private var previousTrainers: String? = null
    private var previousLastConnected: String? = null
    private val kickr = Trainer("AA:BB:CC:DD:EE:01", "KICKR CORE 1234", ResistanceRange(0, 100, 1))
    private val other = Trainer("AA:BB:CC:DD:EE:02", "Another trainer", ResistanceRange(10, 240, 10))

    @Before
    fun saveUserTrainers() {
        previousTrainers = preferences.getString("trainers", null)
        previousLastConnected = preferences.getString("last_connected", null)
        preferences.edit().remove("trainers").commit()
        // The trainer saved last is the one that connected last
        SavedTrainerStorage(context).apply {
            save(kickr)
            save(other)
        }
    }

    @After
    fun restoreUserTrainers() {
        preferences.edit().putString("trainers", previousTrainers)
            .putString("last_connected", previousLastConnected).commit()
    }

    private fun launch(connected: Boolean): ActivityScenario<WorkoutSetupActivity> {
        val intent = Intent(context, WorkoutSetupActivity::class.java)
        if (connected) {
            intent.putExtra("min_resistance_level", 0).putExtra("max_resistance_level", 100)
        } else {
            intent.putExtra("trainer_connected", false)
        }
        return ActivityScenario.launch(intent)
    }

    // A complete one-minute workout at one level
    private fun WorkoutSetupActivity.enterCompleteWorkout(level: Int = 5) {
        findViewById<EditText>(R.id.totalDurationEditText).setText("1")
        findViewById<Button>(R.id.setDurationButton).performClick()
        findViewById<EditText>(R.id.intervalDurationEditText).setText("1")
        findViewById<EditText>(R.id.intervalResistanceEditText).setText("$level")
        findViewById<Button>(R.id.addIntervalButton).performClick()
    }

    // Picks a saved trainer as the user does in the dropdown, which lists them by name
    private fun WorkoutSetupActivity.selectTrainer(trainer: Trainer) {
        val selector = findViewById<MaterialAutoCompleteTextView>(R.id.trainerSelector)
        selector.onItemClickListener.onItemClick(null, null, listOf(other, kickr).indexOf(trainer), 0)
    }

    @Test
    fun loadingAndDeletingAreSeparateActions() {
        // Unique, so a failing test can't touch the user's own workouts; removed again below
        val name = "WorkoutSetupTest load and delete"
        val workouts = SavedWorkoutStorage(context)
        workouts.save(name, Workout(60, listOf(WorkoutInterval(60, 5))))
        try {
            launch(connected = true).use { scenario ->
                // Picking a workout to load loads it, without asking what to do with it
                scenario.onActivity { it.findViewById<Button>(R.id.loadWorkoutButton).performClick() }
                onView(withText(name)).inRoot(isDialog()).perform(click())
                scenario.onActivity { activity ->
                    assertEquals(name, activity.findViewById<EditText>(R.id.workoutNameEditText).text.toString())
                }
                assertTrue(name in workouts.names())

                scenario.onActivity { it.findViewById<Button>(R.id.deleteWorkoutButton).performClick() }
                onView(withText(name)).inRoot(isDialog()).perform(click())
                assertTrue("Deleting asks first", name in workouts.names())
                onView(withText("Delete")).inRoot(isDialog()).perform(click())
                assertFalse(name in workouts.names())
            }
        } finally {
            workouts.delete(name)
        }
    }

    @Test
    fun intervalsOutsideTheSelectedTrainersLevelsAreNotSaved() {
        // Unique, so a failing test can't touch the user's own workouts; removed again below
        val name = "WorkoutSetupTest out of range"
        val workouts = SavedWorkoutStorage(context)
        SavedTrainerStorage(context).save(kickr)
        try {
            launch(connected = false).use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.workoutNameEditText).setText(name)
                    activity.enterCompleteWorkout(level = 80)
                    activity.selectTrainer(other)
                    activity.findViewById<Button>(R.id.saveWorkoutButton).performClick()
                    assertFalse("Level 80 is beyond levels 1-24", name in workouts.names())

                    activity.selectTrainer(kickr)
                    activity.findViewById<Button>(R.id.saveWorkoutButton).performClick()
                    assertTrue(name in workouts.names())
                }
            }
        } finally {
            workouts.delete(name)
        }
    }

    @Test
    fun withoutAConnectionResistanceLevelsAreThoseOfTheLastConnectedTrainer() {
        launch(connected = false).use { scenario ->
            scenario.onActivity { activity ->
                val trainerLayout = activity.findViewById<TextInputLayout>(R.id.trainerLayout)
                assertEquals(View.VISIBLE, trainerLayout.visibility)
                assertEquals("Another trainer",
                    activity.findViewById<MaterialAutoCompleteTextView>(R.id.trainerSelector).text.toString())
                assertEquals(activity.getString(R.string.resistance_percent_hint, 1, 24),
                    activity.findViewById<TextInputLayout>(R.id.intervalValueLayout).hint)

                activity.findViewById<View>(R.id.powerRadioButton).performClick()
                assertEquals("Power workouts don't depend on the trainer", View.GONE, trainerLayout.visibility)
            }
        }
    }

    @Test
    fun aCompleteWorkoutCanOnlyBeStartedWithAConnectedTrainer() {
        for (connected in listOf(true, false)) {
            launch(connected).use { scenario ->
                scenario.onActivity { activity ->
                    activity.enterCompleteWorkout()
                    assertEquals(if (connected) View.VISIBLE else View.GONE,
                        activity.findViewById<Button>(R.id.startWorkoutButton).visibility)
                    assertTrue(activity.findViewById<Button>(R.id.saveWorkoutButton).isEnabled)
                }
            }
        }
    }

    @Test
    fun aConnectedTrainerNeedsNoTrainerSelection() {
        launch(connected = true).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.trainerLayout).visibility)
                assertEquals(activity.getString(R.string.resistance_percent_hint, 0, 100),
                    activity.findViewById<TextInputLayout>(R.id.intervalValueLayout).hint)
            }
        }
    }
}
