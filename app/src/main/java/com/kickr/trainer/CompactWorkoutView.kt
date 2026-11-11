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
    private val tabs = root.findViewById<TabLayout>(R.id.compactChartTabs)
    private val chartContainer = root.findViewById<FrameLayout>(R.id.compactChartContainer)
    private val charts = listOf(profileChart, powerChart, speedChart)
    // Each chart is the last child of its classic card. Keep its original sizing when moving it back.
    private val chartHomes = charts.map { (it.parent as ViewGroup) to it.layoutParams }
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
            R.id.compactPauseButton to onPause,
            R.id.compactStopButton to onStop,
            R.id.compactConnectionButton to onConnection
        ).forEach { (id, action) ->
            root.findViewById<Button>(id).apply {
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
                val (parent, params) = chartHomes[index]
                chart.visibility = View.VISIBLE
                parent.addView(chart, params)
            }
        }
        selectChart()
    }

    fun render(
        workout: Workout,
        elapsedSeconds: Int,
        paused: Boolean,
        trainerData: TrainerData,
        distanceKm: Double,
        connection: ConnectionState
    ) {
        this.data = trainerData
        this.distanceKm = distanceKm
        renderMetrics()
        val intervalIndex = workout.getCurrentIntervalIndex(elapsedSeconds)
        val interval = workout.intervals.getOrNull(intervalIndex) ?: return
        val remaining = workout.getRemainingTime(elapsedSeconds)
        val intervalRemaining = workout.intervals.take(intervalIndex + 1).sumOf { it.duration } - elapsedSeconds
        val typeLabel = if (workout.type == WorkoutType.POWER) R.string.power_mode else R.string.resistance_mode
        root.findViewById<TextView>(R.id.compactWorkoutType).text = context.getString(
            if (paused) R.string.compact_paused else typeLabel
        )
        root.findViewById<TextView>(R.id.compactIntervalText).text = context.getString(
            R.string.workout_interval_format, intervalIndex + 1, workout.intervals.size
        )
        root.findViewById<TextView>(R.id.compactWorkoutTime).text = time(remaining)
        root.findViewById<TextView>(R.id.compactIntervalTime).text = context.getString(
            R.string.compact_interval_remaining, time(intervalRemaining)
        )
        root.findViewById<TextView>(R.id.compactCurrentTarget).text = if (workout.type == WorkoutType.POWER) {
            context.getString(R.string.workout_power_format, interval.power)
        } else {
            context.getString(R.string.workout_resistance_format, interval.resistance)
        }
        val next = workout.intervals.getOrNull(intervalIndex + 1)
        root.findViewById<TextView>(R.id.compactNextTarget).text = when {
            next == null -> context.getString(R.string.compact_final_interval)
            workout.type == WorkoutType.POWER -> context.getString(R.string.compact_next_power, next.power)
            else -> context.getString(R.string.compact_next_resistance, next.resistance)
        }
        root.findViewById<Button>(R.id.compactPauseButton).setText(
            if (paused) R.string.compact_resume else R.string.compact_pause
        )
        val connectionButton = root.findViewById<Button>(R.id.compactConnectionButton)
        connectionButton.text = when (connection) {
            ConnectionState.Connected -> "●"
            ConnectionState.Connecting -> context.getString(R.string.connecting)
            else -> context.getString(R.string.compact_reconnect)
        }
        connectionButton.setTextColor(ContextCompat.getColor(context, when (connection) {
            ConnectionState.Connected -> android.R.color.holo_green_dark
            ConnectionState.Connecting -> android.R.color.holo_blue_dark
            else -> android.R.color.holo_red_dark
        }))
        connectionButton.contentDescription = if (isPreview) {
            context.getString(R.string.workout_preview)
        } else when (connection) {
            ConnectionState.Connected -> context.getString(R.string.connected)
            ConnectionState.Connecting -> context.getString(R.string.connecting)
            is ConnectionState.Error -> connection.message
            else -> context.getString(R.string.compact_disconnected_reconnect)
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
        Metric.SPEED -> decimal(data.speed.toDouble(), 1)
        Metric.DISTANCE -> decimal(distanceKm, 2)
    }

    private fun decimal(value: Double, digits: Int): String = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
        isGroupingUsed = false
    }.format(value)

    private fun time(seconds: Int): String = context.getString(
        R.string.workout_time_format, seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60
    )
}
