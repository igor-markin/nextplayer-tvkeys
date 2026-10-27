package dev.anilbeesetti.nextplayer.feature.player.state

import android.view.KeyEvent

internal enum class TvColorAction { INCREASE, DECREASE, RESET, AUDIO, SUBTITLES }

internal data class TvColorKeyResult(val consumed: Boolean, val action: TvColorAction? = null)

/** Called only by the foreground TV video controls, never by the library or a global receiver. */
internal class TvColorKeyHandler {
    private val pressed = mutableSetOf<Int>()
    private var redResetSent = false

    fun handle(keyCode: Int, action: Int, repeatCount: Int, heldMillis: Long): TvColorKeyResult {
        if (keyCode !in COLOR_KEYS) return TvColorKeyResult(false)
        if (action == KeyEvent.ACTION_UP) {
            pressed.remove(keyCode)
            val reset = keyCode == KeyEvent.KEYCODE_PROG_RED && heldMillis >= HOLD_MILLIS && !redResetSent
            if (keyCode == KeyEvent.KEYCODE_PROG_RED) redResetSent = false
            return TvColorKeyResult(true, TvColorAction.RESET.takeIf { reset })
        }
        if (action != KeyEvent.ACTION_DOWN) return TvColorKeyResult(true)
        val fresh = pressed.add(keyCode) && repeatCount == 0
        if (fresh) {
            if (keyCode == KeyEvent.KEYCODE_PROG_RED) redResetSent = false
            return TvColorKeyResult(
                true,
                when (keyCode) {
                    KeyEvent.KEYCODE_PROG_GREEN -> TvColorAction.INCREASE
                    KeyEvent.KEYCODE_PROG_RED -> TvColorAction.DECREASE
                    KeyEvent.KEYCODE_PROG_YELLOW -> TvColorAction.AUDIO
                    else -> TvColorAction.SUBTITLES
                },
            )
        }
        if (keyCode == KeyEvent.KEYCODE_PROG_RED && heldMillis >= HOLD_MILLIS && !redResetSent) {
            redResetSent = true
            return TvColorKeyResult(true, TvColorAction.RESET)
        }
        return TvColorKeyResult(true)
    }

    fun clear() {
        pressed.clear()
        redResetSent = false
    }

    companion object {
        const val HOLD_MILLIS = 600L
        /** Scan codes captured from the owner's Xiaomi RC (2717:32b9), not generic media-key aliases. */
        fun normalize(keyCode: Int, scanCode: Int, vendorId: Int, productId: Int): Int {
            if (vendorId != 0x2717 || productId != 0x32b9) return keyCode
            return when (scanCode) {
                166 -> KeyEvent.KEYCODE_PROG_RED
                161 -> KeyEvent.KEYCODE_PROG_GREEN
                410 -> KeyEvent.KEYCODE_PROG_YELLOW
                217 -> KeyEvent.KEYCODE_PROG_BLUE
                else -> keyCode
            }
        }

        private val COLOR_KEYS = setOf(
            KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_PROG_GREEN,
            KeyEvent.KEYCODE_PROG_YELLOW,
            KeyEvent.KEYCODE_PROG_BLUE,
        )
    }
}
