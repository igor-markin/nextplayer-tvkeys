package dev.anilbeesetti.nextplayer.feature.player.state

import android.view.KeyEvent

internal enum class TvColorAction { INCREASE, DECREASE, RESET, AUDIO, SUBTITLES }

internal data class TvColorKeyResult(val consumed: Boolean, val action: TvColorAction? = null)

/** Called only by the foreground TV video controls, never by the library or a global receiver. */
internal class TvColorKeyHandler {
    private val pressed = mutableSetOf<Int>()

    fun handle(keyCode: Int, action: Int, repeatCount: Int, heldMillis: Long): TvColorKeyResult {
        if (keyCode !in COLOR_KEYS) return TvColorKeyResult(false)
        if (action == KeyEvent.ACTION_UP) {
            pressed.remove(keyCode)
            return TvColorKeyResult(true)
        }
        if (action != KeyEvent.ACTION_DOWN) return TvColorKeyResult(true)
        val fresh = pressed.add(keyCode) && repeatCount == 0
        if (fresh) {
            return TvColorKeyResult(
                true,
                when (keyCode) {
                    KeyEvent.KEYCODE_PROG_RED -> TvColorAction.INCREASE
                    KeyEvent.KEYCODE_PROG_GREEN -> TvColorAction.DECREASE
                    KeyEvent.KEYCODE_PROG_YELLOW -> TvColorAction.AUDIO
                    else -> TvColorAction.SUBTITLES
                },
            )
        }
        return TvColorKeyResult(true)
    }

    fun clear() {
        pressed.clear()
    }

    companion object {
        /** Measured Xiaomi colors; owner chose upper red=plus and lower green=minus. */
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
