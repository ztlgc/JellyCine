package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.shared.playback.UserDataRefreshEvent
import java.time.Instant

/** Apply known results locally; playback/favorite/watched signals never require a catalog request. */
internal fun List<BaseItemDto>.withLibraryUserData(event: UserDataRefreshEvent): List<BaseItemDto> {
    val itemId = event.itemId ?: return this
    if (event.deleted) return filterNot { it.id == itemId }
    return map { item ->
        val direct = item.id == itemId
        if (!direct && (event.played == null || item.seriesId != itemId)) return@map item
        if (direct && event.ratingChanged && event.played == null && event.favorite == null && event.playbackPositionTicks == null) {
            return@map item.copy(communityRating = event.communityRating)
        }
        val old = item.userData ?: UserItemDataDto(itemId = item.id)
        item.copy(communityRating = if (direct && event.ratingChanged) event.communityRating else item.communityRating,
            userData = old.copy(
            played = event.played ?: old.played,
            isFavorite = if (direct) event.favorite ?: old.isFavorite else old.isFavorite,
            playbackPositionTicks = when {
                event.played != null -> 0L
                direct -> event.playbackPositionTicks ?: old.playbackPositionTicks
                else -> old.playbackPositionTicks
            },
            lastPlayedDate = if (direct && (event.playbackPositionTicks ?: 0L) > 0L)
                Instant.ofEpochMilli(event.timestampMs).toString() else old.lastPlayedDate
        ))
    }
}
