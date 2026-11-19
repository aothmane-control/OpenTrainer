/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Indoor bike simulation parameters as a trainer reports having applied them. */
data class SimulationParameters(
    val windSpeedMps: Double,
    val gradePercent: Double,
    val crr: Double,
    val cw: Double
) {
    companion object {
        // Fitness Machine Status op code "Indoor Bike Simulation Parameters Changed"
        const val STATUS_OPCODE = 0x12

        // Op code, then wind speed SINT16 (0.001 m/s), grade SINT16 (0.01 %),
        // Crr UINT8 (0.0001) and Cw UINT8 (0.01 kg/m), little-endian like the control point command
        fun fromStatus(value: ByteArray): SimulationParameters? {
            if (value.size < 7 || (value[0].toInt() and 0xFF) != STATUS_OPCODE) return null
            val buffer = ByteBuffer.wrap(value, 1, 6).order(ByteOrder.LITTLE_ENDIAN)
            return SimulationParameters(
                windSpeedMps = buffer.getShort() / 1000.0,
                gradePercent = buffer.getShort() / 100.0,
                crr = (buffer.get().toInt() and 0xFF) / 10000.0,
                cw = (buffer.get().toInt() and 0xFF) / 100.0
            )
        }
    }
}
