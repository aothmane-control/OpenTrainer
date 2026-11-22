package com.kickr.trainer.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResistanceLevelsTest {

    @Test
    fun defaultRangeKeepsThePreviousLevelTimesTen() {
        assertEquals(0..100, ResistanceRange.DEFAULT.run { minLevel..maxLevel })
        assertEquals(0, ResistanceRange.DEFAULT.rawFor(0))
        assertEquals(250, ResistanceRange.DEFAULT.rawFor(25))
        assertEquals(1000, ResistanceRange.DEFAULT.rawFor(100))
    }

    @Test
    fun levelsUseTheTrainersOwnNumbering() {
        // A trainer with resistance 1.0 to 24.0 in steps of 1.0 has levels 1 to 24
        val range = ResistanceRange(10, 240, 10)
        assertEquals(1..24, range.minLevel..range.maxLevel)
        assertEquals(10, range.rawFor(1))
        assertEquals(20, range.rawFor(2))
        assertEquals(240, range.rawFor(24))
        // KICKR CORE: 0 to 100 in steps of 1
        val kickr = ResistanceRange(0, 100, 1)
        assertEquals(0..100, kickr.minLevel..kickr.maxLevel)
        assertEquals(40, kickr.rawFor(40))
    }

    @Test
    fun levelsOutsideTheRangeAreClamped() {
        val range = ResistanceRange(10, 240, 10)
        assertEquals(10, range.rawFor(0))
        assertEquals(240, range.rawFor(25))
    }

    @Test
    fun levelsAreNeverNegative() {
        val range = ResistanceRange(-10, 10, 5)
        assertEquals(0..2, range.minLevel..range.maxLevel)
        assertEquals(0, range.rawFor(0))
        assertEquals(10, range.rawFor(2))
    }

    @Test
    fun parsesLittleEndianCharacteristicValue() {
        // min -10 (0xFFF6), max 1000 (0x03E8), increment 5
        val value = byteArrayOf(0xF6.toByte(), 0xFF.toByte(), 0xE8.toByte(), 0x03, 0x05, 0x00)
        assertEquals(ResistanceRange(-10, 1000, 5), ResistanceRange.parse(value))
    }

    @Test
    fun rejectsShortEmptyOrZeroIncrementValuesSafely() {
        assertNull(ResistanceRange.parse(byteArrayOf(0, 0, 0x64)))
        assertNull(ResistanceRange.parse(byteArrayOf(0x64, 0, 0x64, 0, 1, 0)))
        assertEquals(ResistanceRange(0, 100, 1), ResistanceRange.parse(byteArrayOf(0, 0, 0x64, 0, 0, 0)))
    }
}
