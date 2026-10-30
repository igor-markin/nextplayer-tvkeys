package dev.markin.tvhelper

import kotlin.math.sign

/** Turns a one-step change of the music volume into a [STEP]-sized one. */
object VolumeStep {
    const val STEP = 5

    /** Returns the volume to set, or null when the change is not a single remote step. */
    fun target(previous: Int, current: Int, min: Int, max: Int): Int? {
        val delta = current - previous
        if (delta != 1 && delta != -1) return null
        val target = (previous + delta.sign * STEP).coerceIn(min, max)
        return target.takeIf { it != current }
    }
}
