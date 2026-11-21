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
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
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
    mapView: View,
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
    private val timeLabelText = root.findViewById<TextView>(R.id.compactTimeLabel)
    private val workoutTimeText = root.findViewById<TextView>(R.id.compactWorkoutTime)
    private val intervalTimeText = root.findViewById<TextView>(R.id.compactIntervalTime)
    private val currentTargetText = root.findViewById<TextView>(R.id.compactCurrentTarget)
    private val nextTargetText = root.findViewById<TextView>(R.id.compactNextTarget)
    private val pauseButton = root.findViewById<Button>(R.id.compactPauseButton)
    private val connectionButton = root.findViewById<Button>(R.id.compactConnectionButton)
    // Shown only during a workout; while just connected the panel shows live metrics only
    private val workoutOnlyViews = listOf(
        R.id.compactHeaderRow, R.id.compactTimeCard, R.id.compactCurrentTarget, R.id.compactWorkoutControls
    ).map { root.findViewById<View>(it) }
    private val oneDecimal = decimalFormat(1)
    private val twoDecimals = decimalFormat(2)
    private val tabs = root.findViewById<TabLayout>(R.id.compactChartTabs)
    private val chartContainer = root.findViewById<FrameLayout>(R.id.compactChartContainer)
    // The charts and, during GPX rides, the route map, one at a time below the tabs
    private val panes = listOf(profileChart, powerChart, speedChart, mapView)
    // Where each pane sits in its classic card, so it goes back to the same place and size
    private class ChartHome(val parent: ViewGroup, val index: Int, val params: ViewGroup.LayoutParams)
    private val chartHomes = panes.map {
        val parent = it.parent as ViewGroup
        ChartHome(parent, parent.indexOfChild(it), it.layoutParams)
    }
    private val secondaryColor = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private var data = TrainerData()
    private var distanceKm = 0.0

    val isVisible: Boolean get() = root.isVisible

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
        tabs.getTabAt(PROFILE_TAB)?.select()
        renderMetrics()
    }

    /**
     * Without a workout there is no timer, target or profile, only live trainer metrics.
     * Only GPX rides ([route]) have a map.
     */
    fun setWorkoutMode(workout: Boolean, route: Boolean = false) {
        workoutOnlyViews.forEach { it.isVisible = workout }
        root.keepScreenOn = workout
        tabs.getTabAt(PROFILE_TAB)?.view?.isVisible = workout
        tabs.getTabAt(MAP_TAB)?.view?.isVisible = route
        if (!workout && tabs.selectedTabPosition == PROFILE_TAB) tabs.getTabAt(POWER_TAB)?.select()
        if (!route && tabs.selectedTabPosition == MAP_TAB) {
            tabs.getTabAt(if (workout) PROFILE_TAB else POWER_TAB)?.select()
        }
        selectChart()
    }

    fun setVisible(visible: Boolean) {
        if (isVisible == visible) return
        root.isVisible = visible
        panes.forEachIndexed { index, chart ->
            (chart.parent as ViewGroup).removeView(chart)
            if (visible) {
                chartContainer.addView(chart, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ))
            } else {
                val home = chartHomes[index]
                chart.isVisible = true
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
        val power = workout.type == WorkoutType.POWER
        val next = workout.intervals.getOrNull(intervalIndex + 1)
        showCard(
            type = if (power) R.string.power_mode else R.string.resistance_mode,
            title = context.getString(R.string.workout_interval_format, intervalIndex + 1, workout.intervals.size),
            timeLabel = R.string.compact_workout_remaining,
            time = context.formatWorkoutTime(workout.getRemainingTime(elapsedSeconds)),
            detail = context.getString(
                R.string.compact_interval_remaining,
                context.formatWorkoutTime(workout.getIntervalRemainingTime(elapsedSeconds))
            ),
            next = when {
                next == null -> context.getString(R.string.compact_final_interval)
                power -> context.getString(R.string.compact_next_power, next.power)
                else -> context.getString(R.string.compact_next_resistance, next.resistance)
            },
            target = if (power) {
                context.getString(R.string.workout_power_format, interval.power)
            } else {
                context.getString(R.string.workout_resistance_format, interval.resistance)
            },
            paused = paused,
            connection = connection
        )
    }

    /** A GPX ride: distance instead of time, and the grade sent to the trainer instead of a target. */
    fun renderRoute(
        name: String,
        elapsedSeconds: Int,
        paused: Boolean,
        trainerData: TrainerData,
        distanceMeters: Double,
        totalMeters: Double,
        gradePercent: Double,
        connection: ConnectionState
    ) {
        updateMetrics(trainerData, distanceMeters / 1000)
        showCard(
            type = R.string.compact_route,
            title = name,
            timeLabel = R.string.compact_distance_remaining,
            time = context.getString(R.string.distance_format, (totalMeters - distanceMeters) / 1000),
            detail = context.getString(R.string.compact_route_time, context.formatWorkoutTime(elapsedSeconds)),
            next = context.getString(R.string.route_progress, distanceMeters / 1000, totalMeters / 1000),
            target = context.getString(R.string.grade_format, gradePercent),
            paused = paused,
            connection = connection
        )
    }

    // The header, time card and target line, filled the same way for workouts and routes
    private fun showCard(
        type: Int,
        title: String,
        timeLabel: Int,
        time: String,
        detail: String,
        next: String,
        target: String,
        paused: Boolean,
        connection: ConnectionState
    ) {
        workoutTypeText.setText(if (paused) R.string.compact_paused else type)
        intervalText.text = title
        timeLabelText.setText(timeLabel)
        workoutTimeText.text = time
        intervalTimeText.text = detail
        nextTargetText.text = next
        currentTargetText.text = target
        renderControls(paused, connection)
    }

    private fun renderControls(paused: Boolean, connection: ConnectionState) {
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

    // Hidden panes stay laid out: the map only draws the route once it has a size
    private fun selectChart() {
        if (!isVisible) return
        panes.forEachIndexed { index, chart ->
            chart.isInvisible = index != tabs.selectedTabPosition
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

    private companion object {
        // Positions in compactChartTabs and in panes
        const val PROFILE_TAB = 0
        const val POWER_TAB = 1
        const val MAP_TAB = 3
    }
}
