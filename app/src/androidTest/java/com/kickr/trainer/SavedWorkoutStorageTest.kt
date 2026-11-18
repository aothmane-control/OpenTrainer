package com.kickr.trainer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kickr.trainer.model.Workout
import com.kickr.trainer.model.WorkoutInterval
import com.kickr.trainer.model.WorkoutType
import com.kickr.trainer.utils.SavedWorkoutStorage
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedWorkoutStorageTest {
    // Test APK's own preferences, never the user's saved workouts.
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val preferences = context.getSharedPreferences("workout_prefs", 0)
    private val storage = SavedWorkoutStorage(context)
    private val definition = """{"totalDurationSeconds":60,"intervals":[{"duration":60,"resistance":25}]}"""

    @Before
    fun setUp() { preferences.edit().clear().commit() }

    @After
    fun tearDown() { preferences.edit().clear().commit() }

    @Test
    fun listsOnlyStartableWorkoutsInAlphabeticalOrder() {
        preferences.edit().putString("workouts", """{
            "Zeta":$definition,
            "Alpha":$definition,
            "Incomplete":{"totalDurationSeconds":120,"intervals":[{"duration":60,"resistance":25}]},
            "Empty":{"totalDurationSeconds":0,"intervals":[]},
            "Bad target":{"totalDurationSeconds":60,"intervals":[{"duration":60,"resistance":101}]},
            "Broken":{"intervals":[]},
            "Wrong shape":false
        }""").commit()
        val workouts = storage.loadStartable()
        assertEquals(listOf("Alpha", "Zeta"), workouts.keys.toList())
        assertEquals(Workout(60, listOf(WorkoutInterval(60, 25))), workouts["Alpha"])
    }

    @Test
    fun supportsPowerAndLegacyTypeFallback() {
        val definitions = JSONObject()
            .put("Power", JSONObject("""{"type":"POWER","totalDurationSeconds":60,"intervals":[{"duration":60,"power":200}]}"""))
            .put("Legacy", JSONObject(definition))
            .put("Unknown type", JSONObject(definition).put("type", "UNKNOWN"))
        preferences.edit().putString("workouts", definitions.toString()).commit()
        val workouts = storage.loadStartable()
        assertEquals(WorkoutType.POWER, workouts["Power"]!!.type)
        assertEquals(200, workouts["Power"]!!.intervals.single().power)
        assertEquals(storage.load("Legacy"), workouts["Legacy"])
        assertEquals(WorkoutType.RESISTANCE, workouts["Legacy"]!!.type)
        assertEquals(WorkoutType.RESISTANCE, workouts["Unknown type"]!!.type)
    }

    @Test
    fun malformedLibraryIsNotOverwritten() {
        preferences.edit().putString("workouts", "not json").commit()
        assertTrue(storage.loadStartable().isEmpty())
        val workout = Workout(60, listOf(WorkoutInterval(60, 25)))
        assertThrows(JSONException::class.java) { storage.save("New", workout) }
        assertThrows(JSONException::class.java) { storage.delete("Saved") }
        assertEquals("not json", preferences.getString("workouts", null))
    }

    @Test
    fun savesLoadsOverwritesAndDeletesWithoutChangingOtherWorkouts() {
        val resistance = Workout(60, listOf(WorkoutInterval(60, 25)))
        val power = Workout(120, listOf(WorkoutInterval(60, 0, 200),
            WorkoutInterval(60, 0, 250)), WorkoutType.POWER)
        storage.save("Zeta", resistance)
        storage.save("Alpha", power)
        assertEquals(listOf("Alpha", "Zeta"), storage.names())
        assertEquals(resistance, storage.load("Zeta"))
        assertEquals(power, storage.load("Alpha"))

        val updated = resistance.copy(intervals = listOf(WorkoutInterval(60, 50)))
        storage.save("Zeta", updated)
        assertEquals(updated, storage.load("Zeta"))
        assertEquals(power, storage.load("Alpha"))

        storage.delete("Alpha")
        storage.delete("Missing")
        assertEquals(listOf("Zeta"), storage.names())
        assertEquals(updated, storage.load("Zeta"))
    }

    @Test
    fun incompleteWorkoutsCanBeSavedAndLoadedButNotStarted() {
        val incomplete = Workout(120, listOf(WorkoutInterval(60, 25)))
        storage.save("Partial", incomplete)
        assertEquals(listOf("Partial"), storage.names())
        assertEquals(incomplete, storage.load("Partial"))
        assertTrue(storage.loadStartable().isEmpty())
    }

    @Test
    fun unreadableWorkoutsRemainAvailableToDeleteAndArePreservedWhenSavingAnother() {
        preferences.edit().putString("workouts", """{"Broken":false}""").commit()
        storage.save("Valid", Workout(60, listOf(WorkoutInterval(60, 25))))
        assertEquals(listOf("Broken", "Valid"), storage.names())
        assertThrows(JSONException::class.java) { storage.load("Broken") }
        assertEquals(listOf("Valid"), storage.loadStartable().keys.toList())
        storage.delete("Broken")
        assertEquals(listOf("Valid"), storage.names())
    }

    @Test
    fun rejectsBlankNamesAndEmptyWorkoutsWithoutWriting() {
        val workout = Workout(60, listOf(WorkoutInterval(60, 25)))
        assertThrows(IllegalArgumentException::class.java) { storage.save(" ", workout) }
        assertThrows(IllegalArgumentException::class.java) { storage.save("Empty", Workout(60, emptyList())) }
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun remembersOnlyAnActuallyStartedUnmodifiedSavedWorkout() {
        preferences.edit().putString("workouts", """{"Saved":$definition}""").commit()
        val workout = storage.loadStartable().getValue("Saved")
        assertNull(storage.lastStartedName)
        assertFalse(storage.rememberStarted(null, workout))
        assertFalse(storage.rememberStarted("Unsaved", workout))
        assertTrue(storage.rememberStarted("Saved", workout))
        assertEquals("Saved", SavedWorkoutStorage(context).lastStartedName)
        assertFalse(storage.rememberStarted("Saved", workout.copy(intervals = listOf(WorkoutInterval(60, 50)))))
        assertEquals("Saved", storage.lastStartedName)
        assertEquals(listOf("Saved"), storage.loadStartable().keys.toList())
    }
}
