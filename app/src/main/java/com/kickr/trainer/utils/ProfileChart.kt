/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.utils

import android.content.Context
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import kotlin.math.ceil
import kotlin.math.floor

/** The target or grade along a workout, drawn alike during the ride and, thinner, in its summary. */
fun Context.profileLineDataSet(entries: List<Entry>, label: String, width: Float = 3f) =
    LineDataSet(entries, label).apply {
        color = ContextCompat.getColor(this@profileLineDataSet, android.R.color.holo_blue_dark)
        lineWidth = width
        setDrawCircles(false)
        setDrawValues(false)
        mode = LineDataSet.Mode.LINEAR
        setDrawFilled(true)
        fillColor = ContextCompat.getColor(this@profileLineDataSet, android.R.color.holo_blue_light)
        fillAlpha = 50
    }

/** Whole percent with a margin, always including 0 % so descents show below the line. */
fun YAxis.fitGrades(grades: List<Float>) {
    axisMinimum = floor(minOf(0f, grades.minOrNull() ?: 0f)) - 1f
    axisMaximum = ceil(maxOf(0f, grades.maxOrNull() ?: 0f)) + 1f
}
