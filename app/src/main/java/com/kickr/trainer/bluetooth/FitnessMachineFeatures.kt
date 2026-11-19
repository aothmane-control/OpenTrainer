/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * What a trainer says it supports, from its Fitness Machine Feature characteristic:
 * two little-endian UINT32 bit fields, the machine features and the target setting features.
 */
data class FitnessMachineFeatures(val machineFeatures: Long, val targetSettings: Long) {

    val supportsResistanceTarget get() = targetSettings.hasBit(2)
    val supportsPowerTarget get() = targetSettings.hasBit(3)
    val supportsSimulation get() = targetSettings.hasBit(13)
    val supportsWheelCircumference get() = targetSettings.hasBit(14)

    override fun toString() =
        "FitnessMachineFeatures(machine=0x%08X, targetSettings=0x%08X)".format(machineFeatures, targetSettings)

    companion object {
        fun parse(value: ByteArray): FitnessMachineFeatures? {
            if (value.size < 8) return null
            val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
            val machine = buffer.getInt().toLong() and 0xFFFFFFFFL
            val targetSettings = buffer.getInt().toLong() and 0xFFFFFFFFL
            return FitnessMachineFeatures(machine, targetSettings)
        }

        private fun Long.hasBit(index: Int) = (this shr index) and 1L == 1L
    }
}
