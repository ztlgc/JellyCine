package com.jellycine.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WatchSyncSession(
    val id: String,
    @SerialName("scope_id") val scopeId: String,
    @SerialName("item_id") val itemId: String,
    val name: String,
    @SerialName("opened_at_ms") val openedAtMs: Long,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class WatchSyncSpan(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("scope_id") val scopeId: String,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    val version: Long
)

@Serializable
data class WatchSyncBatch(
    val sessions: List<WatchSyncSession> = emptyList(),
    val spans: List<WatchSyncSpan> = emptyList()
) {
    val isEmpty: Boolean get() = sessions.isEmpty() && spans.isEmpty()
}

@Serializable
data class WatchSyncPage(
    val sessions: List<WatchSyncSession> = emptyList(),
    val spans: List<WatchSyncSpan> = emptyList(),
    @SerialName("next_cursor") val nextCursor: Long,
    @SerialName("has_more") val hasMore: Boolean
)

interface WatchSyncLocalStore {
    suspend fun pending(ownerId: String, limit: Int): WatchSyncBatch
    suspend fun acknowledge(ownerId: String, batch: WatchSyncBatch)
    suspend fun cursor(ownerId: String): Long
    /** Apply records and advance the cursor in one transaction. */
    suspend fun merge(ownerId: String, page: WatchSyncPage)
}

interface WatchSyncRemote {
    suspend fun push(batch: WatchSyncBatch)
    suspend fun pull(after: Long, limit: Int): WatchSyncPage
}

/** At-least-once delivery. IDs/versions, rather than request completion, decide uniqueness. */
class WatchSyncEngine(private val local: WatchSyncLocalStore, private val remote: WatchSyncRemote) {
    suspend fun synchronize(ownerId: String, canContinue: suspend () -> Unit = {}): Boolean {
        var batches = 0
        while (true) {
            canContinue()
            val pending = local.pending(ownerId, 200)
            if (pending.isEmpty) break
            remote.push(pending)
            canContinue()
            local.acknowledge(ownerId, pending)
            // A long playback session must not starve the download phase.
            if (++batches >= 50) break
        }
        var after = local.cursor(ownerId)
        do {
            canContinue()
            val page = remote.pull(after, 400)
            require(page.nextCursor >= after && (!page.hasMore || page.nextCursor > after)) {
                "Invalid watch sync cursor"
            }
            canContinue()
            local.merge(ownerId, page)
            after = page.nextCursor
        } while (page.hasMore)
        canContinue()
        return local.pending(ownerId,1).isEmpty
    }
}
