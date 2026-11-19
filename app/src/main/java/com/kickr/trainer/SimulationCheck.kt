/*
 * Copyright (c) 2026 Amine Othmane
 * All rights reserved.
 */

package com.kickr.trainer

import android.util.Log
import com.kickr.trainer.bluetooth.KickrBluetoothService
import com.kickr.trainer.bluetooth.SimulationParameters
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Debug check whether the connected trainer supports FTMS simulation mode, without riding:
 * what the trainer advertises, whether it accepts a simulation command, and whether it
 * confirms the parameters it applied. Each line is also logged with the tag "SimulationCheck".
 */
internal object SimulationCheck {
    private const val TAG = "SimulationCheck"
    private const val SIMULATION_OPCODE = 0x11
    private const val TIMEOUT_MS = 3000L
    // Unusual on purpose, so the trainer's confirmation can't be left over from a GPX ride
    private const val TEST_GRADE = 1.5
    private const val TEST_CRR = 0.004
    private const val TEST_CW = 0.51

    suspend fun run(service: KickrBluetoothService): String {
        val lines = mutableListOf<String>()
        fun report(line: String) {
            Log.i(TAG, line)
            lines += line
        }

        val features = service.features
        if (features == null) {
            report("Fitness Machine Feature: not available")
        } else {
            report("Advertises simulation mode: ${yesNo(features.supportsSimulation)}")
            report("Advertises resistance / power targets: " +
                "${yesNo(features.supportsResistanceTarget)} / ${yesNo(features.supportsPowerTarget)}")
            report("Raw features: $features")
        }

        // Listen before sending, so a fast reply isn't missed
        val (response, applied) = coroutineScope {
            val response = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(TIMEOUT_MS) {
                    service.controlPointResponses.first { it.requestOpcode == SIMULATION_OPCODE }
                }
            }
            val applied = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(TIMEOUT_MS) {
                    service.machineStatus.mapNotNull { SimulationParameters.fromStatus(it) }.first()
                }
            }
            service.setSimulationParameters(gradePercent = TEST_GRADE, crr = TEST_CRR, cw = TEST_CW)
            response.await() to applied.await()
        }

        report("Sent: grade %.2f %%, Crr %.4f, Cw %.2f kg/m".format(TEST_GRADE, TEST_CRR, TEST_CW))
        report(if (response == null) {
            "Reply to simulation command: none within ${TIMEOUT_MS / 1000} s"
        } else {
            "Reply to simulation command: 0x%02X (%s)".format(response.resultCode, response.resultName)
        })
        if (applied == null) {
            report("Applied parameters: not reported (the status message is optional)")
        } else {
            val matches = abs(applied.gradePercent - TEST_GRADE) < 0.005 &&
                abs(applied.crr - TEST_CRR) < 0.00005 && abs(applied.cw - TEST_CW) < 0.005
            report("Applied: grade %.2f %%, Crr %.4f, Cw %.2f kg/m, wind %.3f m/s (%s)".format(
                applied.gradePercent, applied.crr, applied.cw, applied.windSpeedMps,
                if (matches) "matches" else "DIFFERS"
            ))
        }
        report("Rider weight can be set (User Data 0x2A98): ${yesNo(service.hasWeightCharacteristic)}")

        val accepted = response?.resultCode == 0x01
        report(when {
            accepted -> "Result: simulation mode supported"
            features?.supportsSimulation == true -> "Result: advertised, but the command was not accepted"
            else -> "Result: simulation mode not supported"
        })

        // Leave the trainer on a flat road
        service.setSimulationParameters(gradePercent = 0.0, crr = TEST_CRR, cw = TEST_CW)
        return lines.joinToString("\n")
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}
