package com.jellycine.app.ui.screens.dashboard.home

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.data.model.mergeRecentWatching
import org.junit.Assert.*
import org.junit.Test

class RecentWatchingTest {
    private fun video(id: String, date: String?, played: Boolean = false, position: Long = 1) =
        BaseItemDto(id = id, name = "same filename", type = "Video",
            userData = UserItemDataDto(played = played, playbackPositionTicks = position, lastPlayedDate = date))

    @Test fun completedAndResumableVideosAreSortedTogetherWithoutMergingEqualNames() {
        val recent = mergeRecentWatching(
            listOf(video("a", "2026-10-13T01:00:00Z")),
            listOf(video("b", "2026-10-13T02:00:00.0000000Z", played = true, position = 0)), 24)
        assertEquals(listOf("b", "a"), recent.map { it.id })
    }

    @Test fun duplicatesUnknownDatesAndLimitsAreHandled() {
        val a = video("a", "2026-10-13T01:00:00Z")
        val unknown = video("unknown", "invalid", played = true)
        assertEquals(listOf("a", "unknown"), mergeRecentWatching(listOf(a, unknown), listOf(a), 24).map { it.id })
        assertEquals(listOf("a"), mergeRecentWatching(listOf(unknown, a), emptyList(), 1).map { it.id })
    }

    @Test fun unwatchedAndMissingIdsAreExcluded() {
        assertTrue(mergeRecentWatching(listOf(video("new", null, position = 0),
            video("", null, played = true)), emptyList(), 24).isEmpty())
        assertTrue(mergeRecentWatching(listOf(video("a", null)), emptyList(), 0).isEmpty())
    }
}
