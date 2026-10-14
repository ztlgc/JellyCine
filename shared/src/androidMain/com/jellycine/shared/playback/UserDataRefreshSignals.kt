package com.jellycine.shared.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserDataRefreshEvent(
    val itemId: String?,
    val played: Boolean? = null,
    val deleted: Boolean = false,
    val serverId: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

object UserDataRefreshSignals {
    private val _deletedItems = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val deletedItems: StateFlow<Map<String, Set<String>>> = _deletedItems.asStateFlow()
    private val _refreshEvent = MutableStateFlow<UserDataRefreshEvent?>(null)
    val refreshEvent: StateFlow<UserDataRefreshEvent?> = _refreshEvent.asStateFlow()

    @Synchronized
    fun notifyItemDeleted(serverId: String, itemId: String) {
        _deletedItems.value = _deletedItems.value + (serverId to (_deletedItems.value[serverId].orEmpty() + itemId))
        _refreshEvent.value = UserDataRefreshEvent(itemId = itemId, deleted = true, serverId = serverId)
    }

    fun notifyUserDataChanged(itemId: String?, played: Boolean? = null) {
        _refreshEvent.value = UserDataRefreshEvent(
            itemId = itemId,
            played = played
        )
    }
}
