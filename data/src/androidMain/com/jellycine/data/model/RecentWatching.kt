package com.jellycine.data.model

import java.time.Instant

/** Keep different videos with the same filename; item IDs identify watch records. */
fun mergeRecentWatching(resume: List<BaseItemDto>, played: List<BaseItemDto>, limit: Int): List<BaseItemDto> =
    (resume + played)
        .filter { !it.id.isNullOrBlank() && !it.name.isNullOrBlank() &&
            (it.userData?.played == true || (it.userData?.playbackPositionTicks ?: 0) > 0 ||
                !it.userData?.lastPlayedDate.isNullOrBlank()) }
        .distinctBy { it.id }
        .sortedByDescending { item ->
            runCatching { Instant.parse(item.userData?.lastPlayedDate).toEpochMilli() }.getOrDefault(0L)
        }
        .take(limit.coerceAtLeast(0))
