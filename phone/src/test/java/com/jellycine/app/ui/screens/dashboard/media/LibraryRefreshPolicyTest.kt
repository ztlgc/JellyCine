package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.shared.playback.UserDataRefreshEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryRefreshPolicyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun ratingUpdatesEveryCachedLibraryInItsSessionWithoutChangingUserDataOrFreshness() = runBlocking {
        var now = 1_000_000L
        val directory = temporary.newFolder()
        val cache = LibraryCatalogCache(directory) { now }
        val a = libraryCatalogKey("a", "url", "user", "library")
        val a2 = libraryCatalogKey("a", "url", "user", "second-library")
        val b = libraryCatalogKey("b", "url", "user", "library")
        val video = BaseItemDto(id = "video", communityRating = 7.5f,
            userData = UserItemDataDto(played = true, isFavorite = true, playbackPositionTicks = 123L))
        listOf(a, a2, b).forEach { cache.save(it, listOf(video)) }
        now += LibraryCatalogCache.FULL_REFRESH_INTERVAL_MS - 1
        cache.updateRating("a", "video", 9f)
        val restarted = LibraryCatalogCache(directory) { now }
        listOf(a, a2).forEach { key ->
            assertEquals(video.copy(communityRating = 9f), restarted.load(key)?.single())
            assertTrue(restarted.isFresh(key))
        }
        assertEquals(video, restarted.load(b)?.single())
        now++
        assertFalse(restarted.isFresh(a))
        val event = UserDataRefreshEvent("video", serverId = "a", ratingChanged = true, communityRating = 9f)
        assertEquals(video.copy(communityRating = 9f), listOf(video).withLibraryUserData(event).single())
    }

    @Test fun freshDiskCatalogAndEmptyCatalogDoNotRequestTheServer() = runBlocking {
        val directory = temporary.newFolder()
        val now = 1_000_000L
        val cache = LibraryCatalogCache(directory) { now }
        val items = listOf(BaseItemDto(id = "video"))
        cache.save("library", items)
        cache.save("empty", emptyList())
        val restarted = LibraryCatalogCache(directory) { now + 60_000L }
        var requests = 0
        for ((key, expected) in listOf("library" to items, "empty" to emptyList())) {
            assertEquals(expected, loadCachedLibraryCatalog(restarted, key) {
                requests++
                error("Fresh snapshots must bypass the server")
            })
        }
        assertEquals(0, requests)
    }

    @Test fun expiredCatalogIsShownBeforeRefreshAndManualRefreshBypassesTheInterval() = runBlocking {
        var now = 1_000_000L
        val cache = LibraryCatalogCache(temporary.newFolder()) { now }
        val old = listOf(BaseItemDto(id = "old"))
        val fresh = listOf(BaseItemDto(id = "new"))
        cache.save("library", old)
        now += LibraryCatalogCache.FULL_REFRESH_INTERVAL_MS
        var visible: List<BaseItemDto>? = null
        var requests = 0
        assertEquals(fresh, loadCachedLibraryCatalog(cache, "library", onCached = { visible = it }) {
            assertEquals(old, visible)
            requests++
            fresh
        })
        assertTrue(cache.isFresh("library"))
        assertEquals(old, loadCachedLibraryCatalog(cache, "library", forceRefresh = true) {
            requests++
            old
        })
        assertEquals(2, requests)
    }

    @Test fun playbackAndFavoriteChangesPersistWithoutNetworkOrExtendingCatalogFreshness() = runBlocking {
        var now = 1_000_000L
        val directory = temporary.newFolder()
        val cache = LibraryCatalogCache(directory) { now }
        cache.save("library", listOf(BaseItemDto(id = "video", userData = UserItemDataDto(played = false))))
        now += LibraryCatalogCache.FULL_REFRESH_INTERVAL_MS - 1
        val playback = UserDataRefreshEvent("video", timestampMs = now, playbackPositionTicks = 123_000L)
        val favorite = UserDataRefreshEvent("video", timestampMs = now, favorite = true)
        cache.updateItems("library") { it.withLibraryUserData(playback).withLibraryUserData(favorite) }
        val restarted = LibraryCatalogCache(directory) { now }
        val result = loadCachedLibraryCatalog(restarted, "library") { error("Local changes must not refetch the catalog") }
        assertEquals(123_000L, result.single().userData?.playbackPositionTicks)
        assertEquals(true, result.single().userData?.isFavorite)
        assertEquals(false, result.single().userData?.played)
        now++
        assertFalse(restarted.isFresh("library"))
    }

    @Test fun watchedSeriesUpdatesItsEpisodesWithoutChangingOtherVideosOrFavorites() {
        val items = listOf(
            BaseItemDto(id = "series", type = "Series"),
            BaseItemDto(id = "episode", seriesId = "series", userData = UserItemDataDto(playbackPositionTicks = 123L, isFavorite = true)),
            BaseItemDto(id = "other", userData = UserItemDataDto(played = false))
        )
        val updated = items.withLibraryUserData(UserDataRefreshEvent("series", played = true))
        assertEquals(true, updated[0].userData?.played)
        assertEquals(true, updated[1].userData?.played)
        assertEquals(0L, updated[1].userData?.playbackPositionTicks)
        assertEquals(true, updated[1].userData?.isFavorite)
        assertEquals(items[2], updated[2])
    }

    @Test fun deletionPreservesOtherItemsAndOtherServersWithoutResettingRefreshTime() = runBlocking {
        var now = 1_000_000L
        val directory = temporary.newFolder()
        val cache = LibraryCatalogCache(directory) { now }
        val a = libraryCatalogKey("a", "url", "user", "library")
        val b = libraryCatalogKey("b", "url", "user", "library")
        val items = listOf(BaseItemDto(id = "removed"), BaseItemDto(id = "kept"))
        cache.save(a, items)
        cache.save(b, items)
        now += LibraryCatalogCache.FULL_REFRESH_INTERVAL_MS - 1
        cache.removeItem("a", "removed")
        val restarted = LibraryCatalogCache(directory) { now }
        assertEquals(listOf("kept"), restarted.load(a)?.map { it.id })
        assertEquals(items, restarted.load(b))
        assertTrue(restarted.isFresh(a))
        now++
        assertFalse(restarted.isFresh(a))
    }

    @Test fun failedExpiredRefreshKeepsTheSnapshotAndDoesNotMarkItFresh() = runBlocking {
        var now = 1_000_000L
        val cache = LibraryCatalogCache(temporary.newFolder()) { now }
        val items = listOf(BaseItemDto(id = "video"))
        cache.save("library", items)
        now += LibraryCatalogCache.FULL_REFRESH_INTERVAL_MS
        try {
            loadCachedLibraryCatalog(cache, "library") { error("Offline") }
            fail("Network failure should propagate")
        } catch (_: IllegalStateException) {
            assertEquals(items, cache.peek("library"))
            assertFalse(cache.isFresh("library"))
        }
    }
}
