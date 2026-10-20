package com.jellycine.app.ui.screens.dashboard.search

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.VideoSearchCursor
import com.jellycine.data.model.VideoSearchPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class SearchPagingState(
    val query: String = "",
    val generation: Long = 0,
    val loadedPages: Int = 0,
    val items: List<BaseItemDto> = emptyList(),
    val cursor: VideoSearchCursor = VideoSearchCursor(),
    val isLoading: Boolean = false,
    val failed: Boolean = false
)

/** Owns request cancellation and append order independently of the Android screen. */
internal class SearchPager(
    private val scope: CoroutineScope,
    private val fetch: suspend (String, VideoSearchCursor) -> Result<VideoSearchPage>,
    private val mergeKey: (BaseItemDto) -> String,
    private val rank: (List<BaseItemDto>, String) -> List<BaseItemDto>
) {
    private val mutableState = MutableStateFlow(SearchPagingState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private val ratingChanges = mutableMapOf<String, Float?>()

    fun updateRating(itemId: String, rating: Float?) {
        ratingChanges[itemId] = rating
        mutableState.value = state.value.copy(items = applyRatings(state.value.items))
    }

    private fun applyRatings(items: List<BaseItemDto>) = items.map { item ->
        if (ratingChanges.containsKey(item.id)) item.copy(communityRating = ratingChanges[item.id]) else item
    }

    fun search(query: String, debounceMillis: Long = 0) {
        job?.cancel()
        ratingChanges.clear()
        val initial = SearchPagingState(
            query = query, generation = state.value.generation + 1, isLoading = query.isNotBlank()
        )
        mutableState.value = initial
        if (query.isBlank()) return
        job = scope.launch {
            delay(debounceMillis)
            request(initial)
        }
    }

    fun loadMore() {
        val current = state.value
        if (current.query.isBlank() || current.isLoading || !current.cursor.hasMore) return
        mutableState.value = current.copy(isLoading = true, failed = false)
        job = scope.launch { request(current) }
    }

    private suspend fun request(request: SearchPagingState) {
        val result = try {
            fetch(request.query, request.cursor)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
        currentCoroutineContext().ensureActive()
        if (request.generation != state.value.generation) return
        result.fold(
            onSuccess = { page ->
                mutableState.value = request.copy(
                    // Rank only the new batch so appending cannot move cards already on screen.
                    items = applyRatings((request.items + rank(page.items, request.query)).distinctBy(mergeKey)),
                    cursor = page.nextCursor,
                    loadedPages = request.loadedPages + 1,
                    isLoading = false,
                    failed = false
                )
            },
            onFailure = {
                // Keep both the items and the cursor so retry requests exactly the failed page.
                mutableState.value = request.copy(items = applyRatings(request.items), isLoading = false, failed = true)
            }
        )
    }
}
