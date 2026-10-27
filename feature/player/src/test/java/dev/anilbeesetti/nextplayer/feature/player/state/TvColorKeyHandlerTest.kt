package dev.anilbeesetti.nextplayer.feature.player.state

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TvColorKeyHandlerTest {
    private val keys = TvColorKeyHandler()
    private fun down(code: Int, repeat: Int = 0, held: Long = 0) = keys.handle(code, KeyEvent.ACTION_DOWN, repeat, held)
    private fun up(code: Int, held: Long = 20) = keys.handle(code, KeyEvent.ACTION_UP, 0, held)

    @Test
    fun measuredScanCodesApplyOnlyToTheOwnersRemote() {
        val expected = mapOf(166 to KeyEvent.KEYCODE_PROG_RED, 161 to KeyEvent.KEYCODE_PROG_GREEN, 410 to KeyEvent.KEYCODE_PROG_YELLOW, 217 to KeyEvent.KEYCODE_PROG_BLUE)
        expected.forEach { (scan, key) ->
            assertEquals(key, TvColorKeyHandler.normalize(KeyEvent.KEYCODE_UNKNOWN, scan, 0x2717, 0x32b9))
            assertEquals(KeyEvent.KEYCODE_UNKNOWN, TvColorKeyHandler.normalize(KeyEvent.KEYCODE_UNKNOWN, scan, 0x2717, 0x9999))
        }
        assertEquals(KeyEvent.KEYCODE_VOLUME_UP, TvColorKeyHandler.normalize(KeyEvent.KEYCODE_VOLUME_UP, 115, 0x2717, 0x32b9))
    }

    @Test
    fun greenOnlyIncreasesOncePerPress() {
        assertEquals(TvColorAction.INCREASE, down(KeyEvent.KEYCODE_PROG_GREEN).action)
        assertNull(down(KeyEvent.KEYCODE_PROG_GREEN, 1, 700).action)
        assertNull(down(KeyEvent.KEYCODE_PROG_GREEN, 0, 800).action)
        up(KeyEvent.KEYCODE_PROG_GREEN)
        assertEquals(TvColorAction.INCREASE, down(KeyEvent.KEYCODE_PROG_GREEN).action)
    }

    @Test
    fun redHoldResetsOnceWithOrWithoutRepeatEvents() {
        assertEquals(TvColorAction.DECREASE, down(KeyEvent.KEYCODE_PROG_RED).action)
        assertEquals(TvColorAction.RESET, down(KeyEvent.KEYCODE_PROG_RED, 1, 650).action)
        assertNull(down(KeyEvent.KEYCODE_PROG_RED, 2, 800).action)
        assertNull(up(KeyEvent.KEYCODE_PROG_RED, 900).action)
        down(KeyEvent.KEYCODE_PROG_RED)
        assertEquals(TvColorAction.RESET, up(KeyEvent.KEYCODE_PROG_RED, 700).action)
    }

    @Test
    fun yellowAndBlueOpenSelectorsWithoutRepeatsAndOtherKeysPassThrough() {
        assertEquals(TvColorAction.AUDIO, down(KeyEvent.KEYCODE_PROG_YELLOW).action)
        assertNull(down(KeyEvent.KEYCODE_PROG_YELLOW, 1, 700).action)
        assertEquals(TvColorAction.SUBTITLES, down(KeyEvent.KEYCODE_PROG_BLUE).action)
        assertFalse(down(KeyEvent.KEYCODE_VOLUME_UP).consumed)
        assertFalse(down(KeyEvent.KEYCODE_DPAD_UP).consumed)
    }
}
