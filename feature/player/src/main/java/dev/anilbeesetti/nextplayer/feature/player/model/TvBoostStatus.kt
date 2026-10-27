package dev.anilbeesetti.nextplayer.feature.player.model

data class TvBoostStatus(
    val generation: Long,
    val mediaId: String?,
    val targetMillibels: Int,
    val appliedMillibels: Int,
    val active: Boolean,
    val available: Boolean,
)
