package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.QueryResult
import com.jellycine.data.model.UserItemDataDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryCatalogLoadingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun firstPageIsPublishedBeforeRequestingTheRestOfThreeThousandItems() = runBlocking {
        val published = mutableListOf<Int>()
        val requested = mutableListOf<Int>()
        val result = fetchLibraryCatalog(fetchPage = { offset, limit ->
            if (offset > 0) assertEquals(offset, published.last())
            requested.add(offset)
            QueryResult(items = (offset until offset + limit).map { BaseItemDto(id = "$it") }, totalRecordCount = 3000)
        }, onPage = { items, total ->
            assertEquals(3000, total)
            published.add(items.size)
        })
        assertEquals(200, published.first())
        assertEquals(15, requested.size)
        assertEquals(3000, result.size)
        assertEquals(3000, result.map { it.id }.distinct().size)
    }

    @Test fun duplicatePagesFailInsteadOfLoopingForever() = runBlocking {
        var calls = 0
        try {
            fetchLibraryCatalog(fetchPage = { _, _ ->
                calls++
                QueryResult(items = listOf(BaseItemDto(id = "same")), totalRecordCount = 3000)
            })
            fail("Repeated pages should fail")
        } catch (_: IllegalStateException) {
            assertEquals(2, calls)
        }
    }

    @Test fun failedRefreshKeepsThePreviousCompleteDiskCatalog() = runBlocking {
        val directory = temporary.newFolder()
        val cache = LibraryCatalogCache(directory)
        val old = listOf(BaseItemDto(id = "old", userData = UserItemDataDto(played = true)))
        cache.save("library", old)
        var calls = 0
        try {
            val fresh = fetchLibraryCatalog(fetchPage = { _, _ ->
                if (++calls == 2) error("Offline")
                QueryResult(items = listOf(BaseItemDto(id = "new")), totalRecordCount = 2)
            })
            cache.save("library", fresh)
            fail("Network failure should propagate")
        } catch (_: IllegalStateException) {
            assertEquals(old, cache.peek("library"))
            assertEquals(old, LibraryCatalogCache(directory).load("library"))
        }
    }

    @Test fun persistedCatalogSurvivesRestartAndRemainsIsolatedByServerUserAndLibrary() = runBlocking {
        val directory = temporary.newFolder()
        val key = libraryCatalogKey("server", "https://example", "alice", "movies")
        val items = listOf(BaseItemDto(id = "film", name = "Film", genres = listOf("Drama")))
        LibraryCatalogCache(directory).save(key, items)
        val restarted = LibraryCatalogCache(directory)
        assertNull(restarted.peek(key))
        assertEquals(items, restarted.load(key))
        assertEquals(items, restarted.peek(key))
        assertNull(restarted.load(libraryCatalogKey("server", "https://example", "bob", "movies")))
        assertNull(restarted.load(libraryCatalogKey("other", "https://example", "alice", "movies")))
        assertNull(restarted.load(libraryCatalogKey("server", "https://example", "alice", "shows")))
        assertNotEquals(libraryCatalogKey("a|b", "c", "d", "e"), libraryCatalogKey("a", "b|c", "d", "e"))
    }

    @Test fun successfulEmptyRefreshRemovesDeletedItems() = runBlocking {
        val directory = temporary.newFolder()
        val cache = LibraryCatalogCache(directory)
        cache.save("library", listOf(BaseItemDto(id = "deleted")))
        cache.save("library", fetchLibraryCatalog(fetchPage = { _, _ -> QueryResult(items = emptyList(), totalRecordCount = 0) }))
        assertEquals(emptyList<BaseItemDto>(), cache.peek("library"))
        assertEquals(emptyList<BaseItemDto>(), LibraryCatalogCache(directory).load("library"))
    }

    @Test fun corruptedCacheFallsBackToNetworkAndMemoryIsBounded() = runBlocking {
        val directory = temporary.newFolder()
        val cache = LibraryCatalogCache(directory)
        cache.save("library", listOf(BaseItemDto(id = "film")))
        directory.listFiles()!!.single().writeText("corrupt")
        assertNull(LibraryCatalogCache(directory).load("library"))
        repeat(4) { cache.save("library$it", listOf(BaseItemDto(id = "$it"))) }
        assertNull(cache.peek("library0"))
        assertNotNull(cache.peek("library3"))
    }
}
