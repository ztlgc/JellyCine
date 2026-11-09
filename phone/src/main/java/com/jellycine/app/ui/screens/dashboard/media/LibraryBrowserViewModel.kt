package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.shared.R
import com.jellycine.app.locale.userFacingError
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.shared.playback.UserDataRefreshEvent
import com.jellycine.shared.playback.UserDataRefreshSignals
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
class LibraryBrowserViewModel @Inject constructor(@ApplicationContext private val context: Context) : ViewModel() {
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
    private val collectionMembers = LibraryCollectionMembers.getInstance()
    private val userDataChanges = mutableListOf<UserDataRefreshEvent>()
    private var lastAppliedUserDataEvent: UserDataRefreshEvent? = null
    private var loadedCatalogKey: String? = null
    private val folderSnapshots = mutableMapOf<String, List<BaseItemDto>>()
    private val collectionsMutable = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val collections = collectionsMutable.asStateFlow()
    private val collectionItemsMutable = MutableStateFlow<Map<String, List<BaseItemDto>>>(emptyMap())
    val collectionItems = collectionItemsMutable.asStateFlow()
    private val collectionsLoadingMutable = MutableStateFlow(false)
    val collectionsLoading = collectionsLoadingMutable.asStateFlow()
    private val collectionsErrorMutable = MutableStateFlow<String?>(null)
    val collectionsError = collectionsErrorMutable.asStateFlow()

    init {
        // Keep the retained screen and any in-flight catalog request current while a detail route is open.
        viewModelScope.launch { UserDataRefreshSignals.refreshEvent.collect(::applyUserDataChange) }
    }

