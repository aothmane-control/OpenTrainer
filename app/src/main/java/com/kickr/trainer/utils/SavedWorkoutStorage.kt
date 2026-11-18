package com.kickr.trainer.utils

import android.content.Context
import android.util.Log
import com.kickr.trainer.model.Workout
import com.kickr.trainer.model.WorkoutInterval
import com.kickr.trainer.model.WorkoutType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SavedWorkoutStorage(context: Context) {
    private val preferences = context.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)

    val lastStartedName: String?
        get() = preferences.getString("last_started_workout", null)

    fun names(): List<String> = readWorkouts().keys().asSequence().toList().sorted()

    fun load(name: String): Workout = parseWorkout(readWorkouts().getJSONObject(name))

    fun save(name: String, workout: Workout) {
        require(name.isNotBlank()) { "Please enter a workout name" }
        require(workout.intervals.isNotEmpty()) { "No intervals to save" }
        val workoutJson = JSONObject()
        workoutJson.put("totalDurationSeconds", workout.totalDuration)
        workoutJson.put("type", workout.type.name)
        val intervals = JSONArray()
        for (interval in workout.intervals) {
            intervals.put(JSONObject().apply {
                put("duration", interval.duration)
                put("resistance", interval.resistance)
                put("power", interval.power)
            })
        }
        workoutJson.put("intervals", intervals)

        val workouts = readWorkouts()
        workouts.put(name, workoutJson)
        preferences.edit().putString("workouts", workouts.toString()).apply()
    }

    fun delete(name: String) {
        val workouts = readWorkouts()
        if (!workouts.has(name)) return
        workouts.remove(name)
        preferences.edit().putString("workouts", workouts.toString()).apply()
    }

    fun loadStartable(): Map<String, Workout> {
        val workoutsJson = try {
            readWorkouts()
        } catch (e: JSONException) {
            Log.w("SavedWorkoutStorage", "Cannot read saved workouts", e)
            return emptyMap()
        }
        val workouts = linkedMapOf<String, Workout>()
        for (name in workoutsJson.keys().asSequence().toList().sorted()) {
            if (name.isBlank()) continue
            try {
                val workout = parseWorkout(workoutsJson.getJSONObject(name))
                if (workout.isValid()) workouts[name] = workout
            } catch (e: JSONException) {
                Log.w("SavedWorkoutStorage", "Skipping unreadable workout: $name", e)
            }
        }
        return workouts
    }

    fun rememberStarted(name: String?, workout: Workout): Boolean {
        // An unsaved or edited setup result must not replace the last saved workout used.
        if (name == null || loadStartable()[name] != workout) return false
        preferences.edit().putString("last_started_workout", name).apply()
        return true
    }

    private fun readWorkouts(): JSONObject =
        JSONObject(preferences.getString("workouts", null) ?: "{}")

    private fun parseWorkout(json: JSONObject): Workout {
        val type = WorkoutType.entries.find { it.name == json.optString("type") }
            ?: WorkoutType.RESISTANCE
        val intervals = json.getJSONArray("intervals")
        return Workout(
            json.getInt("totalDurationSeconds"),
            (0 until intervals.length()).map { index ->
                val interval = intervals.getJSONObject(index)
                WorkoutInterval(interval.getInt("duration"),
                    interval.optInt("resistance", 0), interval.optInt("power", 0))
            },
            type
        )
    }
}
