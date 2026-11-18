/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kickr.trainer.adapter.IntervalAdapter
import com.kickr.trainer.model.Workout
import com.kickr.trainer.model.WorkoutInterval
import com.kickr.trainer.model.WorkoutType
import com.kickr.trainer.utils.SavedWorkoutStorage
import com.kickr.trainer.utils.applyEdgeToEdgeInsets
import kotlin.math.roundToInt

class WorkoutSetupActivity : AppCompatActivity() {

    private lateinit var workoutNameEditText: TextInputEditText
    private lateinit var workoutTypeRadioGroup: RadioGroup
    private lateinit var totalDurationEditText: TextInputEditText
    private lateinit var setDurationButton: Button
    private lateinit var intervalsCard: MaterialCardView
    private lateinit var remainingTimeTextView: TextView
    private lateinit var intervalsRecyclerView: RecyclerView
    private lateinit var intervalDurationEditText: TextInputEditText
    private lateinit var intervalValueLayout: TextInputLayout
    private lateinit var intervalResistanceEditText: TextInputEditText
    private lateinit var addIntervalButton: Button
    private lateinit var startWorkoutButton: Button
    private lateinit var cancelButton: Button
    private lateinit var saveWorkoutButton: Button
    private lateinit var loadWorkoutButton: Button

    private lateinit var intervalAdapter: IntervalAdapter
    private val intervals = mutableListOf<WorkoutInterval>()
    private var totalDurationSeconds = 0
    private var workoutType = WorkoutType.RESISTANCE
    
