package com.jellycine.app.ui.screens.dashboard.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jellycine.app.R
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.shared.playback.UserDataRefreshSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val LibraryBlue = Color(0xFFA5C9FF)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryBrowserScreen(
    libraryId: String, title: String, contentType: ContentType,
    onBack: () -> Unit, onItemClick: (BaseItemDto) -> Unit, onPlayQueue: (List<String>) -> Unit,
    model: LibraryBrowserViewModel = hiltViewModel()
) {
    val state by model.state.collectAsStateWithLifecycle()
    val collections by model.collections.collectAsStateWithLifecycle()
    val collectionContents by model.collectionItems.collectAsStateWithLifecycle()
    val collectionsLoading by model.collectionsLoading.collectAsStateWithLifecycle()
    val collectionsError by model.collectionsError.collectAsStateWithLifecycle()
    val refresh by UserDataRefreshSignals.refreshEvent.collectAsState()
    val deletedItems by UserDataRefreshSignals.deletedItems.collectAsState()
    val activeSessionId = com.jellycine.data.repository.AuthRepositoryProvider.getInstance(LocalContext.current)
        .getActiveSessionSnapshot().activeServerId
    val deletedIds = deletedItems[activeSessionId].orEmpty()
    val context = LocalContext.current
    LaunchedEffect(libraryId) { model.open(libraryId) }
    LaunchedEffect(libraryId, refresh) { model.applyUserDataChange(refresh) }
    var folderIds by rememberSaveable(libraryId) { mutableStateOf(listOf(libraryId)) }
    var folderNames by rememberSaveable(libraryId) { mutableStateOf(listOf(title)) }
    var group by rememberSaveable(libraryId, state.tab) { mutableStateOf<String?>(null) }
    var sortSheet by remember { mutableStateOf(false) }
    var filterSheet by remember { mutableStateOf(false) }
    var folderItems by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var folderLoading by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var folderRetry by remember { mutableIntStateOf(0) }
    var handledFolderRetry by remember(libraryId) { mutableIntStateOf(0) }
    var loadedFolderId by remember(libraryId) { mutableStateOf<String?>(null) }
    val folderId = folderIds.last()
    LaunchedEffect(state.tab, folderId, folderRetry) {
        if (state.tab == LibraryTab.FOLDERS) {
            val forceRefresh = folderRetry != handledFolderRetry
            handledFolderRetry = folderRetry
            folderLoading = true; folderError = null
            if (loadedFolderId != folderId) folderItems = emptyList()
            try {
                folderItems = model.loadFolder(folderId, forceRefresh)
                loadedFolderId = folderId
            }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { folderError = e.message }
            finally { folderLoading = false }
        }
    }
    fun back() {
        when {
            state.tab == LibraryTab.FOLDERS && folderIds.size > 1 -> {
                folderIds = folderIds.dropLast(1); folderNames = folderNames.dropLast(1)
            }
            group != null -> group = null
            else -> onBack()
        }
    }
    BackHandler { back() }
    val media = remember(state.items, contentType) { libraryMedia(state.items, contentType) }
    val grouped = state.tab == LibraryTab.TAGS && group == null
    val collectionRoot = state.tab == LibraryTab.COLLECTIONS && group == null
    val source = remember(media, state.items, state.tab, folderItems, group, collectionContents, collections) {
        when (state.tab) {
            LibraryTab.ALL -> media
            LibraryTab.RESUME -> state.items.filter { it.isLibraryPlayable() && (it.userData?.playbackPositionTicks ?: 0) > 0 && it.userData?.played != true }
            LibraryTab.FAVORITES -> media.filter { it.userData?.isFavorite == true }
            LibraryTab.FOLDERS -> {
                // The catalog carries refreshed metadata (for example the community rating) and
                // user data; the folder query only decides which children this level contains.
                val catalog = state.items.associateBy { it.id }
                folderItems.map { item -> catalog[item.id] ?: item }
            }
            LibraryTab.COLLECTIONS -> if (group == null) collections else libraryMedia(collectionContents[group].orEmpty(), contentType)
            LibraryTab.TAGS -> media.filter { group == null || group in it.tags.orEmpty() }
        }
    }
    val filtered = remember(source, state.items, state.genres, state.watched, collectionContents, state.tab) {
        filterLibrary(source, state.items, state.genres, state.watched, collectionContents, state.tab == LibraryTab.FOLDERS)
    }
    val display = remember(filtered, state.sort, state.descending, state.randomSeed, deletedIds) {
        sortLibrary(filtered.filterNot { it.id in deletedIds }, state.sort, state.descending, state.randomSeed)
    }
    val groups = remember(filtered, grouped, state.tab, state.sort, state.descending, state.randomSeed) {
        if (!grouped) emptyList() else filtered.flatMap { it.tags.orEmpty() }
            .filter { it.isNotBlank() }.distinct().let { names ->
                sortLibrary(names.map { BaseItemDto(id = it, name = it) }, state.sort, state.descending, state.randomSeed).map { it.name!! }
            }
    }
    val busy = state.loading || (state.tab == LibraryTab.FOLDERS && folderLoading) ||
        (state.tab == LibraryTab.COLLECTIONS && collectionsLoading)
    val error = state.error ?: if (state.tab == LibraryTab.FOLDERS) folderError else if (state.tab == LibraryTab.COLLECTIONS) collectionsError else null
    val gridState = rememberLazyGridState()
    var visibleCount by rememberSaveable(libraryId, state.tab, group, folderId, state.sort, state.descending, state.randomSeed, state.genres, state.watched) { mutableIntStateOf(60) }
    val scrollIdentity = "$libraryId:${state.tab}:$group:$folderId:${state.sort}:${state.descending}:${state.randomSeed}:${state.genres}:${state.watched}"
    var lastScrollIdentity by rememberSaveable(libraryId) { mutableStateOf<String?>(null) }
    LaunchedEffect(scrollIdentity) {
        if (lastScrollIdentity != null && lastScrollIdentity != scrollIdentity) gridState.scrollToItem(0)
        lastScrollIdentity = scrollIdentity
    }
    val genres = remember(media) { media.flatMap { it.genres.orEmpty() }.distinct().sorted() }
    val playable = if (grouped || collectionRoot || busy || error != null) emptyList() else libraryPlayableQueue(display, state.items)
        .filter { state.watched == null || (it.userData?.played == true) == state.watched }
    val selectedTitle = if (state.tab == LibraryTab.FOLDERS) folderNames.last() else
        if (state.tab == LibraryTab.COLLECTIONS && group != null) collections.firstOrNull { it.id == group }?.name ?: title else group ?: title

    Column(Modifier.fillMaxSize().background(Color(0xFF101114)).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.library_back), tint = Color.White) }
            Text(selectedTitle, color = Color.White, style = MaterialTheme.typography.headlineSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { model.open(libraryId, refresh = true); folderRetry++ }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = LibraryBlue, strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, stringResource(R.string.library_refresh), tint = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            LibraryTab.entries.forEach { tab ->
                Column(Modifier.heightIn(min = 48.dp).clickable { group = null; model.tab(tab) }.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(tab.label), color = if (state.tab == tab) LibraryBlue else Color(0xFFBABDC4))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(30.dp).height(3.dp).background(if (state.tab == tab) LibraryBlue else Color.Transparent, RoundedCornerShape(3.dp)))
                }
            }
        }
        LibraryBrowserToolbar(
            countText = if (busy && source.isEmpty()) stringResource(R.string.library_loading_count, state.loaded, state.total)
                else stringResource(R.string.library_count, if (grouped) groups.size else display.size),
            sortLabel = stringResource(state.sort.label),
            descending = state.descending,
            randomSort = state.sort == LibrarySort.RANDOM,
            listView = state.listView,
            hasFilters = state.genres.isNotEmpty() || state.watched != null,
            canShuffle = playable.isNotEmpty(),
            onSort = { sortSheet = true },
            onDirection = model::direction,
            onView = model::view,
            onFilter = { filterSheet = true },
            onShuffle = { onPlayQueue(playable.shuffled().mapNotNull { it.id }) }
        )
        if (error != null && source.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.library_error), color = Color.Gray, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (state.error != null) model.open(libraryId, true) else when (state.tab) {
                        LibraryTab.FOLDERS -> folderRetry++
                        LibraryTab.COLLECTIONS -> model.loadCollections(forceRefresh = true)
                        else -> model.open(libraryId, true)
                    }
                }) { Text(stringResource(R.string.library_retry)) }
            }
        }
        when {
            busy && source.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LibraryBlue) }
            error != null && source.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.library_error), color = Color.White)
                Text(error, color = Color.Gray, maxLines = 3)
                Button(onClick = {
                    if (state.error != null) model.open(libraryId, true) else when (state.tab) {
                        LibraryTab.FOLDERS -> folderRetry++
                        LibraryTab.COLLECTIONS -> model.loadCollections(forceRefresh = true)
                        else -> model.open(libraryId, true)
                    }
                }) { Text(stringResource(R.string.library_retry)) }
            }
            (grouped && groups.isEmpty()) || (!grouped && display.isEmpty()) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.library_empty), color = Color.Gray)
            }
            else -> LazyVerticalGrid(columns = GridCells.Fixed(if (state.listView || grouped) 1 else 2),
                state = gridState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (grouped) items(groups, key = { it }) { name ->
                    Surface(onClick = { group = name }, color = Color(0xFF1C1E22), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, color = Color.White)
                            Text(filtered.count { name in it.tags.orEmpty() }.toString(), color = Color.Gray)
                        }
                    }
                } else {
                    items(display.take(visibleCount), key = { it.id!! }) { item ->
                        LibraryBrowserCard(item, state.listView) {
                            when {
                                collectionRoot -> group = item.id
                                state.tab == LibraryTab.FOLDERS && (item.isFolder == true || item.type == "Folder" || item.type == "PhotoAlbum") -> {
                                    folderIds = folderIds + item.id!!; folderNames = folderNames + item.name.orEmpty()
                                }
                                contentType == ContentType.HOME_MEDIA && item.isLibraryPlayable() -> onItemClick(item)
                                else -> onItemClick(item)
                            }
                        }
                    }
                    if (visibleCount < display.size) item(span = { GridItemSpan(maxLineSpan) }) {
                        LaunchedEffect(visibleCount) { visibleCount += 60 }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LibraryBlue) }
                    }
                }
            }
        }
    }
    if (sortSheet) ModalBottomSheet(onDismissRequest = { sortSheet = false }, containerColor = Color(0xFF101114),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(stringResource(R.string.library_sort), color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            LibrarySort.entries.forEach { sort ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { model.sort(sort); sortSheet = false }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(sort.label), color = if (sort == state.sort) LibraryBlue else Color.White, modifier = Modifier.weight(1f))
                    if (sort == state.sort) Icon(if (sort == LibrarySort.RANDOM) Icons.Default.Check else
                        if (state.descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = LibraryBlue)
                }
            }
        }
    }
    if (filterSheet) ModalBottomSheet(onDismissRequest = { filterSheet = false }, containerColor = Color(0xFF101114),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        var draftGenres by remember { mutableStateOf(state.genres) }
        var draftWatched by remember { mutableStateOf(state.watched) }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.library_filter), color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.library_play_state), color = Color.Gray, modifier = Modifier.padding(top = 20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to R.string.library_all, true to R.string.library_watched, false to R.string.library_unwatched).forEach { (value, label) ->
                    FilterChip(selected = draftWatched == value, onClick = { draftWatched = value }, label = { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.library_genres), color = Color.Gray, modifier = Modifier.padding(top = 20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.forEach { genre -> FilterChip(selected = genre in draftGenres, onClick = {
                    draftGenres = if (genre in draftGenres) draftGenres - genre else draftGenres + genre
                }, label = { Text(genre) }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { draftGenres = emptySet(); draftWatched = null }) { Text(stringResource(R.string.library_clear)) }
                Button(onClick = { model.filters(draftGenres, draftWatched); filterSheet = false }) { Text(stringResource(R.string.library_apply)) }
            }
        }
    }
}

