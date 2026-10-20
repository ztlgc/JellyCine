package com.jellycine.app.ui.screens.dashboard.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SeerrDiscoveryCategory
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.filterSeerTitles
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.SeerrRepository
import com.jellycine.shared.playback.UserDataRefreshSignals
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository
) : ViewModel() {
    private val authRepository = AuthRepositoryProvider.getInstance(context)
    private val seerrRepository = SeerrRepository(context)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDiscoveryTab = MutableStateFlow(SearchDiscoveryTab.SUGGESTIONS)
    val selectedDiscoveryTab: StateFlow<SearchDiscoveryTab> = _selectedDiscoveryTab.asStateFlow()

    private var searchJob: Job? = null
    private var seerrScopeId: String? = null
    private var searchSeerrTitles: List<SeerrRecommendationTitle> = emptyList()
    private val searchPager = SearchPager(
        scope = viewModelScope,
        fetch = { query, cursor -> mediaRepository.searchVideosPage(query, cursor) },
        mergeKey = mediaRepository::versionMergeKey,
        rank = ::rankSearchItems
    )

    init {
        viewModelScope.launch {
            UserDataRefreshSignals.refreshEvent.collect { event ->
                val itemId = event?.itemId
                if (event?.ratingChanged == true && itemId != null &&
                    event.serverId == authRepository.getActiveSessionSnapshot().activeServerId) {
                    searchPager.updateRating(itemId, event.communityRating)
                }
            }
        }
        viewModelScope.launch {
            searchPager.state.collect { paging ->
                if (paging.query == _searchQuery.value) applySearchResults(paging)
            }
        }
        loadsuggestions()
        refreshSeerrConnectionState()
    }

    fun selectDiscoveryTab(tab: SearchDiscoveryTab) {
        if (_selectedDiscoveryTab.value == tab &&
            (tab.seerrCategory == null || _uiState.value.seerrDiscoveryItems.isNotEmpty())
        ) {
            return
        }
        _selectedDiscoveryTab.value = tab
        tab.seerrCategory?.let(::loadSeerrDiscovery)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        startSearch(query, debounceMillis = 300)
    }

    fun executeSearch() {
        val query = _searchQuery.value
        if (query.isNotBlank()) {
            startSearch(query)
        }
    }

    fun loadMoreSearchResults() = searchPager.loadMore()

    private fun startSearch(query: String, debounceMillis: Long = 0) {
        searchJob?.cancel()
        searchSeerrTitles = emptyList()
        searchPager.search(query, debounceMillis)
        applySearchResults(searchPager.state.value)
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(debounceMillis)
            val activeServerId = authRepository.getActiveSessionSnapshot().activeServerId
            val connected = seerrRepository.getSavedConnectionInfo(activeServerId)?.isVerified == true
            val titles = fetchSeerrTitles(query, activeServerId, connected)
            currentCoroutineContext().ensureActive()
            searchSeerrTitles = titles
            applySearchResults(searchPager.state.value)
        }
    }

    private fun loadsuggestions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(SuggestionsLoading = true)

            try {
                mediaRepository.getSuggestions(
                    mediaType = "Movie,Series",
                    limit = 15
                ).fold(
                    onSuccess = { suggestions ->
                        _uiState.value = _uiState.value.copy(
                            suggestions = suggestions,
                            SuggestionsLoading = false,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            SuggestionsLoading = false,
                            error = error.message
                        )
                    }
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    SuggestionsLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun refreshSeerrConnectionState(activeServerId: String? = authRepository.getActiveSessionSnapshot().activeServerId) {
        val scopeId = activeServerId?.takeIf { it.isNotBlank() }
        val scopeChanged = seerrScopeId != scopeId
        val wasSeerrConnected = _uiState.value.isSeerrConnected
        seerrScopeId = scopeId
        if (scopeChanged && _searchQuery.value.isNotBlank()) executeSearch()
        val isSeerrConnected = scopeId != null &&
            seerrRepository.getSavedConnectionInfo(scopeId)?.isVerified == true

        if (!isSeerrConnected) {
            searchJob?.cancel()
            searchSeerrTitles = emptyList()
            _selectedDiscoveryTab.value = SearchDiscoveryTab.SUGGESTIONS
            _uiState.value = _uiState.value.copy(
                isSeerrConnected = false,
                seerrMovieResults = emptyList(),
                seerrShowResults = emptyList(),
                seerrDiscoveryItems = emptyList(),
                seerrDiscoveryLoading = false
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isSeerrConnected = true,
            seerrDiscoveryItems = if (scopeChanged) emptyList() else _uiState.value.seerrDiscoveryItems
        )

        if (_searchQuery.value.isNotBlank() && !scopeChanged && !wasSeerrConnected) {
            executeSearch()
            return
        }

        val category = _selectedDiscoveryTab.value.seerrCategory ?: return
        if ((scopeChanged || _uiState.value.seerrDiscoveryItems.isEmpty()) && !_uiState.value.seerrDiscoveryLoading) {
            loadSeerrDiscovery(category)
        }
    }

    private fun loadSeerrDiscovery(category: SeerrDiscoveryCategory) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                seerrDiscoveryLoading = true
            )

            val activeServerId = authRepository.getActiveSessionSnapshot().activeServerId
            val scopeId = activeServerId?.takeIf { it.isNotBlank() }
            val isSeerrConnected = seerrRepository.getSavedConnectionInfo(activeServerId)?.isVerified == true
            if (scopeId == null || !isSeerrConnected) {
                _uiState.value = _uiState.value.copy(
                    isSeerrConnected = false,
                    seerrDiscoveryItems = emptyList(),
                    seerrDiscoveryLoading = false,
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSeerrConnected = true)

            seerrRepository.getDiscoveryTitles(
                scopeId = scopeId,
                category = category,
                limit = 20
            ).fold(
                onSuccess = { titles ->
                    if (_selectedDiscoveryTab.value.seerrCategory == category) {
                        _uiState.value = _uiState.value.copy(
                            seerrDiscoveryItems = titles.toDiscoveryItems(),
                            seerrDiscoveryLoading = false
                        )
                    }
                },
                onFailure = {
                    if (_selectedDiscoveryTab.value.seerrCategory == category) {
                        _uiState.value = _uiState.value.copy(
                            seerrDiscoveryItems = emptyList(),
                            seerrDiscoveryLoading = false
                        )
                    }
                }
            )
        }
    }

    private fun List<SeerrRecommendationTitle>.toDiscoveryItems(): List<BaseItemDto> =
        distinctBy { title -> "${title.mediaType}:${title.tmdbId}" }
            .map { title ->
                BaseItemDto(
                    id = SeerrItemIds.detailId(tmdbId = title.tmdbId, mediaType = title.mediaType),
                    name = title.title,
                    type = if (title.mediaType.equals("tv", ignoreCase = true)) "Series" else "Movie",
                    providerIds = buildMap {
                        put("tmdb", title.tmdbId)
                        title.jellyfinMediaId?.takeIf { it.isNotBlank() }?.let { put("jellyfin", it) }
                    },
                    productionYear = title.productionYear,
                    communityRating = title.voteAverage?.toFloat(),
                    imageUrl = title.posterUrl
                )
            }

    private fun rankSearchItems(items: List<BaseItemDto>, query: String): List<BaseItemDto> {
        return items
            .sortedBy { item ->
                // A server match may come from metadata rather than the display title.
                bestSearchMatch(query, item.name, item.originalTitle)?.priority ?: Int.MAX_VALUE
            }
    }

    private fun applySearchResults(paging: SearchPagingState) {
        val localItems = paging.items
        val seerrTitles = searchSeerrTitles
        val query = paging.query
        val movieResults = localItems.filter { it.type.equals("Movie", ignoreCase = true) }
        val showResults = localItems.filter { it.type.equals("Series", ignoreCase = true) }
        val episodeResults = localItems.filter { it.type.equals("Episode", ignoreCase = true) }
        val videoResults = localItems.filter { item ->
            listOf("Movie", "Series", "Episode").none { item.type.equals(it, ignoreCase = true) }
        }

        _uiState.value = _uiState.value.copy(
            movieResults = movieResults,
            showResults = showResults,
            episodeResults = episodeResults,
            videoResults = videoResults,
            seerrMovieResults = buildSeerrResults(
                seerrTitles = seerrTitles,
                mediaType = "movie",
                query = query,
                localItems = movieResults
            ),
            seerrShowResults = buildSeerrResults(
                seerrTitles = seerrTitles,
                mediaType = "tv",
                query = query,
                localItems = showResults
            ),
            isSearching = paging.isLoading && localItems.isEmpty() && seerrTitles.isEmpty(),
            isLoadingMore = paging.isLoading && (localItems.isNotEmpty() || seerrTitles.isNotEmpty()),
            hasMoreResults = query.isNotBlank() && paging.cursor.hasMore,
            hasMoreVideos = query.isNotBlank() && paging.cursor.videos != null,
            hasMoreSeries = query.isNotBlank() && paging.cursor.series != null,
            searchLoadFailed = paging.failed,
            searchGeneration = paging.generation,
            loadedSearchPages = paging.loadedPages,
            error = null
        )
    }

    private fun buildSeerrResults(
        seerrTitles: List<SeerrRecommendationTitle>,
        mediaType: String,
        query: String,
        localItems: List<BaseItemDto>
    ): List<SeerrRecommendationTitle> {
        if (seerrTitles.isEmpty()) return emptyList()

        val matchedTitles = seerrTitles
            .filter { title -> title.mediaType.equals(mediaType, ignoreCase = true) }
            .mapNotNull { title ->
                bestSearchMatch(query, title.title)?.let { match -> title to match }
            }
            .sortedBy { (_, match) -> match.priority }
            .map { (title, _) -> title }
            .distinctBy { title -> title.tmdbId }

        return filterSeerTitles(
            seerrTitles = matchedTitles,
            localItems = localItems
        ).take(12)
    }

    private suspend fun fetchSeerrTitles(
        query: String,
        activeServerId: String?,
        isSeerrConnected: Boolean
    ): List<SeerrRecommendationTitle> {
        val scopeId = activeServerId?.takeIf { it.isNotBlank() } ?: return emptyList()
        if (!isSeerrConnected) return emptyList()

        return seerrRepository.searchTitles(
            scopeId = scopeId,
            query = query,
            limit = 20
        ).getOrElse { emptyList() }
    }
}

