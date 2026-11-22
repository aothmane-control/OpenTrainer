package com.kickr.trainer.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FtmsParsingTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun readsTargetSettingBitsFromTheSecondField() {
        // Machine features 0x00004486, target settings 0x0000600C (bits 2, 3, 13, 14)
        val features = FitnessMachineFeatures.parse(bytes(0x86, 0x44, 0, 0, 0x0C, 0x60, 0, 0))
        assertNotNull(features)
        features!!
        assertEquals(0x4486L, features.machineFeatures)
        assertTrue(features.supportsResistanceTarget)
        assertTrue(features.supportsPowerTarget)
        assertTrue(features.supportsSimulation)
    }

    @Test
    fun simulationIsOnlySupportedWithBit13() {
        // Bit 14, the wheel circumference, right next to it
        val features = FitnessMachineFeatures.parse(bytes(0, 0, 0, 0, 0x0C, 0x40, 0, 0))!!
        assertFalse(features.supportsSimulation)
    }

    @Test
    fun bitFieldsAreUnsigned() {
        val features = FitnessMachineFeatures.parse(bytes(0, 0, 0, 0x80, 0, 0, 0, 0x80))!!
        assertEquals(0x80000000L, features.machineFeatures)
        assertEquals(0x80000000L, features.targetSettings)
    }

    @Test
    fun rejectsShortFeatureValues() {
        assertNull(FitnessMachineFeatures.parse(bytes(0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun readsAppliedSimulationParametersFromStatus() {
        // Wind 0, grade 1.50 % (150), Crr 0.0040 (40), Cw 0.51 (51)
        val applied = SimulationParameters.fromStatus(bytes(0x12, 0, 0, 0x96, 0, 0x28, 0x33))
        assertEquals(SimulationParameters(0.0, 1.5, 0.004, 0.51), applied)
    }

    @Test
    fun negativeGradeAndWindAreSigned() {
        // Wind -1.000 m/s (0xFC18), grade -3.00 % (0xFED4)
        val applied = SimulationParameters.fromStatus(bytes(0x12, 0x18, 0xFC, 0xD4, 0xFE, 0, 0))!!
        assertEquals(-1.0, applied.windSpeedMps, 1e-9)
        assertEquals(-3.0, applied.gradePercent, 1e-9)
    }

    @Test
    fun ignoresOtherStatusMessages() {
        // Started or resumed by the user
        assertNull(SimulationParameters.fromStatus(bytes(0x04)))
        assertNull(SimulationParameters.fromStatus(bytes(0x12, 0, 0, 0x96, 0, 0x28)))
    }

    @Test
    fun encodesTheSimulationCommand() {
        // Wind 0, grade 1.50 % (150), Crr 0.0040 (40), Cw 0.51 (51)
        assertArrayEquals(
            bytes(0x11, 0, 0, 0x96, 0, 0x28, 0x33),
            SimulationParameters(0.0, 1.5, 0.004, 0.51).toCommand()
        )
    }

    @Test
    fun roundsToTheFieldResolutions() {
        // Truncation sent 1.000 m/s, -2.99 %, 0.0040 and 0.28 (0.29 * 100 = 28.999…)
        val command = SimulationParameters(1.0006, -2.996, 0.00406, 0.29).toCommand()
        assertEquals(SimulationParameters.COMMAND_OPCODE, command[0].toInt())
        val sent = SimulationParameters.fromStatus(bytes(SimulationParameters.STATUS_OPCODE) + command.copyOfRange(1, 7))
        assertEquals(SimulationParameters(1.001, -3.0, 0.0041, 0.29), sent)
    }

    @Test
    fun clampsToTheFieldRanges() {
        // Wind 32.767 m/s (0x7FFF), grade -100.00 % (0xD8F0), Crr 0.0255 (255), Cw 0
        assertArrayEquals(
            bytes(0x11, 0xFF, 0x7F, 0xF0, 0xD8, 0xFF, 0),
            SimulationParameters(40.0, -150.0, 0.03, -1.0).toCommand()
        )
    }

    @Test
    fun sendsNaNAsZero() {
        assertArrayEquals(
            bytes(0x11, 0, 0, 0, 0, 0, 0),
            SimulationParameters(Double.NaN, Double.NaN, Double.NaN, Double.NaN).toCommand()
        )
    }
}
