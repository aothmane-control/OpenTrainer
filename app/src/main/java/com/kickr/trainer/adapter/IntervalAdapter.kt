package com.kickr.trainer.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kickr.trainer.R
import com.kickr.trainer.model.WorkoutInterval

class IntervalAdapter(
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<IntervalAdapter.IntervalViewHolder>() {

    private var intervals: List<WorkoutInterval> = emptyList()

    inner class IntervalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val intervalNumberTextView: TextView = itemView.findViewById(R.id.intervalNumberTextView)
        private val intervalDetailsTextView: TextView = itemView.findViewById(R.id.intervalDetailsTextView)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteIntervalButton)

        fun bind(interval: WorkoutInterval, position: Int) {
            intervalNumberTextView.text = "Interval ${position + 1}"
            val durationMinutes = interval.duration / 60.0
            intervalDetailsTextView.text = "%.1f min @ ${interval.resistance}%%".format(durationMinutes)
            
            deleteButton.setOnClickListener {
                onDeleteClick(position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntervalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_interval, parent, false)
        return IntervalViewHolder(view)
    }

    override fun onBindViewHolder(holder: IntervalViewHolder, position: Int) {
        holder.bind(intervals[position], position)
    }

    override fun getItemCount(): Int = intervals.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateIntervals(newIntervals: List<WorkoutInterval>) {
        intervals = newIntervals
        notifyDataSetChanged()
    }
}
