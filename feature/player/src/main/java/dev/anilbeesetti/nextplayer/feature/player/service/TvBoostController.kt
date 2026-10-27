package dev.anilbeesetti.nextplayer.feature.player.service

internal interface TvBoostEffect {
    val hasControl: Boolean
    fun applyGain(gainMillibels: Int): Int
    fun release()
}

/** The saved user target outlives effects; lifecycle never changes system volume. */
internal class TvBoostController(
    private val onGenerationChanged: () -> Unit = {},
    private val createEffect: (Int) -> TvBoostEffect,
) {
    var targetMillibels = 0
        private set
    var gainMillibels = 0
        private set
    var active = false
        private set
    var generation = 0L
        private set
    private var ready = false
    private var sessionId = 0
    private var enabled = false
    private var effect: TvBoostEffect? = null

    val isSupported: Boolean get() = runCatching { effect?.hasControl == true }.getOrDefault(false)

    fun restoreTarget(value: Int) {
        targetMillibels = value.coerceIn(0, MAX_MILLIBELS)
        applyTarget()
        onGenerationChanged()
    }

    fun setActive(value: Boolean) {
        active = value
        recreateEffect()
    }

    fun setReady(value: Boolean) {
        if (ready == value) return
        ready = value
        if (!value) generation++
        applyTarget()
        onGenerationChanged()
    }

    fun updateAudioSession(sessionId: Int, enabled: Boolean) {
        this.sessionId = sessionId
        this.enabled = enabled
        recreateEffect()
    }

    fun invalidatePlayback() {
        ready = false
        clearEffect()
        generation++
        onGenerationChanged()
    }

    fun recreateEffect() {
        clearEffect()
        generation++
        applyTarget()
        onGenerationChanged()
    }

    fun adjust(expectedGeneration: Long, deltaMillibels: Int, reset: Boolean = false): Boolean {
        if (expectedGeneration != generation || !active || !ready) return false
        if (reset) {
            reset()
            return true
        }
        return setGain(targetMillibels + deltaMillibels.coerceIn(-STEP_MILLIBELS, STEP_MILLIBELS))
    }

    fun setGain(requestedMillibels: Int): Boolean {
        if (!active || !ready) return false
        restoreTarget(requestedMillibels)
        return true
    }

    /** Explicit legacy command only; media/route/lifecycle preserve the target. */
    fun reset() {
        targetMillibels = 0
        recreateEffect()
    }

    fun close() {
        active = false
        ready = false
        clearEffect()
        generation++
        onGenerationChanged()
    }

    private fun applyTarget() {
        if (!active || !ready || sessionId <= 0 || !enabled) {
            clearEffect()
            return
        }
        try {
            val current = effect ?: createEffect(sessionId).also { effect = it }
            check(current.hasControl)
            gainMillibels = current.applyGain(targetMillibels).also { check(it in 0..MAX_MILLIBELS) }
        } catch (_: Exception) {
            clearEffect()
        }
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
