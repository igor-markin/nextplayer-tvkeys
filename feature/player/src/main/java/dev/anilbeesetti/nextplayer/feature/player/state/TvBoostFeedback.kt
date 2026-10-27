package dev.anilbeesetti.nextplayer.feature.player.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A user-input lease for the existing non-focusable toast; restores never arm it. */
internal class TvBoostFeedback(
    private val scope: CoroutineScope,
    private val showMessage: (String) -> Unit,
    private val dismissMessage: () -> Unit,
) {
    private var timeout: Job? = null
    private var armed = false
    private var blocked = false

    fun onUserInteraction() {
        if (blocked) return
        armed = true
        timeout?.cancel()
        timeout = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(TIMEOUT_MILLIS)
            armed = false
            dismissMessage()
        }
    }

    fun show(text: String) {
        if (armed && !blocked) showMessage(text)
    }

    fun setBlocked(value: Boolean) {
        blocked = value
        if (value) dismiss()
    }

    fun dismiss() {
        armed = false
        timeout?.cancel()
        timeout = null
        dismissMessage()
    }

    companion object {
        const val TIMEOUT_MILLIS = 1500L
    }
}
