package dev.markin.tvhelper

object GainLevels {
    const val STEP_DB = 5
    const val MAX_DB = 30

    /** Rounds down to the 5 dB grid and keeps the value within 0..MAX_DB. */
    fun normalize(db: Int): Int = (db.coerceIn(0, MAX_DB) / STEP_DB) * STEP_DB
}