    fun open(libraryId: String, refresh: Boolean = false) {
        val session = auth.getActiveSessionSnapshot()
        val server = session.savedServers.firstOrNull { it.id == session.activeServerId }
        val next = libraryCatalogKey(session.activeServerId, session.serverUrl, server?.userId ?: session.username, libraryId)
        // Navigation back to this existing screen must never start another full scan, even after a long video.
        if (key == next && !refresh && (mutable.value.loading || mutable.value.items.isNotEmpty() || loadedCatalogKey == next)) return
        val changed = key != next
        key = next
        val version = ++requestVersion
        job?.cancel(); collectionsJob?.cancel()
        // A failed refresh must keep the previous collection lists; only a different library or
        // session discards them.
        if (changed) {
            collectionsMutable.value = emptyList(); collectionItemsMutable.value = emptyMap()
        }
        collectionsLoadingMutable.value = false; collectionsErrorMutable.value = null
        collectionsLoaded = false
        folderSnapshots.clear()
        if (refresh) collectionMembers.invalidateSession(session.activeServerId.orEmpty())
        if (changed) {
            userDataChanges.clear()
            lastAppliedUserDataEvent = null
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
                // Large libraries arrive page by page; publishing every page re-filters and re-sorts
                // the whole accumulating catalog on the main thread. Coalesce the intermediate pages.
                var lastProgressPublishMs = 0L
                val items = loadCachedLibraryCatalog(catalogCache, next, refresh, onCached = { snapshot ->
                    if (version == requestVersion) mutable.value = mutable.value.copy(
                        items = applyUserDataChanges(snapshot), loaded = snapshot.size, total = snapshot.size)
                }, onProgress = { visible, loaded, total ->
                    val now = System.currentTimeMillis()
                    if (lastProgressPublishMs == 0L || loaded >= total ||
                        now - lastProgressPublishMs >= PROGRESS_PUBLISH_INTERVAL_MS) {
                        lastProgressPublishMs = now
                        withContext(Dispatchers.Main.immediate) {
                            if (version == requestVersion) mutable.value = mutable.value.copy(
                                items = applyUserDataChanges(visible), loaded = loaded, total = total)
                        }
                    }
                }) { onPage ->
                    // Reconcile fresh server data with local user-data changes and durable deletion filters.
                    userDataChanges.clear()
                    applyUserDataChanges(fetchAll(libraryId, true, progress = onPage))
                }
                if (version != requestVersion) return@launch
                val updated = applyUserDataChanges(items)
                loadedCatalogKey = next
                mutable.value = mutable.value.copy(items = updated, loading = false, loaded = updated.size, total = updated.size)
                val changes = userDataChanges.toList()
                catalogCache.updateItems(next) { snapshot ->
                    changes.fold(snapshot) { updatedItems, event -> updatedItems.withLibraryUserData(event) }
                }
                if (mutable.value.tab == LibraryTab.COLLECTIONS) loadCollections(forceRefresh = refresh)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (version == requestVersion) {
                    // Preserve the last complete catalog if a manual refresh fails.
                    val snapshot = if (refresh) catalogCache.peek(next)?.let(::applyUserDataChanges) else null
                    mutable.value = mutable.value.copy(
                        items = snapshot ?: mutable.value.items,
                        loaded = snapshot?.size ?: mutable.value.loaded,
                        total = snapshot?.size ?: mutable.value.total,
                        loading = false, error = context.userFacingError(e, R.string.my_media_load_error))
                }
            }
        }
    }

    fun applyUserDataChange(event: UserDataRefreshEvent?) {
        if (event?.itemId == null || key.isEmpty() || event == lastAppliedUserDataEvent) return
        val serverId = auth.getActiveSessionSnapshot().activeServerId.orEmpty()
        if (!key.startsWith("${serverId.length}:$serverId")) return
        if (event.serverId != null && event.serverId != serverId) return
        lastAppliedUserDataEvent = event
        userDataChanges.add(event)
        val items = mutable.value.items.withLibraryUserData(event)
        val removed = mutable.value.items.size - items.size
        mutable.value = mutable.value.copy(items = items,
            loaded = (mutable.value.loaded - removed).coerceAtLeast(0),
            total = (mutable.value.total - removed).coerceAtLeast(0))
        collectionsMutable.value = collectionsMutable.value.withLibraryUserData(event)
        collectionItemsMutable.value = collectionItemsMutable.value.mapValues { (_, children) -> children.withLibraryUserData(event) }
        folderSnapshots.replaceAll { _, children -> children.withLibraryUserData(event) }
        collectionMembers.updateSession(serverId) { it.withLibraryUserData(event) }
        val catalogKey = key
        viewModelScope.launch { catalogCache.updateItems(catalogKey) { it.withLibraryUserData(event) } }
    }

    private fun applyUserDataChanges(items: List<BaseItemDto>): List<BaseItemDto> {
        val deletedIds = libraryDeletedIds(key)
        return userDataChanges.fold(items) { updated, event -> updated.withLibraryUserData(event) }
            .filterNot { it.id in deletedIds }
    }

    suspend fun loadFolder(folderId: String, forceRefresh: Boolean = false): List<BaseItemDto> {
        if (!forceRefresh) folderSnapshots[folderId]?.let { return it }
        val version = requestVersion
        val items = applyUserDataChanges(fetchAll(folderId, false))
        if (version == requestVersion) folderSnapshots[folderId] = items
        return items
    }

    /** Complete metadata is required for global grouping, file/resolution sorting and stable random pages. */
    suspend fun fetchAll(parentId: String?, recursive: Boolean,
        progress: suspend (List<BaseItemDto>, Int) -> Unit = { _, _ -> }): List<BaseItemDto> =
        withContext(Dispatchers.IO) {
            fetchLibraryCatalog(fetchPage = { offset, limit ->
                // Every call here runs because the current data is missing or expired, so the HTTP
                // cache must not satisfy it: it could be fresh while the catalog policy is not.
                repository.getUserItems(parentId = parentId, recursive = recursive,
                    sortBy = "SortName", sortOrder = "Ascending", startIndex = offset, limit = limit,
                    fields = LIBRARY_FIELDS, enableUserData = true, forceRefresh = true,
                    excludeItemTypes = PHOTO_ITEM_TYPES).getOrThrow()
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
    fun loadCollections(forceRefresh: Boolean = false) {
        if (collectionsLoadingMutable.value || (collectionsLoaded && !forceRefresh)) return
        collectionsLoadingMutable.value = true; collectionsErrorMutable.value = null
        val version = requestVersion
        val session = auth.getActiveSessionSnapshot()
        val serverId = session.activeServerId.orEmpty()
        val user = session.savedServers.firstOrNull { it.id == session.activeServerId }?.userId ?: session.username
        val libraryIds = mutable.value.items.mapNotNull { it.id }.toSet()
        if (forceRefresh) collectionMembers.invalidateSession(serverId)
        collectionsJob = viewModelScope.launch {
            try {
                val candidates = withContext(Dispatchers.IO) {
                    val result = mutableListOf<BaseItemDto>(); var offset = 0
                    while (true) {
                        val page = repository.getUserItems(includeItemTypes = "BoxSet", recursive = true,
                            startIndex = offset, limit = 200, sortBy = "SortName", sortOrder = "Ascending",
                            fields = LIBRARY_FIELDS, enableUserData = true, forceRefresh = true).getOrThrow()
                        val batch = page.items.orEmpty(); result.addAll(batch); offset += batch.size
                        if (batch.isEmpty() || (page.totalRecordCount != null && offset >= page.totalRecordCount!!)) break
                        check(result.mapNotNull { it.id }.distinct().size == result.size) { "Server returned duplicate collection pages" }
                    }
                    result.distinctBy { it.id }
                }
                val contents = linkedMapOf<String, List<BaseItemDto>>()
                val pending = mutableListOf<PendingCollection>()
                for (collection in candidates) {
                    val id = collection.id ?: continue
                    val memberKey = libraryCatalogKey(session.activeServerId, session.serverUrl, user, "collection:$id")
                    val cached = if (forceRefresh) null else collectionMembers.get(memberKey)
                    if (cached != null) {
                        val visible = cached.filter { it.id in libraryIds }
                        if (visible.isNotEmpty()) contents[id] = visible
                    } else {
                        pending += PendingCollection(id, memberKey)
                    }
                }
                // Each scan is a full recursive read. Read a few collections at a time and keep the
                // memberships for other libraries instead of rescanning them from every library.
                val fetched = coroutineScope {
                    buildList {
                        for (batch in pending.chunked(COLLECTION_FETCH_CONCURRENCY)) {
                            addAll(batch.map { entry -> async { entry to fetchAll(entry.id, true) } }.awaitAll())
                        }
                    }
                }
                fetched.forEach { (entry, children) ->
                    collectionMembers.put(entry.key, children)
                    val visible = children.filter { it.id in libraryIds }
                    if (visible.isNotEmpty()) contents[entry.id] = visible
                }
                if (version != requestVersion) return@launch
                collectionItemsMutable.value = contents.mapValues { (_, children) -> applyUserDataChanges(children) }
                collectionsMutable.value = applyUserDataChanges(candidates.filter { it.id in contents })
                collectionsLoaded = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { collectionsErrorMutable.value = context.userFacingError(e, R.string.my_media_load_error) }
            finally { if (version == requestVersion) collectionsLoadingMutable.value = false }
        }
    }
    private fun save() {
        preferences.edit().putString("$key.tab", mutable.value.tab.name)
            .putString("$key.sort", mutable.value.sort.name).putBoolean("$key.desc", mutable.value.descending)
            .putBoolean("$key.list", mutable.value.listView).apply()
    }
    private inline fun <reified T : Enum<T>> enumValue(name: String?): T? = enumValues<T>().firstOrNull { it.name == name }

    private data class PendingCollection(val id: String, val key: String)

    internal companion object {
        // Request the rating explicitly so list results do not depend on a detail-page read.
        const val LIBRARY_FIELDS = "Path,SortName,MediaStreams,MediaSources,Width,Height,DateCreated,Genres,Tags,ParentId,ChildCount,CommunityRating"
        // Photos are hidden from every browser view; leave folders and PhotoAlbum navigable.
        private const val PHOTO_ITEM_TYPES = "Photo"
        private const val COLLECTION_FETCH_CONCURRENCY = 4
        private const val PROGRESS_PUBLISH_INTERVAL_MS = 400L
    }
}
