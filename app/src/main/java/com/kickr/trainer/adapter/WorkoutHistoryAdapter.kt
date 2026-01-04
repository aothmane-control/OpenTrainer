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
import com.kickr.trainer.R
import com.kickr.trainer.model.WorkoutHistory

class WorkoutHistoryAdapter(
    private val onItemClick: (WorkoutHistory) -> Unit
) : RecyclerView.Adapter<WorkoutHistoryAdapter.ViewHolder>() {

    private var workouts = listOf<WorkoutHistory>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val workoutNameText: TextView = view.findViewById(R.id.workoutNameText)
        val workoutTypeText: TextView = view.findViewById(R.id.workoutTypeText)
        val workoutDateText: TextView = view.findViewById(R.id.workoutDateText)
        val durationText: TextView = view.findViewById(R.id.durationText)
        val distanceText: TextView = view.findViewById(R.id.distanceText)
        val avgPowerText: TextView = view.findViewById(R.id.avgPowerText)
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
        
        holder.itemView.setOnClickListener {
            onItemClick(workout)
        }
    }

    override fun getItemCount() = workouts.size

    fun updateWorkouts(newWorkouts: List<WorkoutHistory>) {
        workouts = newWorkouts
        notifyDataSetChanged()
    }
}
