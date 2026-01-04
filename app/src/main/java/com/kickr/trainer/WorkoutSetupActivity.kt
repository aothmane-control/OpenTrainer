package com.kickr.trainer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.kickr.trainer.adapter.IntervalAdapter
import com.kickr.trainer.model.Workout
import com.kickr.trainer.model.WorkoutInterval

class WorkoutSetupActivity : AppCompatActivity() {

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

    private lateinit var intervalAdapter: IntervalAdapter
    private val intervals = mutableListOf<WorkoutInterval>()
    private var totalDurationSeconds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_setup)

        initViews()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun initViews() {
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
    }

    private fun addInterval() {
        val duration = intervalDurationEditText.text.toString().toIntOrNull()
        val resistance = intervalResistanceEditText.text.toString().toIntOrNull()

        if (duration == null || duration <= 0) {
            Toast.makeText(this, "Please enter a valid duration", Toast.LENGTH_SHORT).show()
            return
        }

        if (resistance == null || resistance !in 0..100) {
            Toast.makeText(this, "Resistance must be between 0 and 100%", Toast.LENGTH_SHORT).show()
            return
        }

        val currentTotal = intervals.sumOf { it.duration }
        if (currentTotal + duration > totalDurationSeconds) {
            val remaining = totalDurationSeconds - currentTotal
            Toast.makeText(
                this,
                "Duration too long. Only $remaining seconds remaining",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        intervals.add(WorkoutInterval(duration, resistance))
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
        startWorkoutButton.visibility = if (isComplete) View.VISIBLE else View.GONE
        startWorkoutButton.isEnabled = isComplete
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
}
