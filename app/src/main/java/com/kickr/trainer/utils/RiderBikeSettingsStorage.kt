/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.utils

import android.content.Context
import com.kickr.trainer.model.RiderBikeSettings

class RiderBikeSettingsStorage(context: Context) {
    private val preferences = context.getSharedPreferences("rider_bike_settings", Context.MODE_PRIVATE)

    // Values that are missing, unreadable or out of range fall back to their default
    fun load(): RiderBikeSettings = RiderBikeSettings.of(
        RiderBikeSettings.Parameter.entries.mapNotNull { parameter ->
            preferences.getString(parameter.key, null)?.toDoubleOrNull()
                ?.takeIf { it in parameter.range }
                ?.let { parameter to it }
        }.toMap()
    )

    // Stored as text so values like 0.004 are kept exactly
    fun save(settings: RiderBikeSettings) {
        val editor = preferences.edit()
        for (parameter in RiderBikeSettings.Parameter.entries) {
            editor.putString(parameter.key, settings.valueOf(parameter).toString())
        }
        editor.apply()
    }
}
