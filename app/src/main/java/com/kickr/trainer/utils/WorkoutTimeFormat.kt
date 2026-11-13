/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.utils

import android.content.Context
import com.kickr.trainer.R

/** Formats a workout duration as minutes and seconds, e.g. 12:34. */
fun Context.formatWorkoutTime(seconds: Int): String {
    val clamped = seconds.coerceAtLeast(0)
    return getString(R.string.workout_time_format, clamped / 60, clamped % 60)
}
