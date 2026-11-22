package com.kickr.trainer

import android.view.View
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutLauncherTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("workout_prefs", 0)
    private val trainerPreferences = context.getSharedPreferences("saved_trainers", 0)
    private var previousWorkouts: String? = null
    private var previousLastStarted: String? = null
    private var previousTrainers: String? = null
    private var previousLastConnected: String? = null
    private val complete = """{"totalDurationSeconds":60,"intervals":[{"duration":60,"resistance":25}]}"""

    @Before
    fun saveUserWorkouts() {
        previousWorkouts = preferences.getString("workouts", null)
        previousLastStarted = preferences.getString("last_started_workout", null)
        previousTrainers = trainerPreferences.getString("trainers", null)
        previousLastConnected = trainerPreferences.getString("last_connected", null)
        // No trainer has connected yet unless a test saves one
        trainerPreferences.edit().remove("trainers").remove("last_connected").commit()
    }

    @After
    fun restoreUserWorkouts() {
        preferences.edit().putString("workouts", previousWorkouts)
            .putString("last_started_workout", previousLastStarted).commit()
        trainerPreferences.edit().putString("trainers", previousTrainers)
            .putString("last_connected", previousLastConnected).commit()
    }

    @Test
    fun restoresLastStartedOrFallsBackToFirstAlphabeticallyWithoutWritingSelection() {
        preferences.edit().putString("workouts", """{"Zeta":$complete,"Alpha":$complete}""").commit()
        for (remembered in listOf("Zeta", "Deleted", null)) {
            preferences.edit().putString("last_started_workout", remembered).commit()
            val before = preferences.all
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val selector = activity.findViewById<MaterialAutoCompleteTextView>(R.id.savedWorkoutSelector)
                    assertEquals(if (remembered == "Zeta") "Zeta" else "Alpha", selector.text.toString())
                    assertEquals(listOf("Alpha", "Zeta"), (0 until selector.adapter.count).map { selector.adapter.getItem(it) })
                    assertTrue(activity.findViewById<Button>(R.id.startSavedWorkoutButton).isEnabled)
                    assertEquals("Disconnected launcher stays hidden without a saved trainer", View.GONE,
                        activity.findViewById<View>(R.id.workoutLauncher).visibility)
                }
            }
            assertEquals("Merely loading a selection must not remember it", before, preferences.all)
        }
    }

    @Test
    fun emptyOrIncompleteLibraryKeepsCreateEnabledAndPlayDisabled() {
        for (library in listOf<String?>(null,
            """{"Partial":{"totalDurationSeconds":120,"intervals":[{"duration":60,"resistance":25}]}}""")) {
            preferences.edit().putString("workouts", library).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val selector = activity.findViewById<MaterialAutoCompleteTextView>(R.id.savedWorkoutSelector)
                    assertEquals(activity.getString(R.string.no_workouts_available), selector.text.toString())
                    assertFalse(selector.isEnabled)
                    assertFalse(activity.findViewById<Button>(R.id.startSavedWorkoutButton).isEnabled)
                    val create = activity.findViewById<Button>(R.id.setupWorkoutButton)
                    assertTrue(create.isEnabled)
                    assertEquals(activity.getString(R.string.create_workout), create.contentDescription)
                }
            }
        }
    }

    @Test
    fun withASavedTrainerWorkoutsCanBeSetUpButNotStartedWhileDisconnected() {
        preferences.edit().putString("workouts", """{"Zeta":$complete}""").commit()
        trainerPreferences.edit().putString("trainers",
            """{"AA:BB:CC:DD:EE:01":{"name":"KICKR CORE 1234","minimum":0,"maximum":100,"increment":1}}""")
            .commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.workoutLauncher).visibility)
                assertEquals("Zeta", activity.findViewById<MaterialAutoCompleteTextView>(R.id.savedWorkoutSelector).text.toString())
                assertEquals(View.VISIBLE, activity.findViewById<Button>(R.id.setupWorkoutButton).visibility)
                assertEquals("Nothing can be started without a trainer", View.GONE,
                    activity.findViewById<Button>(R.id.startSavedWorkoutButton).visibility)
                assertEquals(View.GONE, activity.findViewById<Button>(R.id.viewGpxButton).visibility)
            }
        }
    }

    @Test
    fun reloadsWorkoutsWhenReturningFromSetup() {
        preferences.edit().putString("workouts", """{"Zeta":$complete}""")
            .putString("last_started_workout", "Zeta").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            preferences.edit().putString("workouts", """{"New":$complete}""").commit()
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertEquals("New", activity.findViewById<MaterialAutoCompleteTextView>(R.id.savedWorkoutSelector).text.toString())
            }
        }
        assertEquals("Zeta", preferences.getString("last_started_workout", null))
    }
}
