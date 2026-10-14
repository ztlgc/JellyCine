package com.jellycine.app.ui.screens.dashboard.media

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LibraryBrowserState(
    val items: List<BaseItemDto> = emptyList(), val loading: Boolean = false,
    val loaded: Int = 0, val total: Int = 0, val error: String? = null,
    val tab: LibraryTab = LibraryTab.ALL, val sort: LibrarySort = LibrarySort.CREATED,
    val descending: Boolean = true, val listView: Boolean = false,
    val genres: Set<String> = emptySet(), val watched: Boolean? = null,
    val randomSeed: Int = 0
)

@HiltViewModel
class LibraryBrowserViewModel @Inject constructor(@ApplicationContext context: Context) : ViewModel() {
    private val repository = MediaRepositoryProvider.getInstance(context)
    private val auth = AuthRepositoryProvider.getInstance(context)
    private val preferences = context.getSharedPreferences("library_browser", Context.MODE_PRIVATE)
    private val catalogCache = LibraryCatalogCache.getInstance(context.cacheDir)
    private val mutable = MutableStateFlow(LibraryBrowserState())
    val state = mutable.asStateFlow()
    private var key = ""
    private var job: Job? = null
    private var collectionsLoaded = false
    private var requestVersion = 0
    private var collectionsJob: Job? = null
    private val collectionsMutable = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val collections = collectionsMutable.asStateFlow()
    private val collectionItemsMutable = MutableStateFlow<Map<String, List<BaseItemDto>>>(emptyMap())
    val collectionItems = collectionItemsMutable.asStateFlow()
    private val collectionsLoadingMutable = MutableStateFlow(false)
    val collectionsLoading = collectionsLoadingMutable.asStateFlow()
    private val collectionsErrorMutable = MutableStateFlow<String?>(null)
    val collectionsError = collectionsErrorMutable.asStateFlow()

    fun open(libraryId: String, refresh: Boolean = false) {
        val session = auth.getActiveSessionSnapshot()
        val server = session.savedServers.firstOrNull { it.id == session.activeServerId }
        val next = libraryCatalogKey(session.activeServerId, session.serverUrl, server?.userId ?: session.username, libraryId)
        if (key == next && !refresh && (mutable.value.loading || mutable.value.items.isNotEmpty())) return
        val changed = key != next
        key = next
        val version = ++requestVersion
        job?.cancel(); collectionsJob?.cancel()
        collectionsMutable.value = emptyList(); collectionItemsMutable.value = emptyMap()
        collectionsLoadingMutable.value = false; collectionsErrorMutable.value = null
        collectionsLoaded = false
        if (changed) {
            val legacyKey = "${session.activeServerId}|$libraryId"
            mutable.value = LibraryBrowserState(
                tab = enumValue<LibraryTab>(preferences.getString("$key.tab", preferences.getString("$legacyKey.tab", null))) ?: LibraryTab.ALL,
                sort = enumValue<LibrarySort>(preferences.getString("$key.sort", preferences.getString("$legacyKey.sort", null))) ?: LibrarySort.CREATED,
                descending = preferences.getBoolean("$key.desc", preferences.getBoolean("$legacyKey.desc", true)),
                listView = preferences.getBoolean("$key.list", preferences.getBoolean("$legacyKey.list", false))
            )
        }
        val cached = catalogCache.peek(next)
        val initialItems = cached ?: mutable.value.items
        mutable.value = mutable.value.copy(loading = true, items = initialItems,
            loaded = initialItems.size, total = initialItems.size, error = null)
        job = viewModelScope.launch {
            try {
                if (cached == null && mutable.value.items.isEmpty()) {
                    catalogCache.load(next)?.let { snapshot ->
                        if (version == requestVersion) mutable.value = mutable.value.copy(
                            items = snapshot, loaded = snapshot.size, total = snapshot.size)
                    }
                }
                // Keep a complete cached catalog stable until the entire refresh succeeds.
                val hasSnapshot = catalogCache.peek(next) != null
                val items = fetchAll(libraryId, true) { batch, total ->
                    withContext(Dispatchers.Main.immediate) {
                        if (version == requestVersion) mutable.value = mutable.value.copy(
                            items = if (hasSnapshot) mutable.value.items else batch,
                            loaded = batch.size, total = total)
                    }
                }
                if (version != requestVersion) return@launch
                mutable.value = mutable.value.copy(items = items, loading = false, loaded = items.size, total = items.size)
                catalogCache.save(next, items)
                if (mutable.value.tab == LibraryTab.COLLECTIONS) loadCollections()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (version == requestVersion) mutable.value = mutable.value.copy(loading = false, error = e.message ?: "Error")
            }
        }
    }

