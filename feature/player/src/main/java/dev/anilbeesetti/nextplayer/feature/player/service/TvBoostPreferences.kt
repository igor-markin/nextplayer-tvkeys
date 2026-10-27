package dev.anilbeesetti.nextplayer.feature.player.service

import android.content.Context
import android.content.SharedPreferences

/** Dedicated private file; never rewrites the existing player settings datastore. */
internal interface TvBoostStore {
    fun load(): Int
    fun save(value: Int): Boolean
}

internal class TvBoostPreferences(private val preferences: SharedPreferences) : TvBoostStore {
    constructor(context: Context) : this(context.getSharedPreferences("tv_keys_boost", Context.MODE_PRIVATE))

    override fun load(): Int = when (val value = runCatching { preferences.all[KEY] }.getOrNull()) {
        is Int -> value.coerceIn(0, TvBoostController.MAX_MILLIBELS)
        is Long -> value.coerceIn(0L, TvBoostController.MAX_MILLIBELS.toLong()).toInt()
        else -> 0
    }

    /** Called on Dispatchers.IO; a changed target is acknowledged after the disk commit. */
    override fun save(value: Int): Boolean = preferences.edit().putInt(KEY, value.coerceIn(0, TvBoostController.MAX_MILLIBELS)).commit()

    companion object {
        internal const val KEY = "target_millibels"
    }
}
