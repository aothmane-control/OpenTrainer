/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.utils

import android.content.Context
import android.util.Log
import com.kickr.trainer.bluetooth.ResistanceRange
import com.kickr.trainer.bluetooth.Trainer
import org.json.JSONException
import org.json.JSONObject

class SavedTrainerStorage(context: Context) {
    private val preferences = context.getSharedPreferences("saved_trainers", Context.MODE_PRIVATE)

    // The trainer that connected last, if it is still saved
    val lastConnected: Trainer?
        get() = preferences.getString("last_connected", null)?.let { address ->
            loadAll().find { it.address == address }
        }

    // Sorted by name; unreadable entries are skipped
    fun loadAll(): List<Trainer> {
        val trainersJson = try {
            readTrainers()
        } catch (e: JSONException) {
            Log.w(TAG, "Cannot read saved trainers", e)
            return emptyList()
        }
        return trainersJson.keys().asSequence().mapNotNull { address ->
            try {
                parseTrainer(address, trainersJson.getJSONObject(address))
            } catch (e: JSONException) {
                Log.w(TAG, "Skipping unreadable trainer: $address", e)
                null
            }
        }.sortedWith(compareBy({ it.name }, { it.address })).toList()
    }

    // Adds the trainer or updates its name and levels, and remembers it as the last one connected.
    // Trainers are learned again when they connect, so an unreadable list is replaced.
    fun save(trainer: Trainer) {
        val trainers = try {
            readTrainers()
        } catch (e: JSONException) {
            Log.w(TAG, "Replacing unreadable saved trainers", e)
            JSONObject()
        }
        val range = trainer.resistanceRange
        trainers.put(trainer.address, JSONObject().apply {
            put("name", trainer.name)
            put("minimum", range.minimum)
            put("maximum", range.maximum)
            put("increment", range.increment)
        })
        preferences.edit()
            .putString("trainers", trainers.toString())
            .putString("last_connected", trainer.address)
            .apply()
    }

    private fun readTrainers(): JSONObject =
        JSONObject(preferences.getString("trainers", null) ?: "{}")

    private fun parseTrainer(address: String, json: JSONObject): Trainer? {
        val range = ResistanceRange(json.getInt("minimum"), json.getInt("maximum"), json.getInt("increment"))
        // Same checks as for the value the trainer reports
        if (address.isBlank() || range.maximum <= range.minimum || range.increment < 1) return null
        return Trainer(address, json.optString("name").ifBlank { address }, range)
    }

    companion object {
        private const val TAG = "SavedTrainerStorage"
    }
}
