package dev.anilbeesetti.nextplayer.feature.player.state

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TvColorKeyHandlerTest {
    private val keys = TvColorKeyHandler()
    private fun down(code: Int, repeat: Int = 0, held: Long = 0) = keys.handle(code, KeyEvent.ACTION_DOWN, repeat, held)
    private fun up(code: Int) = keys.handle(code, KeyEvent.ACTION_UP, 0, 20)

    @Test
    fun measuredPhysicalColorsRemainAccurateAndApplyOnlyToTheOwnersRemote() {
        val expected = mapOf(166 to KeyEvent.KEYCODE_PROG_RED, 161 to KeyEvent.KEYCODE_PROG_GREEN, 410 to KeyEvent.KEYCODE_PROG_YELLOW, 217 to KeyEvent.KEYCODE_PROG_BLUE)
        expected.forEach { (scan, key) ->
            assertEquals(key, TvColorKeyHandler.normalize(KeyEvent.KEYCODE_UNKNOWN, scan, 0x2717, 0x32b9))
            assertEquals(KeyEvent.KEYCODE_UNKNOWN, TvColorKeyHandler.normalize(KeyEvent.KEYCODE_UNKNOWN, scan, 0x2717, 0x9999))
        }
    }

    @Test
    fun upperPhysicalRedIsPlusAndLowerPhysicalGreenIsMinus() {
        val red = TvColorKeyHandler.normalize(KeyEvent.KEYCODE_UNKNOWN, 166, 0x2717, 0x32b9)
        val green = TvColorKeyHandler.normalize(KeyEvent.KEYCODE_UNKNOWN, 161, 0x2717, 0x32b9)
        assertEquals(TvColorAction.INCREASE, down(red).action)
        up(red)
        assertEquals(TvColorAction.DECREASE, down(green).action)
    }

    @Test
    fun hardwareRepeatStormCannotDirectlyChangeGainOrInvokeInstantReset() {
        assertEquals(TvColorAction.INCREASE, down(KeyEvent.KEYCODE_PROG_RED).action)
        repeat(100) { assertNull(down(KeyEvent.KEYCODE_PROG_RED, it + 1, 5000).action) }
        assertNull(up(KeyEvent.KEYCODE_PROG_RED).action)
        assertEquals(TvColorAction.DECREASE, down(KeyEvent.KEYCODE_PROG_GREEN).action)
        repeat(100) { assertNull(down(KeyEvent.KEYCODE_PROG_GREEN, it + 1, 5000).action) }
        assertNull(up(KeyEvent.KEYCODE_PROG_GREEN).action)
    }

    @Test
    fun yellowAndBlueKeepSelectorsAndOtherKeysPassThrough() {
        assertEquals(TvColorAction.AUDIO, down(KeyEvent.KEYCODE_PROG_YELLOW).action)
        assertNull(down(KeyEvent.KEYCODE_PROG_YELLOW, 1, 700).action)
        assertEquals(TvColorAction.SUBTITLES, down(KeyEvent.KEYCODE_PROG_BLUE).action)
        assertFalse(down(KeyEvent.KEYCODE_VOLUME_UP).consumed)
    }

    @Test
    fun aReleaseOrRepeatAfterLifecycleClearCannotCreateANewPress() {
        down(KeyEvent.KEYCODE_PROG_RED)
        keys.clear()
        assertNull(up(KeyEvent.KEYCODE_PROG_RED).action)
        assertNull(down(KeyEvent.KEYCODE_PROG_RED, 1, 900).action)
        up(KeyEvent.KEYCODE_PROG_RED)
        assertEquals(TvColorAction.INCREASE, down(KeyEvent.KEYCODE_PROG_RED).action)
    }
}
