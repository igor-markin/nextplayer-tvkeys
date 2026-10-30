package dev.markin.tvhelper

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

/** Applies a gain to the global output mix (audio session 0). */
class GlobalGain {

    enum class Mode { DYNAMICS, LOUDNESS }

    private var effect: AudioEffect? = null
    private var mode = Mode.DYNAMICS
    private var limiter = true

    var gainDb = 0
        private set

    fun apply(db: Int, mode: Mode, limiter: Boolean) {
        val normalized = GainLevels.normalize(db)
        val current = effect
        if (normalized > 0 && current is DynamicsProcessing && mode == this.mode && limiter == this.limiter) {
            // Changing only the level keeps the effect alive, so playback has no gap.
            current.setInputGainAllChannelsTo(normalized.toFloat())
            gainDb = normalized
            Log.i(TAG, "Changed gain to +$gainDb dB")
            return
        }

        release()
        this.mode = mode
        this.limiter = limiter
        gainDb = normalized
        if (gainDb == 0) return
        effect = when (mode) {
            Mode.DYNAMICS -> createDynamics(gainDb, limiter)
            Mode.LOUDNESS -> createLoudness(gainDb)
        }.also { it.enabled = true }
        Log.i(TAG, "Applied +$gainDb dB with $mode, limiter=$limiter, enabled=${effect?.enabled}")
    }

    fun release() {
        effect?.release()
        effect = null
        gainDb = 0
    }

    private fun createDynamics(db: Int, limiter: Boolean): AudioEffect {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNELS,
            false,
            0,
            false,
            0,
            false,
            0,
            limiter,
        ).build()
        return DynamicsProcessing(PRIORITY, GLOBAL_SESSION, config).apply {
            setInputGainAllChannelsTo(db.toFloat())
            if (!limiter) return@apply
            setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    LIMITER_ATTACK_MS,
                    LIMITER_RELEASE_MS,
                    LIMITER_RATIO,
                    LIMITER_THRESHOLD_DB,
                    0f,
                ),
            )
        }
    }

    // LoudnessEnhancer has a built-in compressor that cannot be turned off.
    private fun createLoudness(db: Int): AudioEffect = LoudnessEnhancer(GLOBAL_SESSION).apply {
        setTargetGain(db * MILLIBELS_PER_DB)
    }

    private companion object {
        const val TAG = "TvHelper"
        const val GLOBAL_SESSION = 0
        const val PRIORITY = Int.MAX_VALUE
        const val CHANNELS = 2
        const val MILLIBELS_PER_DB = 100
        const val LIMITER_ATTACK_MS = 1f
        const val LIMITER_RELEASE_MS = 60f
        const val LIMITER_RATIO = 10f
        const val LIMITER_THRESHOLD_DB = -1f
    }
}
