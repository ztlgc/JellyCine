package com.jellycine.shared.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserDataRefreshEvent(
    val itemId: String?,
    val played: Boolean? = null,
    val deleted: Boolean = false,
    val serverId: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val favorite: Boolean? = null,
    val playbackPositionTicks: Long? = null,
    val communityRating: Float? = null,
    val ratingChanged: Boolean = false
)

object UserDataRefreshSignals {
    private val _deletedItems = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val deletedItems: StateFlow<Map<String, Set<String>>> = _deletedItems.asStateFlow()
    private val _refreshEvent = MutableStateFlow<UserDataRefreshEvent?>(null)
    val refreshEvent: StateFlow<UserDataRefreshEvent?> = _refreshEvent.asStateFlow()

    fun notifyRatingChanged(serverId: String, itemId: String, rating: Float?) {
        _refreshEvent.value = UserDataRefreshEvent(
            itemId = itemId, serverId = serverId, communityRating = rating, ratingChanged = true
        )
    }

    @Synchronized
    fun notifyItemDeleted(serverId: String, itemId: String) {
        _deletedItems.value = _deletedItems.value + (serverId to (_deletedItems.value[serverId].orEmpty() + itemId))
        _refreshEvent.value = UserDataRefreshEvent(itemId = itemId, deleted = true, serverId = serverId)
    }

    fun notifyUserDataChanged(
        itemId: String?, played: Boolean? = null,
        favorite: Boolean? = null, playbackPositionTicks: Long? = null
    ) {
        _refreshEvent.value = UserDataRefreshEvent(
            itemId = itemId,
            played = played,
            favorite = favorite,
            playbackPositionTicks = playbackPositionTicks
        )
    }
}