    /** Complete metadata is required for global grouping, file/resolution sorting and stable random pages. */
    suspend fun fetchAll(parentId: String?, recursive: Boolean, progress: suspend (List<BaseItemDto>, Int) -> Unit = { _, _ -> }): List<BaseItemDto> =
        withContext(Dispatchers.IO) {
            fetchLibraryCatalog(fetchPage = { offset, limit ->
                repository.getUserItems(parentId = parentId, recursive = recursive,
                    sortBy = "SortName", sortOrder = "Ascending", startIndex = offset, limit = limit,
                    fields = LIBRARY_FIELDS,
                    enableUserData = true).getOrThrow()
            }, onPage = progress)
        }

    fun tab(tab: LibraryTab) {
        mutable.value = mutable.value.copy(tab = tab); save()
        if (tab == LibraryTab.COLLECTIONS && !mutable.value.loading) loadCollections()
    }
    fun sort(sort: LibrarySort) {
        mutable.value = mutable.value.copy(sort = sort,
            descending = if (sort == mutable.value.sort) !mutable.value.descending else sort.descendingDefault,
            randomSeed = if (sort == LibrarySort.RANDOM) mutable.value.randomSeed + 1 else mutable.value.randomSeed)
        save()
    }
    fun direction() { mutable.value = mutable.value.copy(descending = !mutable.value.descending); save() }
    fun view() { mutable.value = mutable.value.copy(listView = !mutable.value.listView); save() }
    fun filters(genres: Set<String>, watched: Boolean?) { mutable.value = mutable.value.copy(genres = genres, watched = watched) }
    fun loadCollections() {
        if (collectionsLoadingMutable.value || collectionsLoaded) return
        collectionsLoadingMutable.value = true; collectionsErrorMutable.value = null
        val version = requestVersion
        val libraryIds = mutable.value.items.mapNotNull { it.id }.toSet()
        collectionsJob = viewModelScope.launch {
            try {
                val candidates = withContext(Dispatchers.IO) {
                    val result = mutableListOf<BaseItemDto>(); var offset = 0
                    while (true) {
                        val page = repository.getUserItems(includeItemTypes = "BoxSet", recursive = true,
                            startIndex = offset, limit = 200, sortBy = "SortName", sortOrder = "Ascending",
                            fields = LIBRARY_FIELDS, enableUserData = true).getOrThrow()
                        val batch = page.items.orEmpty(); result.addAll(batch); offset += batch.size
                        if (batch.isEmpty() || (page.totalRecordCount != null && offset >= page.totalRecordCount!!)) break
                        check(result.mapNotNull { it.id }.distinct().size == result.size) { "Server returned duplicate collection pages" }
                    }
                    result.distinctBy { it.id }
                }
                val contents = linkedMapOf<String, List<BaseItemDto>>()
                for (collection in candidates) {
                    val id = collection.id ?: continue
                    val children = fetchAll(id, true).filter { it.id in libraryIds }
                    if (children.isNotEmpty()) contents[id] = children
                }
                collectionItemsMutable.value = contents
                collectionsMutable.value = candidates.filter { it.id in contents }
                collectionsLoaded = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { collectionsErrorMutable.value = e.message }
            finally { if (version == requestVersion) collectionsLoadingMutable.value = false }
        }
    }
    private fun save() {
        preferences.edit().putString("$key.tab", mutable.value.tab.name)
            .putString("$key.sort", mutable.value.sort.name).putBoolean("$key.desc", mutable.value.descending)
            .putBoolean("$key.list", mutable.value.listView).apply()
    }
    private inline fun <reified T : Enum<T>> enumValue(name: String?): T? = enumValues<T>().firstOrNull { it.name == name }

    private companion object {
        // Rating, year, runtime and user data are standard DTO fields; request only valid ItemFields here.
        const val LIBRARY_FIELDS = "Path,SortName,MediaStreams,MediaSources,Width,Height,DateCreated,Genres,Tags,ParentId,ChildCount"
    }
}
