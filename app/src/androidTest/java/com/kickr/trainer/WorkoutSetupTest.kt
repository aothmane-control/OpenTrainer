package com.kickr.trainer

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.kickr.trainer.bluetooth.ResistanceRange
import com.kickr.trainer.bluetooth.Trainer
import com.kickr.trainer.utils.SavedTrainerStorage
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

    @Before
    fun saveUserTrainers() {
        previousTrainers = preferences.getString("trainers", null)
        previousLastConnected = preferences.getString("last_connected", null)
        preferences.edit().remove("trainers").commit()
        // The trainer saved last is the one that connected last
        SavedTrainerStorage(context).apply {
            save(Trainer("AA:BB:CC:DD:EE:01", "KICKR CORE 1234", ResistanceRange(0, 100, 1)))
            save(Trainer("AA:BB:CC:DD:EE:02", "Another trainer", ResistanceRange(10, 240, 10)))
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

    // A complete one-minute workout at level 5
    private fun WorkoutSetupActivity.enterCompleteWorkout() {
        findViewById<EditText>(R.id.totalDurationEditText).setText("1")
        findViewById<Button>(R.id.setDurationButton).performClick()
        findViewById<EditText>(R.id.intervalDurationEditText).setText("1")
        findViewById<EditText>(R.id.intervalResistanceEditText).setText("5")
        findViewById<Button>(R.id.addIntervalButton).performClick()
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
