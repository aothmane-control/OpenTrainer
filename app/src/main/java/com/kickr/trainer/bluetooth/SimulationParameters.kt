/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Indoor bike simulation parameters, as sent to a trainer or as it reports having applied them. */
data class SimulationParameters(
    val windSpeedMps: Double,
    val gradePercent: Double,
    val crr: Double,
    val cw: Double
) {
    /**
     * Control point command "Set Indoor Bike Simulation Parameters". Values are rounded to the
     * field resolutions, since truncating sends 0.28 for 0.29 (0.29 * 100 = 28.999…).
     */
    fun toCommand(): ByteArray = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
        .put(COMMAND_OPCODE.toByte())
        .putShort(encode(windSpeedMps, 1000.0, Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        .putShort(encode(gradePercent, 100.0, -10000, 10000).toShort())
        .put(encode(crr, 10000.0, 0, 255).toByte())
        .put(encode(cw, 100.0, 0, 255).toByte())
        .array()

    companion object {
        // Fitness Machine Control Point op code "Set Indoor Bike Simulation Parameters"
        const val COMMAND_OPCODE = 0x11

        // Fitness Machine Status op code "Indoor Bike Simulation Parameters Changed"
        const val STATUS_OPCODE = 0x12

        // NaN is sent as 0, like truncation did, instead of throwing in the middle of a ride
        private fun encode(value: Double, scale: Double, min: Int, max: Int) =
            if (value.isNaN()) 0 else (value * scale).roundToInt().coerceIn(min, max)

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
