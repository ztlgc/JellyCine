package com.jellycine.data.repository

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.QueryResult
import com.jellycine.data.model.SearchSourceCursor
import com.jellycine.data.model.VideoSearchCursor
import com.jellycine.data.model.VideoSearchPage
import com.jellycine.data.model.isVideoSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Fetch one page per unfinished source. Commit neither cursor if either request fails. */
internal suspend fun fetchVideoSearchPage(
    cursor: VideoSearchCursor,
    pageSize: Int,
    fetch: suspend (videos: Boolean, startIndex: Int, limit: Int, nameStartsWith: Boolean) -> QueryResult<BaseItemDto>
): VideoSearchPage = coroutineScope {
    require(pageSize > 0)

    suspend fun sourcePage(videos: Boolean, source: SearchSourceCursor?): Pair<List<BaseItemDto>, SearchSourceCursor?> {
        if (source == null) return emptyList<BaseItemDto>() to null
        var byName = source.nameStartsWith
        val response = if (source.startIndex == 0 && !byName) {
            val primary = try {
                fetch(videos, 0, pageSize, false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (primary?.items.isNullOrEmpty()) {
                byName = true
                fetch(videos, 0, pageSize, true)
            } else primary
        } else {
            // Never switch search modes on a later page: the offsets belong to one query.
            fetch(videos, source.startIndex, pageSize, byName)
        }
        val batch = response.items.orEmpty()
        val ids = batch.mapNotNull { it.id }.toSet()
        check(batch.isEmpty() || ids.isEmpty() || !source.seenItemIds.containsAll(ids)) {
            "Server returned duplicate search pages"
        }
        // Advance by raw server rows, before filtering or merging alternate versions.
        val nextIndex = source.startIndex + batch.size
        val total = response.totalRecordCount?.takeIf { it >= 0 }
        // Without a total, request until an empty page; some servers cap each response below pageSize.
        val finished = batch.isEmpty() || (total != null && nextIndex >= total)
        val next = if (finished) null else SearchSourceCursor(nextIndex, byName, source.seenItemIds + ids)
        return batch.filter { it.isVideoSearchResult() } to next
    }

    val videos = async { sourcePage(true, cursor.videos) }
    val series = async { sourcePage(false, cursor.series) }
    val videoPage = videos.await()
    val seriesPage = series.await()
    VideoSearchPage(videoPage.first + seriesPage.first, VideoSearchCursor(videoPage.second, seriesPage.second))
}
