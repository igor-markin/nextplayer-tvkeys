package dev.anilbeesetti.nextplayer.feature.player.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerApiTest {

    @Test
    fun zeroPositionFallsBackToSavedPosition() {
        assertNull(apiStartPosition(position = 0, fromStart = false))
    }

    @Test
    fun missingPositionFallsBackToSavedPosition() {
        assertNull(apiStartPosition(position = null, fromStart = false))
    }

    @Test
    fun positivePositionIsUsed() {
        assertEquals(1_337_000, apiStartPosition(position = 1_337_000, fromStart = false))
    }

    @Test
    fun fromStartOverridesPositionAndSavedState() {
        assertEquals(0, apiStartPosition(position = 1_337_000, fromStart = true))
    }
}
