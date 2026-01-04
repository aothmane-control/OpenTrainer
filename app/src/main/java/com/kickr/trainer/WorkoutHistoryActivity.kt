package com.kickr.trainer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.kickr.trainer.adapter.WorkoutHistoryAdapter
import com.kickr.trainer.utils.WorkoutStorageManager
import java.io.File

class WorkoutHistoryActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var historyAdapter: WorkoutHistoryAdapter
    private lateinit var storageManager: WorkoutStorageManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_history)

        storageManager = WorkoutStorageManager(this)

        initViews()
        setupRecyclerView()
        loadWorkoutHistory()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        emptyText = findViewById(R.id.emptyText)

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = WorkoutHistoryAdapter { workout ->
            // Open workout summary
            openWorkoutSummary(workout)
        }

        historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@WorkoutHistoryActivity)
            adapter = historyAdapter
        }
    }

    private fun loadWorkoutHistory() {
        val workouts = storageManager.getAllWorkoutHistories()
        
        if (workouts.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            historyRecyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            historyRecyclerView.visibility = View.VISIBLE
            historyAdapter.updateWorkouts(workouts)
        }
    }

    private fun openWorkoutSummary(workout: com.kickr.trainer.model.WorkoutHistory) {
        // Find the file for this workout
        val historyDir = File(filesDir, "workout_history")
        val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
        val filename = "workout_${sdf.format(java.util.Date(workout.startTime))}.json"
        val file = File(historyDir, filename)
        
        if (file.exists()) {
            val intent = Intent(this, WorkoutSummaryActivity::class.java)
            intent.putExtra(WorkoutSummaryActivity.EXTRA_WORKOUT_FILE, file.absolutePath)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload in case workouts were deleted
        loadWorkoutHistory()
    }
}
