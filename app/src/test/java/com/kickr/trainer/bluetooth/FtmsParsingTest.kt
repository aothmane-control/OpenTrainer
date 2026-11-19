package com.kickr.trainer.bluetooth

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
        assertTrue(features.supportsWheelCircumference)
    }

    @Test
    fun simulationIsOnlySupportedWithBit13() {
        val features = FitnessMachineFeatures.parse(bytes(0, 0, 0, 0, 0x0C, 0x40, 0, 0))!!
        assertFalse(features.supportsSimulation)
        assertTrue(features.supportsWheelCircumference)
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
}
