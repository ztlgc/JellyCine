package com.jellycine.data.repository

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.QueryResult
import com.jellycine.data.model.VideoSearchCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoSearchPagingTest {
    private fun video(id: Int) = BaseItemDto(id = "v$id", type = "Video")

    @Test fun loadsBeyondSixtyAndStopsRequestingTheFinishedSource() = runBlocking {
        val requests = mutableListOf<Pair<Boolean, Int>>()
        val fetch: suspend (Boolean, Int, Int, Boolean) -> QueryResult<BaseItemDto> = { videos, offset, size, _ ->
            requests += videos to offset
            val items = if (videos) (0 until 145).map(::video)
                else (0 until 3).map { BaseItemDto(id = "s$it", type = "Series", isFolder = true) }
            QueryResult(items.drop(offset).take(size), items.size)
        }
        var cursor = VideoSearchCursor()
        val all = mutableListOf<BaseItemDto>()
        while (cursor.hasMore) {
            val page = fetchVideoSearchPage(cursor, 60, fetch)
            all += page.items
            cursor = page.nextCursor
        }
        assertEquals(148, all.map { it.id }.distinct().size)
        assertEquals(listOf(0, 60, 120), requests.filter { it.first }.map { it.second })
        assertEquals(listOf(0), requests.filterNot { it.first }.map { it.second })
    }

    @Test fun rawOffsetsIncludeFilteredRowsAndServerMayCapPageSize() = runBlocking {
        val cursor = VideoSearchCursor(series = null)
        val first = fetchVideoSearchPage(cursor, 60) { _, offset, _, _ ->
            assertEquals(0, offset)
            QueryResult(listOf(video(0), BaseItemDto(id = "photo", type = "Photo")), 3)
        }
        assertEquals(listOf("v0"), first.items.map { it.id })
        assertEquals(2, first.nextCursor.videos?.startIndex)
        val last = fetchVideoSearchPage(first.nextCursor, 60) { _, offset, _, _ ->
            assertEquals(2, offset)
            QueryResult(listOf(video(2)), 3)
        }
        assertFalse(last.nextCursor.hasMore)
    }

    @Test fun missingTotalsContinueUntilEmptyEvenWithShortPages() = runBlocking {
        val first = fetchVideoSearchPage(VideoSearchCursor(series = null), 2) { _, _, _, _ ->
            QueryResult(listOf(video(0), video(1)))
        }
        assertTrue(first.nextCursor.hasMore)
        val last = fetchVideoSearchPage(first.nextCursor, 2) { _, offset, _, _ ->
            assertEquals(2, offset)
            QueryResult(emptyList())
        }
        assertFalse(last.nextCursor.hasMore)
        fetchVideoSearchPage(last.nextCursor, 2) { _, _, _, _ -> error("Finished sources must not be fetched") }
        val short = fetchVideoSearchPage(VideoSearchCursor(series = null), 2) { _, _, _, _ -> QueryResult(listOf(video(0))) }
        assertTrue(short.nextCursor.hasMore)
    }

    @Test fun nameFallbackIsPinnedAcrossPagesAndNotUsedForLaterErrors() = runBlocking {
        val requests = mutableListOf<Pair<Int, Boolean>>()
        val fetch: suspend (Boolean, Int, Int, Boolean) -> QueryResult<BaseItemDto> = { _, offset, _, byName ->
            requests += offset to byName
            if (!byName) QueryResult(emptyList(), 0)
            else QueryResult((offset until minOf(offset + 2, 3)).map(::video), 3)
        }
        val first = fetchVideoSearchPage(VideoSearchCursor(series = null), 2, fetch)
        assertTrue(first.nextCursor.videos!!.nameStartsWith)
        val last = fetchVideoSearchPage(first.nextCursor, 2, fetch)
        assertFalse(last.nextCursor.hasMore)
        assertEquals(listOf(0 to false, 0 to true, 2 to true), requests)

        val primary = fetchVideoSearchPage(VideoSearchCursor(series = null), 2) { _, _, _, _ ->
            QueryResult(listOf(video(0), video(1)), 3)
        }
        var calls = 0
        assertFailsWith<IllegalStateException> {
            fetchVideoSearchPage(primary.nextCursor, 2) { _, offset, _, byName ->
                calls++
                assertEquals(2, offset)
                assertFalse(byName)
                error("Network failed")
            }
        }
        assertEquals(1, calls)
    }

    @Test fun duplicateServerPagesFailRatherThanLoopForever(): Unit = runBlocking {
        val fetch: suspend (Boolean, Int, Int, Boolean) -> QueryResult<BaseItemDto> = { _, _, _, _ ->
            QueryResult(listOf(video(0), video(1)), 20)
        }
        val first = fetchVideoSearchPage(VideoSearchCursor(series = null), 2, fetch)
        assertFailsWith<IllegalStateException> { fetchVideoSearchPage(first.nextCursor, 2, fetch) }

        val second = fetchVideoSearchPage(first.nextCursor, 2) { _, _, _, _ ->
            QueryResult(listOf(video(2), video(3)), 20)
        }
        assertFailsWith<IllegalStateException> { fetchVideoSearchPage(second.nextCursor, 2, fetch) }
    }

    @Test fun failedSourceDoesNotCommitTheOtherSourcesCursor() = runBlocking {
        val cursor = VideoSearchCursor()
        assertFailsWith<IllegalStateException> {
            fetchVideoSearchPage(cursor, 2) { videos, _, _, _ ->
                if (videos) QueryResult(listOf(video(0), video(1)), 3) else error("Offline")
            }
        }
        assertEquals(0, cursor.videos?.startIndex)
        assertEquals(0, cursor.series?.startIndex)
        val retry = fetchVideoSearchPage(cursor, 2) { videos, offset, _, _ ->
            assertEquals(0, offset)
            if (videos) QueryResult(listOf(video(0), video(1)), 3) else QueryResult(emptyList(), 0)
        }
        assertEquals(2, retry.nextCursor.videos?.startIndex)
        assertNull(retry.nextCursor.series)
    }

    @Test fun cancellationIsNotConvertedIntoAFallbackSearch() = runBlocking {
        var calls = 0
        assertFailsWith<CancellationException> {
            fetchVideoSearchPage(VideoSearchCursor(series = null), 2) { _, _, _, _ ->
                calls++
                throw CancellationException("New query")
            }
        }
        assertEquals(1, calls)
    }
}