enum class SearchDiscoveryTab(val seerrCategory: SeerrDiscoveryCategory?) {
    SUGGESTIONS(null),
    TRENDING(SeerrDiscoveryCategory.TRENDING),
    POPULAR_MOVIES(SeerrDiscoveryCategory.POPULAR_MOVIES),
    POPULAR_SHOWS(SeerrDiscoveryCategory.POPULAR_SHOWS),
    UPCOMING_MOVIES(SeerrDiscoveryCategory.UPCOMING_MOVIES),
    UPCOMING_SHOWS(SeerrDiscoveryCategory.UPCOMING_SHOWS)
}

data class SearchUiState(
    val suggestions: List<BaseItemDto> = emptyList(),
    val movieResults: List<BaseItemDto> = emptyList(),
    val showResults: List<BaseItemDto> = emptyList(),
    val episodeResults: List<BaseItemDto> = emptyList(),
    val videoResults: List<BaseItemDto> = emptyList(),
    val seerrMovieResults: List<SeerrRecommendationTitle> = emptyList(),
    val seerrShowResults: List<SeerrRecommendationTitle> = emptyList(),
    val seerrDiscoveryItems: List<BaseItemDto> = emptyList(),
    val isSeerrConnected: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreResults: Boolean = false,
    val hasMoreVideos: Boolean = false,
    val hasMoreSeries: Boolean = false,
    val searchLoadFailed: Boolean = false,
    val searchGeneration: Long = 0,
    val loadedSearchPages: Int = 0,
    val SuggestionsLoading: Boolean = false,
    val seerrDiscoveryLoading: Boolean = false,
    val error: String? = null
)
