package com.kickr.trainer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.kickr.trainer.adapter.IntervalAdapter
import com.kickr.trainer.model.Workout
import com.kickr.trainer.model.WorkoutInterval
import org.json.JSONArray
import org.json.JSONObject

class WorkoutSetupActivity : AppCompatActivity() {

    private lateinit var workoutNameEditText: TextInputEditText
    private lateinit var totalDurationEditText: TextInputEditText
    private lateinit var setDurationButton: Button
    private lateinit var intervalsCard: MaterialCardView
    private lateinit var remainingTimeTextView: TextView
    private lateinit var intervalsRecyclerView: RecyclerView
    private lateinit var intervalDurationEditText: TextInputEditText
    private lateinit var intervalResistanceEditText: TextInputEditText
    private lateinit var addIntervalButton: Button
    private lateinit var startWorkoutButton: Button
    private lateinit var cancelButton: Button
    private lateinit var saveWorkoutButton: Button
    private lateinit var loadWorkoutButton: Button

    private lateinit var intervalAdapter: IntervalAdapter
    private val intervals = mutableListOf<WorkoutInterval>()
    private var totalDurationSeconds = 0
    
    private val prefs by lazy {
        getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_setup)

        initViews()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun initViews() {
        workoutNameEditText = findViewById(R.id.workoutNameEditText)
        totalDurationEditText = findViewById(R.id.totalDurationEditText)
        setDurationButton = findViewById(R.id.setDurationButton)
        intervalsCard = findViewById(R.id.intervalsCard)
        remainingTimeTextView = findViewById(R.id.remainingTimeTextView)
        intervalsRecyclerView = findViewById(R.id.intervalsRecyclerView)
        intervalDurationEditText = findViewById(R.id.intervalDurationEditText)
        intervalResistanceEditText = findViewById(R.id.intervalResistanceEditText)
        addIntervalButton = findViewById(R.id.addIntervalButton)
        startWorkoutButton = findViewById(R.id.startWorkoutButton)
        cancelButton = findViewById(R.id.cancelButton)
        saveWorkoutButton = findViewById(R.id.saveWorkoutButton)
        loadWorkoutButton = findViewById(R.id.loadWorkoutButton)
    }

