/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.utils

import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.color.MaterialColors

/**
 * Axis labels, legend and grid lines in the theme's colours, so they are readable in dark mode
 * too: the chart library draws them black unless told otherwise.
 */
fun LineChart.useThemeColors() {
    val text = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
    val grid = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
    for (axis in listOf(axisLeft, axisRight, xAxis)) {
        axis.textColor = text
        axis.gridColor = grid
    }
    legend.textColor = text
}
