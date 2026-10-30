package dev.markin.tvhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class GainLevelsTest {

    @Test
    fun keepsValuesOnTheGrid() {
        assertEquals(0, GainLevels.normalize(0))
        assertEquals(15, GainLevels.normalize(15))
        assertEquals(30, GainLevels.normalize(30))
    }

    @Test
    fun roundsDownToStep() {
        assertEquals(10, GainLevels.normalize(14))
    }

    @Test
    fun clampsOutOfRangeValues() {
        assertEquals(0, GainLevels.normalize(-5))
        assertEquals(30, GainLevels.normalize(60))
    }
}
