/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
    private var deleteMenuItem: MenuItem? = null

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
        toolbar.inflateMenu(R.menu.menu_workout_history)
        deleteMenuItem = toolbar.menu.findItem(R.id.action_delete_selected)
        deleteMenuItem?.isVisible = false
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_delete_selected) {
                confirmDeleteSelectedWorkouts()
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = WorkoutHistoryAdapter(
            onItemClick = { workout ->
                openWorkoutSummary(workout)
            },
            onSelectionChanged = { count ->
                deleteMenuItem?.isVisible = count > 0
                toolbar.title = if (count > 0) "$count selected" else getString(R.string.workout_history_title)
            }
        )
        historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@WorkoutHistoryActivity)
            adapter = historyAdapter
        }
    }

    private fun confirmDeleteSelectedWorkouts() {
        val selected = historyAdapter.getSelectedWorkouts()
        if (selected.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Delete Workouts")
            .setMessage("Are you sure you want to delete ${selected.size} selected workouts? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSelectedWorkouts(selected)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedWorkouts(selected: List<com.kickr.trainer.model.WorkoutHistory>) {
        var deleted = false
        for (workout in selected) {
            if (storageManager.deleteWorkoutHistory(workout)) {
                deleted = true
            }
        }
        if (deleted) {
            loadWorkoutHistory()
        }
        historyAdapter.clearSelection()
        deleteMenuItem?.isVisible = false
        toolbar.title = getString(R.string.workout_history_title)
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
        historyAdapter.clearSelection()
        deleteMenuItem?.isVisible = false
        toolbar.title = getString(R.string.workout_history_title)
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
    
    private fun confirmDeleteWorkout(workout: com.kickr.trainer.model.WorkoutHistory) {
        AlertDialog.Builder(this)
            .setTitle("Delete Workout")
            .setMessage("Are you sure you want to delete this workout? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteWorkout(workout)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteWorkout(workout: com.kickr.trainer.model.WorkoutHistory) {
        if (storageManager.deleteWorkoutHistory(workout)) {
            loadWorkoutHistory() // Refresh the list
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload in case workouts were deleted
        loadWorkoutHistory()
    }
}
