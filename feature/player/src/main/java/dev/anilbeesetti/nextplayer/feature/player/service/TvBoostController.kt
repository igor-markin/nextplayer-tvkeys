package dev.anilbeesetti.nextplayer.feature.player.service

internal interface TvBoostEffect {
    val hasControl: Boolean
    fun applyGain(gainMillibels: Int): Int
    fun release()
}

/** Owns session-local gain only. It has no dependency on system stream volume. */
internal class TvBoostController(
    private val onGenerationChanged: () -> Unit = {},
    private val createEffect: (Int) -> TvBoostEffect,
) {
    var gainMillibels: Int = 0
        private set
    var active: Boolean = false
        private set
    var generation: Long = 0
        private set
    private var effect: TvBoostEffect? = null

    val isSupported: Boolean
        get() = runCatching { effect?.hasControl == true }.getOrDefault(false)

    fun setActive(value: Boolean) {
        active = value
        reset()
    }

    fun updateAudioSession(sessionId: Int, enabled: Boolean) {
        clearEffect()
        if (sessionId > 0 && enabled) {
            try {
                effect = createEffect(sessionId)
                check(effect?.applyGain(0) == 0)
            } catch (_: Exception) {
                clearEffect()
            }
        }
        advanceGeneration()
    }

    fun adjust(expectedGeneration: Long, deltaMillibels: Int, reset: Boolean = false): Boolean {
        // Validate at execution time, not when the controller or transport queued the press.
        if (expectedGeneration != generation) return false
        if (reset) {
            reset()
            return true
        }
        return setGain(gainMillibels + deltaMillibels.coerceIn(-STEP_MILLIBELS, STEP_MILLIBELS))
    }

    fun setGain(requestedMillibels: Int): Boolean {
        val requested = requestedMillibels.coerceIn(0, MAX_MILLIBELS)
        if (requested == 0) {
            clearGain()
            return true
        }
        if (!active || !isSupported) {
            reset()
            return false
        }
        return try {
            val applied = effect!!.applyGain(requested)
            check(applied in 0..MAX_MILLIBELS)
            gainMillibels = applied
            true
        } catch (_: Exception) {
            clearEffect()
            advanceGeneration()
            false
        }
    }

    fun reset() {
        clearGain()
        advanceGeneration()
    }

    private fun clearGain() {
        gainMillibels = 0
        try {
            check(effect?.applyGain(0)?.let { it == 0 } != false)
        } catch (_: Exception) {
            clearEffect()
        }
    }

    fun close() {
        active = false
        clearEffect()
        advanceGeneration()
    }

    private fun advanceGeneration() {
        generation++
        onGenerationChanged()
    }

    private fun clearEffect() {
        val previous = effect
        effect = null
        gainMillibels = 0
        runCatching { previous?.applyGain(0) }
        runCatching { previous?.release() }
    }

    companion object {
        const val STEP_MILLIBELS = 50
        const val MAX_MILLIBELS = 2000
    }
}
