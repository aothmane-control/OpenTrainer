/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.utils

import android.content.Context
import android.util.Log
import com.kickr.trainer.model.WorkoutDataPoint
import com.kickr.trainer.model.WorkoutHistory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkoutStorageManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WorkoutStorage"
        private const val HISTORY_DIR = "workout_history"
    }
    
    private fun getHistoryDirectory(): File {
        val dir = File(context.filesDir, HISTORY_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    fun saveWorkoutHistory(history: WorkoutHistory): File? {
        try {
            val json = JSONObject().apply {
                put("startTime", history.startTime)
                put("endTime", history.endTime)
                put("workoutType", history.workoutType)
                put("workoutName", history.workoutName)
                put("totalDistance", history.totalDistance)
                put("averagePower", history.averagePower)
                put("maxPower", history.maxPower)
                put("averageSpeed", history.averageSpeed)
                put("maxSpeed", history.maxSpeed)
                put("averageCadence", history.averageCadence)
                put("averageHeartRate", history.averageHeartRate)
                
                val dataPointsArray = JSONArray()
                for (point in history.dataPoints) {
                    val pointJson = JSONObject().apply {
                        put("timestamp", point.timestamp)
                        put("elapsedSeconds", point.elapsedSeconds)
                        put("power", point.power)
                        put("speed", point.speed)
                        put("cadence", point.cadence)
                        put("heartRate", point.heartRate)
                        put("resistance", point.resistance)
                        put("distance", point.distance)
                    }
                    dataPointsArray.put(pointJson)
                }
                put("dataPoints", dataPointsArray)
            }
            
            // Create filename with timestamp
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val filename = "workout_${sdf.format(Date(history.startTime))}.json"
            val file = File(getHistoryDirectory(), filename)
            
            file.writeText(json.toString(2))
            Log.d(TAG, "Saved workout history to: ${file.absolutePath}")
            
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error saving workout history", e)
            return null
        }
    }
    
    fun loadWorkoutHistory(file: File): WorkoutHistory? {
        try {
            val json = JSONObject(file.readText())
            
            val dataPointsArray = json.getJSONArray("dataPoints")
            val dataPoints = mutableListOf<WorkoutDataPoint>()
            
            for (i in 0 until dataPointsArray.length()) {
                val pointJson = dataPointsArray.getJSONObject(i)
                dataPoints.add(WorkoutDataPoint(
                    timestamp = pointJson.getLong("timestamp"),
                    elapsedSeconds = pointJson.getInt("elapsedSeconds"),
                    power = pointJson.getInt("power"),
                    speed = pointJson.getDouble("speed").toFloat(),
                    cadence = pointJson.getInt("cadence"),
                    heartRate = pointJson.getInt("heartRate"),
                    resistance = pointJson.getInt("resistance"),
                    distance = pointJson.getDouble("distance")
                ))
            }
            
            return WorkoutHistory(
                startTime = json.getLong("startTime"),
                endTime = json.getLong("endTime"),
                workoutType = json.getString("workoutType"),
                workoutName = json.getString("workoutName"),
                dataPoints = dataPoints,
                totalDistance = json.getDouble("totalDistance"),
                averagePower = json.getDouble("averagePower"),
                maxPower = json.getInt("maxPower"),
                averageSpeed = json.getDouble("averageSpeed"),
                maxSpeed = json.getDouble("maxSpeed").toFloat(),
                averageCadence = json.getDouble("averageCadence"),
                averageHeartRate = json.getDouble("averageHeartRate")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading workout history from ${file.name}", e)
            return null
        }
    }
    
    fun getAllWorkoutHistories(): List<WorkoutHistory> {
        val histories = mutableListOf<WorkoutHistory>()
        val dir = getHistoryDirectory()
        
        dir.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
            loadWorkoutHistory(file)?.let { histories.add(it) }
        }
        
        return histories.sortedByDescending { it.startTime }
    }
    
    fun deleteWorkoutHistory(history: WorkoutHistory): Boolean {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val filename = "workout_${sdf.format(Date(history.startTime))}.json"
        val file = File(getHistoryDirectory(), filename)
        
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
