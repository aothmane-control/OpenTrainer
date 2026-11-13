package com.kickr.trainer

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.scale
import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import com.kickr.trainer.bluetooth.KickrBluetoothService.ConnectionState
import com.kickr.trainer.model.TrainerData
import com.kickr.trainer.model.Workout
import com.kickr.trainer.model.WorkoutType
import com.kickr.trainer.utils.formatWorkoutTime
import java.text.NumberFormat

/** Presentation only: uses the existing charts, timer values, and workout actions. */
internal class CompactWorkoutView(
    val root: View,
    profileChart: LineChart,
    powerChart: LineChart,
    speedChart: LineChart,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onConnection: () -> Unit,
    private val isPreview: Boolean = false
) {
    private enum class Metric(val label: Int, val unit: Int, val color: Int) {
        POWER(R.string.power, R.string.compact_watts_unit, android.R.color.holo_orange_dark),
        CADENCE(R.string.cadence, R.string.compact_cadence_unit, android.R.color.holo_blue_dark),
        SPEED(R.string.speed, R.string.compact_speed_unit, android.R.color.holo_green_dark),
        DISTANCE(R.string.distance, R.string.compact_distance_unit, android.R.color.holo_purple)
    }

    private val context = root.context
    private val focusedLabel = root.findViewById<TextView>(R.id.compactFocusedLabel)
    private val focusedValue = root.findViewById<TextView>(R.id.compactFocusedValue)
    private val metricButtons = listOf(R.id.compactMetricOne, R.id.compactMetricTwo, R.id.compactMetricThree)
        .map { root.findViewById<Button>(it) }
    private val metrics = Metric.entries.toMutableList()
    private val workoutTypeText = root.findViewById<TextView>(R.id.compactWorkoutType)
    private val intervalText = root.findViewById<TextView>(R.id.compactIntervalText)
    private val workoutTimeText = root.findViewById<TextView>(R.id.compactWorkoutTime)
    private val intervalTimeText = root.findViewById<TextView>(R.id.compactIntervalTime)
    private val currentTargetText = root.findViewById<TextView>(R.id.compactCurrentTarget)
    private val nextTargetText = root.findViewById<TextView>(R.id.compactNextTarget)
    private val pauseButton = root.findViewById<Button>(R.id.compactPauseButton)
    private val connectionButton = root.findViewById<Button>(R.id.compactConnectionButton)
    private val oneDecimal = decimalFormat(1)
    private val twoDecimals = decimalFormat(2)
    private val tabs = root.findViewById<TabLayout>(R.id.compactChartTabs)
    private val chartContainer = root.findViewById<FrameLayout>(R.id.compactChartContainer)
    private val charts = listOf(profileChart, powerChart, speedChart)
    // Where each chart sits in its classic card, so it goes back to the same place and size
    private class ChartHome(val parent: ViewGroup, val index: Int, val params: ViewGroup.LayoutParams)
    private val chartHomes = charts.map {
        val parent = it.parent as ViewGroup
        ChartHome(parent, parent.indexOfChild(it), it.layoutParams)
    }
    private val secondaryColor = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private var data = TrainerData()
    private var distanceKm = 0.0

    val isVisible: Boolean get() = root.visibility == View.VISIBLE

    init {
        metricButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                val previous = metrics[0]
                metrics[0] = metrics[index + 1]
                metrics[index + 1] = previous
                renderMetrics()
            }
        }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = selectChart()
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        listOf(
            pauseButton to onPause,
            root.findViewById<Button>(R.id.compactStopButton) to onStop,
            connectionButton to onConnection
        ).forEach { (button, action) ->
            button.apply {
                isEnabled = !isPreview
                if (!isPreview) setOnClickListener { action() }
            }
        }
    }

    fun reset() {
        metrics.clear()
        metrics.addAll(Metric.entries)
        tabs.getTabAt(0)?.select()
        renderMetrics()
    }

    fun setVisible(visible: Boolean) {
        if (isVisible == visible) return
        root.visibility = if (visible) View.VISIBLE else View.GONE
        charts.forEachIndexed { index, chart ->
            (chart.parent as ViewGroup).removeView(chart)
            if (visible) {
                chartContainer.addView(chart, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ))
            } else {
                val home = chartHomes[index]
                chart.visibility = View.VISIBLE
                home.parent.addView(chart, home.index, home.params)
            }
        }
        selectChart()
    }

    /** Updates only the metric values: trainer data arrives several times per second. */
    fun updateMetrics(trainerData: TrainerData, distanceKm: Double) {
        this.data = trainerData
        this.distanceKm = distanceKm
        renderMetrics()
    }

    fun render(
        workout: Workout,
        elapsedSeconds: Int,
        paused: Boolean,
        trainerData: TrainerData,
        distanceKm: Double,
        connection: ConnectionState
    ) {
        updateMetrics(trainerData, distanceKm)
        val intervalIndex = workout.getCurrentIntervalIndex(elapsedSeconds)
        val interval = workout.intervals.getOrNull(intervalIndex) ?: return
        val remaining = workout.getRemainingTime(elapsedSeconds)
        val intervalRemaining = workout.getIntervalRemainingTime(elapsedSeconds)
        val typeLabel = if (workout.type == WorkoutType.POWER) R.string.power_mode else R.string.resistance_mode
        workoutTypeText.text = context.getString(
            if (paused) R.string.compact_paused else typeLabel
        )
        intervalText.text = context.getString(
            R.string.workout_interval_format, intervalIndex + 1, workout.intervals.size
        )
        workoutTimeText.text = context.formatWorkoutTime(remaining)
        intervalTimeText.text = context.getString(
            R.string.compact_interval_remaining, context.formatWorkoutTime(intervalRemaining)
        )
        currentTargetText.text = if (workout.type == WorkoutType.POWER) {
            context.getString(R.string.workout_power_format, interval.power)
        } else {
            context.getString(R.string.workout_resistance_format, interval.resistance)
        }
        val next = workout.intervals.getOrNull(intervalIndex + 1)
        nextTargetText.text = when {
            next == null -> context.getString(R.string.compact_final_interval)
            workout.type == WorkoutType.POWER -> context.getString(R.string.compact_next_power, next.power)
            else -> context.getString(R.string.compact_next_resistance, next.resistance)
        }
        pauseButton.setText(
            if (paused) R.string.compact_resume else R.string.compact_pause
        )
        connectionButton.text = when (connection) {
            ConnectionState.Connected -> "●"
            ConnectionState.Connecting -> context.getString(R.string.connecting)
            else -> context.getString(R.string.compact_reconnect)
        }
        connectionButton.setTextColor(ContextCompat.getColor(context, connection.statusColorRes()))
        connectionButton.contentDescription = if (isPreview) {
            context.getString(R.string.workout_preview)
        } else when (connection) {
            ConnectionState.Connecting -> context.getString(R.string.compact_connecting_choose_device)
            ConnectionState.Disconnected -> context.getString(R.string.compact_disconnected_reconnect)
            else -> connection.statusLabel(context)
        }
        connectionButton.tooltipText = connectionButton.contentDescription
    }

    private fun selectChart() {
        if (!isVisible) return
        charts.forEachIndexed { index, chart ->
            chart.visibility = if (index == tabs.selectedTabPosition) View.VISIBLE else View.GONE
        }
    }

    private fun renderMetrics() {
        val focused = metrics[0]
        focusedLabel.setText(focused.label)
        focusedValue.setTextColor(ContextCompat.getColor(context, focused.color))
        focusedValue.text = buildSpannedString {
            append(value(focused))
            color(secondaryColor) { scale(0.22f) { append(" ${context.getString(focused.unit)}") } }
        }
        metricButtons.forEachIndexed { index, button ->
            val metric = metrics[index + 1]
            button.setTextColor(ContextCompat.getColor(context, metric.color))
            button.text = buildSpannedString {
                color(secondaryColor) { scale(0.5f) { append(context.getString(metric.label)) } }
                append("\n")
                scale(1.2f) { append(value(metric)) }
                append("\n")
                color(secondaryColor) { scale(0.5f) { append(context.getString(metric.unit)) } }
            }
            button.contentDescription = context.getString(
                R.string.compact_focus_metric, context.getString(metric.label),
                value(metric), context.getString(metric.unit)
            )
        }
    }

    private fun value(metric: Metric): String = when (metric) {
        Metric.POWER -> data.power.toString()
        Metric.CADENCE -> data.cadence.toString()
        Metric.SPEED -> oneDecimal.format(data.speed.toDouble())
        Metric.DISTANCE -> twoDecimals.format(distanceKm)
    }

    private fun decimalFormat(digits: Int): NumberFormat = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
        isGroupingUsed = false
    }
}
