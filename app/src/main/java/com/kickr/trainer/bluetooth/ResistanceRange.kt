/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Resistance values a trainer accepts, in raw FTMS units (0.1 resolution), as reported by
 * its Supported Resistance Level Range characteristic. Workouts count resistance in levels:
 * level 0 is the trainer's minimum and each level is one increment more.
 */
data class ResistanceRange(val minimum: Int, val maximum: Int, val increment: Int) {

    val maxLevel get() = (maximum - minimum) / increment

    fun rawFor(level: Int) = minimum + level.coerceIn(0, maxLevel) * increment

    companion object {
        // Used when the trainer doesn't report a range: levels 0-100, sent as 0-1000 like before
        val DEFAULT = ResistanceRange(0, 1000, 10)

        // SINT16 minimum, SINT16 maximum, UINT16 minimum increment, all little-endian
        fun parse(value: ByteArray): ResistanceRange? {
            if (value.size < 6) return null
            val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
            val minimum = buffer.getShort().toInt()
            val maximum = buffer.getShort().toInt()
            val increment = buffer.getShort().toInt() and 0xFFFF
            if (maximum <= minimum) return null
            return ResistanceRange(minimum, maximum, increment.coerceAtLeast(1))
        }
    }
}
