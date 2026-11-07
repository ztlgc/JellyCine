package com.jellycine.data.repository

import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.network.canonicalServerUrl
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** Durable identity for an accepted deletion. Credentials are resolved separately for each attempt. */
@Serializable
data class VideoDeletionTarget(
    val serverId: String,
    val serverUrl: String,
    val userId: String,
    val serverTypeRaw: String?,
    val itemId: String,
    val name: String
)

enum class VideoDeletionOutcome {
    /** The server accepted the deletion, or a fresh item-ID lookup proved absence. */
    COMPLETE,
    /** Network/HTTP failure, including 401/403. The caller owns the retry budget. */
    RETRY,
    /** No request was sent because the original local session is unavailable or mismatched. */
    SESSION_UNAVAILABLE
}

internal fun VideoDeletionTarget.matchesSession(
    serverId: String,
    serverUrl: String,
    userId: String
): Boolean = this.serverId.isNotBlank() && this.serverUrl.isNotBlank() &&
    this.userId.isNotBlank() && itemId.isNotBlank() &&
    this.serverId == serverId && this.userId == userId &&
    canonicalServerUrl(this.serverUrl) == canonicalServerUrl(serverUrl)

/** The supplied API must belong to this immutable session, never the currently selected account. */
internal suspend fun deleteVideoWithSession(
    target: VideoDeletionTarget,
    api: MediaServerApi,
    serverId: String,
    serverUrl: String,
    userId: String
): VideoDeletionOutcome {
    if (!target.matchesSession(serverId, serverUrl, userId)) {
        return VideoDeletionOutcome.SESSION_UNAVAILABLE
    }
    return try {
        val response = api.deleteItem(target.itemId)
        when {
            response.isSuccessful -> VideoDeletionOutcome.COMPLETE
            response.code() == 404 -> {
                // DELETE /Items may be missing at a proxy or unsupported server endpoint. Require
                // an authenticated, fresh, successful lookup proving this particular ID is absent.
                // Neither a second 404 nor an error message is evidence of item absence.
                val lookup = api.getUserItems(
                    userId = userId,
                    recursive = true,
                    limit = 1,
                    forceRefresh = true,
                    ids = target.itemId
                )
                val result = lookup.body()
                if (lookup.isSuccessful && result != null &&
                    result.items?.isEmpty() == true && result.totalRecordCount == 0
                ) {
                    VideoDeletionOutcome.COMPLETE
                } else {
                    VideoDeletionOutcome.RETRY
                }
            }
            else -> VideoDeletionOutcome.RETRY
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        VideoDeletionOutcome.RETRY
    }
}
