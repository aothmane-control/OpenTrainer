package com.kickr.trainer

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.kickr.trainer.model.WorkoutHistory
import com.kickr.trainer.utils.WorkoutStorageManager

class WorkoutSummaryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WORKOUT_FILE = "workout_file"
    }

    private lateinit var workoutNameText: TextView
    private lateinit var workoutDateText: TextView
    private lateinit var durationText: TextView
    private lateinit var distanceText: TextView
    private lateinit var avgPowerText: TextView
    private lateinit var powerHistoryChart: LineChart
    private lateinit var speedHistoryChart: LineChart
    private lateinit var resistanceHistoryChart: LineChart
    private lateinit var closeButton: Button
    private lateinit var shareButton: Button

    private var workoutHistory: WorkoutHistory? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_summary)

        initViews()
        loadWorkoutData()
        setupClickListeners()
    }

    private fun initViews() {
        workoutNameText = findViewById(R.id.workoutNameText)
        workoutDateText = findViewById(R.id.workoutDateText)
        durationText = findViewById(R.id.durationText)
        distanceText = findViewById(R.id.distanceText)
        avgPowerText = findViewById(R.id.avgPowerText)
        powerHistoryChart = findViewById(R.id.powerHistoryChart)
        speedHistoryChart = findViewById(R.id.speedHistoryChart)
        resistanceHistoryChart = findViewById(R.id.resistanceHistoryChart)
        closeButton = findViewById(R.id.closeButton)
        shareButton = findViewById(R.id.shareButton)

        setupCharts()
    }

    private fun setupCharts() {
        listOf(powerHistoryChart, speedHistoryChart, resistanceHistoryChart).forEach { chart ->
            chart.description.isEnabled = false
            chart.legend.isEnabled = true
            chart.setTouchEnabled(true)
            chart.isDragEnabled = true
            chart.setScaleEnabled(true)
            chart.setPinchZoom(true)
            chart.axisRight.isEnabled = false
        }
    }

    private fun loadWorkoutData() {
        val workoutFile = intent.getStringExtra(EXTRA_WORKOUT_FILE)
        if (workoutFile == null) {
            Toast.makeText(this, "Error: No workout data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val storageManager = WorkoutStorageManager(this)
        val file = java.io.File(workoutFile)
        
        workoutHistory = storageManager.loadWorkoutHistory(file)
        
        if (workoutHistory == null) {
            Toast.makeText(this, "Error loading workout data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        displayWorkoutData()
    }

    private fun displayWorkoutData() {
        val workout = workoutHistory ?: return

        workoutNameText.text = workout.workoutName
        workoutDateText.text = workout.getFormattedDate()
        durationText.text = workout.getFormattedDuration()
        distanceText.text = String.format("%.2f km", workout.totalDistance / 1000)
        avgPowerText.text = String.format("%.0f W", workout.averagePower)

        displayPowerChart(workout)
        displaySpeedChart(workout)
        displayResistanceChart(workout)
    }

    private fun displayPowerChart(workout: WorkoutHistory) {
        val entries = workout.dataPoints.map { 
            Entry(it.elapsedSeconds.toFloat(), it.power.toFloat()) 
        }

        val dataSet = LineDataSet(entries, "Power (W)").apply {
            color = ContextCompat.getColor(this@WorkoutSummaryActivity, android.R.color.holo_orange_dark)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(this@WorkoutSummaryActivity, android.R.color.holo_orange_light)
            fillAlpha = 50
        }

        powerHistoryChart.data = LineData(dataSet)
        powerHistoryChart.xAxis.axisMinimum = 0f
        powerHistoryChart.xAxis.axisMaximum = workout.getDurationSeconds().toFloat()
        powerHistoryChart.invalidate()
    }

    private fun displaySpeedChart(workout: WorkoutHistory) {
        val entries = workout.dataPoints.map { 
            Entry(it.elapsedSeconds.toFloat(), it.speed) 
        }

        val dataSet = LineDataSet(entries, "Speed (km/h)").apply {
            color = ContextCompat.getColor(this@WorkoutSummaryActivity, android.R.color.holo_green_dark)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(this@WorkoutSummaryActivity, android.R.color.holo_green_light)
            fillAlpha = 50
        }

        speedHistoryChart.data = LineData(dataSet)
        speedHistoryChart.xAxis.axisMinimum = 0f
        speedHistoryChart.xAxis.axisMaximum = workout.getDurationSeconds().toFloat()
        speedHistoryChart.invalidate()
    }

    private fun displayResistanceChart(workout: WorkoutHistory) {
        val entries = workout.dataPoints.map { 
            Entry(it.elapsedSeconds.toFloat(), it.resistance.toFloat()) 
        }

        val dataSet = LineDataSet(entries, "Resistance (%)").apply {
            color = ContextCompat.getColor(this@WorkoutSummaryActivity, android.R.color.holo_blue_dark)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(this@WorkoutSummaryActivity, android.R.color.holo_blue_light)
            fillAlpha = 50
        }

        resistanceHistoryChart.data = LineData(dataSet)
        resistanceHistoryChart.xAxis.axisMinimum = 0f
        resistanceHistoryChart.xAxis.axisMaximum = workout.getDurationSeconds().toFloat()
        resistanceHistoryChart.axisLeft.axisMinimum = 0f
        resistanceHistoryChart.axisLeft.axisMaximum = 100f
        resistanceHistoryChart.invalidate()
    }

    private fun setupClickListeners() {
        closeButton.setOnClickListener {
            finish()
        }

        shareButton.setOnClickListener {
            shareWorkout()
        }
    }

    private fun shareWorkout() {
        val workout = workoutHistory ?: return

        val shareText = """
            Workout Summary
            
            ${workout.workoutName}
            ${workout.getFormattedDate()}
            
            Duration: ${workout.getFormattedDuration()}
            Distance: ${"%.2f".format(workout.totalDistance / 1000)} km
            
            Average Power: ${"%.0f".format(workout.averagePower)} W
            Max Power: ${workout.maxPower} W
            
            Average Speed: ${"%.1f".format(workout.averageSpeed)} km/h
            Max Speed: ${"%.1f".format(workout.maxSpeed)} km/h
            
            Average Cadence: ${"%.0f".format(workout.averageCadence)} RPM
            Average Heart Rate: ${"%.0f".format(workout.averageHeartRate)} BPM
            
            #OpenTrainer #CyclingWorkout
        """.trimIndent()

        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Workout")
        startActivity(shareIntent)
    }
}
