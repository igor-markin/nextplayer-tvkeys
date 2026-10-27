package dev.anilbeesetti.nextplayer.feature.player.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Ignores hardware repeat storms; never catches up with a burst after a delayed frame. */
internal class TvBoostKeyRepeater(
    private val scope: CoroutineScope,
    private val currentContext: () -> TvBoostContext?,
    private val step: (TvColorAction) -> Unit,
) {
    private var job: Job? = null
    private var key: Int? = null
    private var action: TvColorAction? = null

    fun start(keyCode: Int, action: TvColorAction) {
        stop()
        val snapshot = currentContext() ?: return
        key = keyCode
        this.action = action
        job = scope.launch {
            delay(INITIAL_DELAY_MILLIS)
            while (isActive && snapshot == currentContext()) {
                step(action)
                delay(INTERVAL_MILLIS)
            }
        }
    }

    fun release(keyCode: Int): Boolean {
        if (key != keyCode) return false
        stop()
        return true
    }

    fun stop() {
        job?.cancel()
        job = null
        key = null
        action = null
    }

    fun reachedBound(targetMillibels: Int): Boolean =
        (action == TvColorAction.INCREASE && targetMillibels >= 2000) ||
            (action == TvColorAction.DECREASE && targetMillibels <= 0)

    companion object {
        const val INITIAL_DELAY_MILLIS = 400L
        const val INTERVAL_MILLIS = 200L
    }
}