    private fun setupRecyclerView() {
        intervalAdapter = IntervalAdapter { position ->
            deleteInterval(position)
        }
        
        intervalsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@WorkoutSetupActivity)
            adapter = intervalAdapter
        }
    }

    private fun setupClickListeners() {
        setDurationButton.setOnClickListener {
            val minutes = totalDurationEditText.text.toString().toIntOrNull()
            if (minutes == null || minutes <= 0) {
                Toast.makeText(this, "Please enter a valid duration", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            totalDurationSeconds = minutes * 60
            intervalsCard.visibility = View.VISIBLE
            totalDurationEditText.isEnabled = false
            setDurationButton.isEnabled = false
            updateRemainingTime()
        }

        addIntervalButton.setOnClickListener {
            addInterval()
        }

        startWorkoutButton.setOnClickListener {
            startWorkout()
        }

        cancelButton.setOnClickListener {
            finish()
        }
        
        saveWorkoutButton.setOnClickListener {
            saveWorkout()
        }
        
        loadWorkoutButton.setOnClickListener {
            loadWorkout()
        }
    }

    private fun addInterval() {
        val durationMinutes = intervalDurationEditText.text.toString().toDoubleOrNull()
        val resistance = intervalResistanceEditText.text.toString().toIntOrNull()

        if (durationMinutes == null || durationMinutes <= 0) {
            Toast.makeText(this, "Please enter a valid duration", Toast.LENGTH_SHORT).show()
            return
        }

        if (resistance == null || resistance !in 0..100) {
            Toast.makeText(this, "Resistance must be between 0 and 100%", Toast.LENGTH_SHORT).show()
            return
        }

        val durationSeconds = (durationMinutes * 60).toInt()
        val currentTotal = intervals.sumOf { it.duration }
        if (currentTotal + durationSeconds > totalDurationSeconds) {
            val remainingSeconds = totalDurationSeconds - currentTotal
            val remainingMinutes = remainingSeconds / 60.0
            Toast.makeText(
                this,
                "Duration too long. Only %.1f minutes remaining".format(remainingMinutes),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        intervals.add(WorkoutInterval(durationSeconds, resistance))
        intervalAdapter.updateIntervals(intervals)
        
        intervalDurationEditText.text?.clear()
        intervalResistanceEditText.text?.clear()
        
        updateRemainingTime()
        checkWorkoutComplete()
    }

    private fun deleteInterval(position: Int) {
        intervals.removeAt(position)
        intervalAdapter.updateIntervals(intervals)
        updateRemainingTime()
        checkWorkoutComplete()
    }

    private fun updateRemainingTime() {
        val used = intervals.sumOf { it.duration }
        val remaining = totalDurationSeconds - used
        val minutes = remaining / 60
        val seconds = remaining % 60
        remainingTimeTextView.text = getString(R.string.remaining_time_format, minutes, seconds)
    }

    private fun checkWorkoutComplete() {
        val currentTotal = intervals.sumOf { it.duration }
        val isComplete = currentTotal == totalDurationSeconds && intervals.isNotEmpty()
        val hasRemainingTime = currentTotal < totalDurationSeconds
        
        startWorkoutButton.visibility = if (isComplete) View.VISIBLE else View.GONE
        startWorkoutButton.isEnabled = isComplete
        
        // Disable and hide Add Interval UI when workout duration is fully allocated
        addIntervalButton.isEnabled = hasRemainingTime
        addIntervalButton.alpha = if (hasRemainingTime) 1.0f else 0.5f
        
        intervalDurationEditText.isEnabled = hasRemainingTime
        intervalResistanceEditText.isEnabled = hasRemainingTime
    }

    private fun startWorkout() {
        val workout = Workout(totalDurationSeconds, intervals.toList())
        if (!workout.isValid()) {
            Toast.makeText(this, "Invalid workout configuration", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent()
        intent.putExtra("workout_duration", workout.totalDuration)
        
        // Convert intervals to arrays for Intent
        val durations = workout.intervals.map { it.duration }.toIntArray()
        val resistances = workout.intervals.map { it.resistance }.toIntArray()
        intent.putExtra("workout_interval_durations", durations)
        intent.putExtra("workout_interval_resistances", resistances)
        
        setResult(RESULT_OK, intent)
        finish()
    }
    
    private fun saveWorkout() {
        if (intervals.isEmpty()) {
            Toast.makeText(this, "No intervals to save", Toast.LENGTH_SHORT).show()
            return
        }
        
        val workoutName = workoutNameEditText.text.toString().trim()
        if (workoutName.isEmpty()) {
            Toast.makeText(this, "Please enter a workout name", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create workout JSON
        val workoutJson = JSONObject()
        workoutJson.put("totalDurationSeconds", totalDurationSeconds)
        
        val intervalsArray = JSONArray()
        intervals.forEach { interval ->
            val intervalJson = JSONObject()
            intervalJson.put("duration", interval.duration)
            intervalJson.put("resistance", interval.resistance)
            intervalsArray.put(intervalJson)
        }
        workoutJson.put("intervals", intervalsArray)
        
        // Get existing workouts
        val workoutsJson = prefs.getString("workouts", null)
        val workoutsMap = if (workoutsJson != null) {
            JSONObject(workoutsJson)
        } else {
            JSONObject()
        }
        
        // Add or update workout
        workoutsMap.put(workoutName, workoutJson)
        
        // Save back to preferences
        prefs.edit().putString("workouts", workoutsMap.toString()).apply()
        Toast.makeText(this, "Workout '$workoutName' saved!", Toast.LENGTH_SHORT).show()
    }
    
    private fun loadWorkout() {
        val workoutsJson = prefs.getString("workouts", null)
        if (workoutsJson == null) {
            Toast.makeText(this, "No saved workouts found", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val workoutsMap = JSONObject(workoutsJson)
            val workoutNames = workoutsMap.keys().asSequence().toList().sorted()
            
            if (workoutNames.isEmpty()) {
                Toast.makeText(this, "No saved workouts found", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Show selection dialog
            AlertDialog.Builder(this)
                .setTitle("Select Workout")
                .setItems(workoutNames.toTypedArray()) { _, which ->
                    val selectedName = workoutNames[which]
                    loadWorkoutByName(selectedName, workoutsMap.getJSONObject(selectedName))
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load workouts: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadWorkoutByName(name: String, json: JSONObject) {
        try {
            // Set workout name
            workoutNameEditText.setText(name)
            
            totalDurationSeconds = json.getInt("totalDurationSeconds")
            
            // Set duration in UI
            val durationMinutes = totalDurationSeconds / 60
            totalDurationEditText.setText(durationMinutes.toString())
            totalDurationEditText.isEnabled = false
            setDurationButton.isEnabled = false
            intervalsCard.visibility = View.VISIBLE
            
            // Load intervals
            intervals.clear()
            val intervalsArray = json.getJSONArray("intervals")
            for (i in 0 until intervalsArray.length()) {
                val intervalJson = intervalsArray.getJSONObject(i)
                val duration = intervalJson.getInt("duration")
                val resistance = intervalJson.getInt("resistance")
                intervals.add(WorkoutInterval(duration, resistance))
            }
            
            intervalAdapter.updateIntervals(intervals)
            updateRemainingTime()
            checkWorkoutComplete()
            
            Toast.makeText(this, "Workout '$name' loaded!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load workout: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
