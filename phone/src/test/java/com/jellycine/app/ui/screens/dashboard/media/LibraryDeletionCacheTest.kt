package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.data.model.BaseItemDto
import com.jellycine.shared.playback.UserDataRefreshSignals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryDeletionCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun deletingOneSessionDoesNotDiscardAnotherServersCatalog() = runBlocking {
        val directory = temporary.newFolder()
        val cache = LibraryCatalogCache(directory)
        val a = libraryCatalogKey("delete-session-a", "url", "user", "library")
        val b = libraryCatalogKey("delete-session-b", "url", "user", "library")
        cache.save(a, listOf(BaseItemDto(id = "a")))
        cache.save(b, listOf(BaseItemDto(id = "b")))
        cache.invalidateSession("delete-session-a")
        assertNull(cache.peek(a))
        assertNull(LibraryCatalogCache(directory).load(a))
        assertEquals("b", LibraryCatalogCache(directory).load(b)?.single()?.id)
    }

    @Test fun restoringDurableHideRecordsFiltersOldDiskCachesAndLaterNetworkRefreshes() = runBlocking {
        val directory = temporary.newFolder()
        val key = libraryCatalogKey("restored-deletion-server", "url", "user", "library")
        val otherKey = libraryCatalogKey("restored-other-server", "url", "user", "library")
        val items = listOf(BaseItemDto(id = "restored-hidden"), BaseItemDto(id = "retained"))
        val originalCache = LibraryCatalogCache(directory)
        originalCache.save(key, items)
        originalCache.save(otherKey, items)

        UserDataRefreshSignals.restoreDeletedItems(mapOf("restored-deletion-server" to setOf("restored-hidden")))
        assertEquals(listOf("retained"), originalCache.peek(key)?.map { it.id })
        val reopened = LibraryCatalogCache(directory)
        assertEquals(listOf("retained"), reopened.load(key)?.map { it.id })
        assertEquals(items, reopened.load(otherKey))
        reopened.save(key, items) // The server still returns the video while deletion retries are pending.
        assertEquals(listOf("retained"), LibraryCatalogCache(directory).load(key)?.map { it.id })
    }

    @Test fun anOlderInFlightRefreshCannotPutTheDeletedVideoBackIntoCache() = runBlocking {
        val directory = temporary.newFolder()
        val key = libraryCatalogKey("delete-race-session", "url", "user", "library")
        UserDataRefreshSignals.notifyItemDeleted("delete-race-session", "removed")
        val cache = LibraryCatalogCache(directory)
        cache.save(key, listOf(BaseItemDto(id = "removed"), BaseItemDto(id = "kept")))
        assertEquals(listOf("kept"), cache.peek(key)?.map { it.id })
        assertEquals(listOf("kept"), LibraryCatalogCache(directory).load(key)?.map { it.id })
    }
}