@Composable
internal fun LibraryBrowserToolbar(
    countText: String,
    sortLabel: String,
    descending: Boolean,
    randomSort: Boolean,
    listView: Boolean,
    hasFilters: Boolean,
    canShuffle: Boolean,
    onSort: () -> Unit,
    onDirection: () -> Unit,
    onView: () -> Unit,
    onFilter: () -> Unit,
    onShuffle: () -> Unit
) {
    var moreExpanded by remember { mutableStateOf(false) }
    val actionSize = 40.dp
    @Composable
    fun Actions() {
        IconButton(onClick = onView, modifier = Modifier.size(actionSize)) {
            Icon(if (listView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                stringResource(R.string.library_view), tint = Color.White)
        }
        IconButton(onClick = onFilter, modifier = Modifier.size(actionSize)) {
            Icon(Icons.Default.FilterAlt, stringResource(R.string.library_filter),
                tint = if (hasFilters) LibraryBlue else Color.White)
        }
        Box {
            IconButton(onClick = { moreExpanded = true }, modifier = Modifier.size(actionSize)) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.library_more), tint = Color.White)
            }
            DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_shuffle)) },
                    leadingIcon = { Icon(Icons.Default.Shuffle, null) },
                    enabled = canShuffle,
                    onClick = { moreExpanded = false; onShuffle() }
                )
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val sortLabelWidth = (maxWidth - 296.dp).coerceIn(24.dp, 64.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(countText, color = Color(0xFFBABDC4), style = MaterialTheme.typography.bodySmall,
                maxLines = 1, softWrap = false,
                modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE))
            Spacer(Modifier.width(8.dp))
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Actions()
                }
                Spacer(Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSort, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Sort, null, tint = LibraryBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(sortLabel, color = LibraryBlue, maxLines = 1, softWrap = false,
                            modifier = Modifier.width(sortLabelWidth).basicMarquee(iterations = Int.MAX_VALUE))
                    }
                    if (!randomSort) IconButton(onClick = onDirection, modifier = Modifier.size(actionSize)) {
                        Icon(if (descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            stringResource(R.string.library_direction), tint = LibraryBlue)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryBrowserCard(
    item: BaseItemDto, listView: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { MediaRepositoryProvider.getInstance(context) }
    val imageTag = item.imageTags?.get("Primary")
    // A refresh can replace the artwork without changing the item id; keep the tag in the key so
    // the card requests the new image instead of continuing to show the old URL.
    var image by remember(item.id, imageTag) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id, imageTag) {
        image = withContext(Dispatchers.IO) { repository.getImageUrlString(item.id!!, width = 360,
            enableImageEnhancers = false, imageTag = imageTag, autoOrient = true) }
    }
    val folder = item.isFolder == true || item.type in setOf("Folder", "PhotoAlbum", "BoxSet", "Series")
    val video = !folder && (item.isLibraryPlayable() && item.type != "Audio")
    val rating = if (video) item.communityRating?.takeIf { it.isFinite() && it in 0f..10f } else null
    @Composable fun Artwork(modifier: Modifier) {
        Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF22252A)), contentAlignment = Alignment.Center) {
            if (image != null) AsyncImage(image, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(if (folder) Icons.Default.Folder else Icons.Default.Image, null, tint = Color.Gray)
            rating?.let { score ->
                Text(
                    text = String.format(Locale.getDefault(), "%.1f", score),
                    color = Color(0xFF102A43),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .clip(CutCornerShape(topEnd = 10.dp))
                        .background(LibraryBlue)
                        .padding(start = 6.dp, end = 12.dp, top = 3.dp, bottom = 3.dp)
                )
            }
            if (item.userData?.isFavorite == true) Icon(Icons.Default.Favorite, null, tint = LibraryBlue,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp))
            if (item.userData?.played == true) Icon(Icons.Default.CheckCircle, null, tint = LibraryBlue,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(16.dp))
        }
    }
    if (listView) Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(Modifier.width(60.dp).height(84.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.name.orEmpty(), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(item.productionYear?.toString(), item.officialRating,
                item.runTimeTicks?.let { stringResource(R.string.library_minutes, it / 600000000L) }).joinToString(" · ")
            Text(detail, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    } else Column(Modifier.clickable(onClick = onClick)) {
        Artwork(Modifier.fillMaxWidth().aspectRatio(if (item.type in setOf("Video", "Folder", "PhotoAlbum")) 4f / 3 else 2f / 3))
        Text(item.name.orEmpty(), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}