    private val savedWorkoutStorage by lazy { SavedWorkoutStorage(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_setup)
        applyEdgeToEdgeInsets()

        initViews()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun initViews() {
        workoutNameEditText = findViewById(R.id.workoutNameEditText)
        workoutTypeRadioGroup = findViewById(R.id.workoutTypeRadioGroup)
        totalDurationEditText = findViewById(R.id.totalDurationEditText)
        setDurationButton = findViewById(R.id.setDurationButton)
        intervalsCard = findViewById(R.id.intervalsCard)
        remainingTimeTextView = findViewById(R.id.remainingTimeTextView)
        intervalsRecyclerView = findViewById(R.id.intervalsRecyclerView)
        intervalDurationEditText = findViewById(R.id.intervalDurationEditText)
        intervalValueLayout = findViewById(R.id.intervalValueLayout)
        intervalResistanceEditText = findViewById(R.id.intervalResistanceEditText)
        addIntervalButton = findViewById(R.id.addIntervalButton)
        startWorkoutButton = findViewById(R.id.startWorkoutButton)
        cancelButton = findViewById(R.id.cancelButton)
        saveWorkoutButton = findViewById(R.id.saveWorkoutButton)
        loadWorkoutButton = findViewById(R.id.loadWorkoutButton)
    }

    private fun setupRecyclerView() {
        intervalAdapter = IntervalAdapter(
            onDeleteClick = { position ->
                deleteInterval(position)
            },
            onEditClick = { position, interval ->
                editInterval(position, interval)
            },
            workoutType = workoutType
        )
        
        intervalsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@WorkoutSetupActivity)
            adapter = intervalAdapter
        }
    }

    private fun setupClickListeners() {
        workoutTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            workoutType = when (checkedId) {
                R.id.powerRadioButton -> WorkoutType.POWER
                else -> WorkoutType.RESISTANCE
            }
            updateIntervalInputHint()
            intervalAdapter.updateWorkoutType(workoutType)
        }
        
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
    
    private fun updateIntervalInputHint() {
        intervalValueLayout.hint = when (workoutType) {
            WorkoutType.RESISTANCE -> getString(R.string.resistance_percent_hint)
            WorkoutType.POWER -> getString(R.string.power_watts_hint)
        }
    }

    private fun addInterval() {
        val durationMinutes = intervalDurationEditText.text.toString().toDoubleOrNull()
        val value = intervalResistanceEditText.text.toString().toIntOrNull()

        if (durationMinutes == null || durationMinutes <= 0) {
            Toast.makeText(this, "Please enter a valid duration", Toast.LENGTH_SHORT).show()
            return
        }

        if (value == null || value <= 0) {
            val msg = when (workoutType) {
                WorkoutType.RESISTANCE -> "Resistance must be between 1 and 100%"
                WorkoutType.POWER -> "Power must be greater than 0 watts"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate range based on workout type
        val isValidValue = when (workoutType) {
            WorkoutType.RESISTANCE -> value in 1..100
            WorkoutType.POWER -> value in 1..1000
        }
        
        if (!isValidValue) {
            val msg = when (workoutType) {
                WorkoutType.RESISTANCE -> "Resistance must be between 1 and 100%"
                WorkoutType.POWER -> "Power must be between 1 and 1000 watts"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }

        val durationSeconds = (durationMinutes * 60).roundToInt()
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

        val interval = when (workoutType) {
            WorkoutType.RESISTANCE -> WorkoutInterval(durationSeconds, value, 0)
            WorkoutType.POWER -> WorkoutInterval(durationSeconds, 0, value)
        }
        
        intervals.add(interval)
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
    
    private fun editInterval(position: Int, interval: WorkoutInterval) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_interval, null)
        val durationEditText = dialogView.findViewById<TextInputEditText>(R.id.editIntervalDurationEditText)
        val valueEditText = dialogView.findViewById<TextInputEditText>(R.id.editIntervalValueEditText)
        val valueLayout = dialogView.findViewById<TextInputLayout>(R.id.editIntervalValueLayout)
        
        // Set hint based on workout type
        when (workoutType) {
            WorkoutType.RESISTANCE -> {
                valueLayout.hint = getString(R.string.resistance_percent_hint)
                valueEditText.setText(interval.resistance.toString())
            }
            WorkoutType.POWER -> {
                valueLayout.hint = getString(R.string.power_watts_hint)
                valueEditText.setText(interval.power.toString())
            }
        }
        
        // Pre-fill with current values
        durationEditText.setText((interval.duration / 60.0).toString())
        
        AlertDialog.Builder(this)
            .setTitle("Edit Interval ${position + 1}")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val durationMinutes = durationEditText.text.toString().toDoubleOrNull()
                val value = valueEditText.text.toString().toIntOrNull()
                
                if (durationMinutes == null || durationMinutes <= 0) {
                    Toast.makeText(this, "Please enter a valid duration", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Validate based on workout type
                val isValid = when (workoutType) {
                    WorkoutType.RESISTANCE -> value != null && value in 0..100
                    WorkoutType.POWER -> value != null && value in 1..1000
                }
                
                if (!isValid) {
                    val errorMsg = when (workoutType) {
                        WorkoutType.RESISTANCE -> "Resistance must be between 0 and 100"
                        WorkoutType.POWER -> "Power must be between 1 and 1000 watts"
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val newDurationSeconds = (durationMinutes * 60).roundToInt()
                
                // Check if new duration fits
                val currentTotal = intervals.sumOf { it.duration } - interval.duration
                if (currentTotal + newDurationSeconds > totalDurationSeconds) {
                    Toast.makeText(
                        this,
                        "Updated interval would exceed total workout duration",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                
                // Update the interval based on workout type
                val updatedInterval = when (workoutType) {
                    WorkoutType.RESISTANCE -> WorkoutInterval(newDurationSeconds, value!!, 0)
                    WorkoutType.POWER -> WorkoutInterval(newDurationSeconds, 0, value!!)
                }
                
                intervals[position] = updatedInterval
                intervalAdapter.updateIntervals(intervals)
                updateRemainingTime()
                checkWorkoutComplete()
                
                Toast.makeText(this, "Interval updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        val workout = Workout(totalDurationSeconds, intervals.toList(), workoutType)
        if (!workout.isValid()) {
            Toast.makeText(this, "Invalid workout configuration", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent()
        intent.putExtra("workout_duration", workout.totalDuration)
        intent.putExtra("workout_type", workoutType.name)
        intent.putExtra("workout_name", workoutNameEditText.text.toString().trim())
        
        // Convert intervals to arrays for Intent
        val durations = workout.intervals.map { it.duration }.toIntArray()
        val resistances = workout.intervals.map { it.resistance }.toIntArray()
        val powers = workout.intervals.map { it.power }.toIntArray()
        intent.putExtra("workout_interval_durations", durations)
        intent.putExtra("workout_interval_resistances", resistances)
        intent.putExtra("workout_interval_powers", powers)
        
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
        
        try {
            savedWorkoutStorage.save(workoutName, Workout(totalDurationSeconds, intervals.toList(), workoutType))
            Toast.makeText(this, "Workout '$workoutName' saved!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save workout: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadWorkout() {
        try {
            val workoutNames = savedWorkoutStorage.names()
            
            if (workoutNames.isEmpty()) {
                Toast.makeText(this, "No saved workouts found", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Show selection dialog with delete option
            AlertDialog.Builder(this)
                .setTitle("Select Workout")
                .setItems(workoutNames.toTypedArray()) { _, which ->
                    val selectedName = workoutNames[which]
                    showWorkoutOptionsDialog(selectedName)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load workouts: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showWorkoutOptionsDialog(name: String) {
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("Load", "Delete")) { _, which ->
                when (which) {
                    0 -> loadWorkoutByName(name)
                    1 -> confirmDeleteWorkout(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun confirmDeleteWorkout(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Workout")
            .setMessage("Are you sure you want to delete '$name'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteWorkout(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteWorkout(name: String) {
        try {
            savedWorkoutStorage.delete(name)
            Toast.makeText(this, "Workout '$name' deleted!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to delete workout: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadWorkoutByName(name: String) {
        try {
            val workout = savedWorkoutStorage.load(name)
            workoutNameEditText.setText(name)
            totalDurationSeconds = workout.totalDuration
            workoutType = workout.type
            
            // Update UI for workout type
            when (workoutType) {
                WorkoutType.RESISTANCE -> workoutTypeRadioGroup.check(R.id.resistanceRadioButton)
                WorkoutType.POWER -> workoutTypeRadioGroup.check(R.id.powerRadioButton)
            }
            updateIntervalInputHint()
            
            // Set duration in UI
            val durationMinutes = totalDurationSeconds / 60
            totalDurationEditText.setText(durationMinutes.toString())
            totalDurationEditText.isEnabled = false
            setDurationButton.isEnabled = false
            intervalsCard.visibility = View.VISIBLE
            
            // Load intervals
            intervals.clear()
            intervals.addAll(workout.intervals)
            
            intervalAdapter.updateIntervals(intervals)
            updateRemainingTime()
            checkWorkoutComplete()
            
            Toast.makeText(this, "Workout '$name' loaded!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load workout: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
