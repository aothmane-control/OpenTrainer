/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.kickr.trainer.R
import com.kickr.trainer.model.WorkoutHistory


class WorkoutHistoryAdapter(
    private val onItemClick: (WorkoutHistory) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<WorkoutHistoryAdapter.ViewHolder>() {

    private var workouts = listOf<WorkoutHistory>()
    private val selectedWorkouts = mutableSetOf<WorkoutHistory>()
    var selectionMode = false
        private set

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val workoutNameText: TextView = view.findViewById(R.id.workoutNameText)
        val workoutTypeText: TextView = view.findViewById(R.id.workoutTypeText)
        val workoutDateText: TextView = view.findViewById(R.id.workoutDateText)
        val durationText: TextView = view.findViewById(R.id.durationText)
        val distanceText: TextView = view.findViewById(R.id.distanceText)
        val avgPowerText: TextView = view.findViewById(R.id.avgPowerText)
        val selectCheckBox: android.widget.CheckBox = view.findViewById(R.id.selectCheckBox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val workout = workouts[position]
        holder.workoutNameText.text = workout.workoutName
        holder.workoutTypeText.text = workout.workoutType
        holder.workoutDateText.text = workout.getFormattedDate()
        holder.durationText.text = workout.getFormattedDuration()
        holder.distanceText.text = String.format("%.2f km", workout.totalDistance / 1000)
        holder.avgPowerText.text = String.format("%.0f W", workout.averagePower)

        holder.selectCheckBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.selectCheckBox.isChecked = selectedWorkouts.contains(workout)

        holder.selectCheckBox.setOnCheckedChangeListener(null)
        holder.selectCheckBox.setOnClickListener {
            toggleSelection(workout)
        }

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelection(workout)
            } else {
                onItemClick(workout)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                selectedWorkouts.clear()
                selectedWorkouts.add(workout)
                notifyDataSetChanged()
                onSelectionChanged(selectedWorkouts.size)
                true
            } else {
                false
            }
        }
    }

    private fun toggleSelection(workout: WorkoutHistory) {
        if (selectedWorkouts.contains(workout)) {
            selectedWorkouts.remove(workout)
        } else {
            selectedWorkouts.add(workout)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedWorkouts.size)
        if (selectedWorkouts.isEmpty()) {
            selectionMode = false
            notifyDataSetChanged()
        }
    }

    fun getSelectedWorkouts(): List<WorkoutHistory> = selectedWorkouts.toList()

    fun clearSelection() {
        selectedWorkouts.clear()
        selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    override fun getItemCount() = workouts.size

    fun updateWorkouts(newWorkouts: List<WorkoutHistory>) {
        workouts = newWorkouts
        selectedWorkouts.clear()
        selectionMode = false
        notifyDataSetChanged()
    }
}
