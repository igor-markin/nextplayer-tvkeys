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

    @Test
    fun networkStreamWithTitleIsKeyedByTitle() {
        val first = externalMediaId(uri = "https://cdn.example/a.m3u8?token=1", title = "Show S1E2")
        val second = externalMediaId(uri = "https://cdn.example/b.m3u8?token=2", title = "Show S1E2")

        assertEquals(first, second)
    }

    @Test
    fun networkStreamWithoutTitleIsKeyedByUri() {
        val uri = "https://cdn.example/a.m3u8"

        assertEquals(uri, externalMediaId(uri = uri, title = null))
        assertEquals(uri, externalMediaId(uri = uri, title = " "))
    }

    @Test
    fun localFileWithTitleIsKeyedByUri() {
        val uri = "content://media/external/video/media/42"

        assertEquals(uri, externalMediaId(uri = uri, title = "Show S1E2"))
    }
}
