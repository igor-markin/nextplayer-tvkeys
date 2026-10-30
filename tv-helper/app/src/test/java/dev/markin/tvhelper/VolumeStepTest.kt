package dev.markin.tvhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeStepTest {

    @Test
    fun singleStepGrowsToFive() {
        assertEquals(35, VolumeStep.target(30, 31, 0, 100))
        assertEquals(25, VolumeStep.target(30, 29, 0, 100))
    }

    @Test
    fun targetStaysWithinBounds() {
        assertEquals(100, VolumeStep.target(98, 99, 0, 100))
        assertEquals(0, VolumeStep.target(2, 1, 0, 100))
        assertNull(VolumeStep.target(99, 100, 0, 100))
    }

    @Test
    fun otherChangesAreLeftAlone() {
        assertNull(VolumeStep.target(30, 30, 0, 100))
        assertNull(VolumeStep.target(30, 35, 0, 100))
        assertNull(VolumeStep.target(30, 20, 0, 100))
    }
}
