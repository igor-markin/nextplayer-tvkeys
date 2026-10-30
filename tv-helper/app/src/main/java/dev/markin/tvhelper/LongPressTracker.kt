package dev.markin.tvhelper

/**
 * Decides what to do with key events of the watched key.
 *
 * The initial press always reaches the foreground app, because an accessibility
 * service cannot re-inject a short press it has swallowed. Repeats are swallowed so
 * the app does not run its own long-press action, and the release is swallowed once
 * the long press has fired.
 */
class LongPressTracker(private val thresholdMs: Long) {

    enum class Action { PASS, CONSUME, FIRE }

    private var pressed = false
    private var fired = false

    fun onDown(repeatCount: Int, downTime: Long, eventTime: Long): Action {
        if (repeatCount == 0) {
            pressed = true
            fired = false
            return Action.PASS
        }
        if (pressed && !fired && eventTime - downTime >= thresholdMs) {
            fired = true
            return Action.FIRE
        }
        return Action.CONSUME
    }

    /** Called when the hold timer started at the initial press expires. */
    fun onTimeout(): Boolean {
        if (!pressed || fired) return false
        fired = true
        return true
    }

    /** Returns true when the release must be swallowed. */
    fun onUp(): Boolean {
        val consume = fired
        pressed = false
        fired = false
        return consume
    }
}
