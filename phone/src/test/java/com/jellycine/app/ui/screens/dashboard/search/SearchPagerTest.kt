package com.jellycine.app.ui.screens.dashboard.search

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SearchSourceCursor
import com.jellycine.data.model.VideoSearchCursor
import com.jellycine.data.model.VideoSearchPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class SearchPagerTest {
    @Test fun ratingEditSurvivesAnInflightPageAndNewSearchReadsTheServerAgain() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<Unit>()
            var calls = 0
            val pager = pager(scope) { _, _ ->
                calls++
                if (calls == 2) pending.await()
                Result.success(page(if (calls == 2) "second" else "first").let { page ->
                    page.copy(items = page.items.map { it.copy(communityRating = 7f) })
                })
            }
            pager.search("same")
            pager.loadMore()
            pager.updateRating("first", 9f)
            assertEquals(9f, pager.state.value.items.first().communityRating)
            pending.complete(Unit)
            assertEquals(9f, pager.state.value.items.first().communityRating)
            pager.search("same")
            assertEquals(7f, pager.state.value.items.single().communityRating)
        } finally { scope.cancel() }
    }
    private fun page(vararg ids: String, more: Boolean = true) = VideoSearchPage(
        ids.map { BaseItemDto(id = it, name = it, type = "Video") },
        VideoSearchCursor(if (more) SearchSourceCursor(60) else null, null)
    )

    private fun pager(scope: CoroutineScope, fetch: suspend (String, VideoSearchCursor) -> Result<VideoSearchPage>) =
        SearchPager(scope, fetch, { it.id!! }, { items, _ -> items.sortedBy { it.name } })

    @Test fun appendKeepsExistingOrderMergesDuplicatesAndStopsAtTheEnd() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var calls = 0
            val pager = pager(scope) { _, cursor ->
                calls++
                Result.success(if (cursor.videos!!.startIndex == 0) page("b", "c") else page("a", "b", more = false))
            }
            pager.search("video")
            assertEquals(listOf("b", "c"), pager.state.value.items.map { it.id })
            pager.loadMore()
            assertEquals(listOf("b", "c", "a"), pager.state.value.items.map { it.id })
            pager.loadMore()
            assertEquals(2, calls)
            assertFalse(pager.state.value.cursor.hasMore)
        } finally { scope.cancel() }
    }

    @Test fun repeatedTriggersStartOneRequestAndFailedPageCanBeRetried() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<Result<VideoSearchPage>>()
            val offsets = mutableListOf<Int>()
            var calls = 0
            val pager = pager(scope) { _, cursor ->
                offsets += cursor.videos!!.startIndex
                when (++calls) {
                    1 -> Result.success(page("first"))
                    2 -> pending.await()
                    else -> Result.success(page("second", more = false))
                }
            }
            pager.search("video")
            pager.loadMore()
            pager.loadMore()
            assertEquals(2, calls)
            assertTrue(pager.state.value.isLoading)
            pending.complete(Result.failure(IllegalStateException("Offline")))
            assertTrue(pager.state.value.failed)
            assertEquals(listOf("first"), pager.state.value.items.map { it.id })
            pager.loadMore()
            assertEquals(listOf(0, 60, 60), offsets)
            assertEquals(listOf("first", "second"), pager.state.value.items.map { it.id })
            assertFalse(pager.state.value.failed)
        } finally { scope.cancel() }
    }

    @Test fun changingOrClearingQueryDiscardsEvenANonCooperativeOldResponse() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<Unit>()
            val pager = pager(scope) { query, _ ->
                if (query == "old") withContext(NonCancellable) { pending.await() }
                Result.success(page(query, more = false))
            }
            pager.search("old")
            pager.search("new")
            assertEquals(listOf("new"), pager.state.value.items.map { it.id })
            pending.complete(Unit)
            assertEquals("new", pager.state.value.query)
            assertEquals(listOf("new"), pager.state.value.items.map { it.id })
            pager.search("")
            pager.loadMore()
            assertTrue(pager.state.value.items.isEmpty())
            assertFalse(pager.state.value.isLoading)
        } finally { scope.cancel() }
    }

    @Test fun restartingSameQueryResetsTheCursorForServerChanges() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val offsets = mutableListOf<Int>()
            val pager = pager(scope) { _, cursor ->
                offsets += cursor.videos!!.startIndex
                Result.success(page("item"))
            }
            pager.search("same")
            val generation = pager.state.value.generation
            pager.search("same")
            assertEquals(listOf(0, 0), offsets)
            assertEquals(generation + 1, pager.state.value.generation)
        } finally { scope.cancel() }
    }
}
