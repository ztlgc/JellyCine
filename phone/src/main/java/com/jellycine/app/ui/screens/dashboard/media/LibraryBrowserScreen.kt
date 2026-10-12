package com.jellycine.app.ui.screens.dashboard.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jellycine.app.R
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.shared.playback.UserDataRefreshSignals
import com.jellycine.shared.ui.home.LibraryPhotoViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val repository = MediaRepositoryProvider.getInstance(LocalContext.current)
    var previousRefresh by remember { mutableStateOf(refresh) }
    LaunchedEffect(libraryId, refresh) {
        model.open(libraryId, refresh != previousRefresh); previousRefresh = refresh
    }
    var folderIds by rememberSaveable(libraryId) { mutableStateOf(listOf(libraryId)) }
    var folderNames by rememberSaveable(libraryId) { mutableStateOf(listOf(title)) }
    var group by rememberSaveable(libraryId, state.tab) { mutableStateOf<String?>(null) }
    var sortSheet by remember { mutableStateOf(false) }
    var filterSheet by remember { mutableStateOf(false) }
    var photoId by rememberSaveable(libraryId) { mutableStateOf<String?>(null) }
    var folderItems by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var folderLoading by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var folderRetry by remember { mutableIntStateOf(0) }
    val folderId = folderIds.last()
    LaunchedEffect(state.tab, folderId, folderRetry, state.loaded, state.loading) {
        if (state.tab == LibraryTab.FOLDERS && !state.loading) {
            folderLoading = true; folderError = null; folderItems = emptyList()
            try { folderItems = model.fetchAll(folderId, false) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { folderError = e.message }
            finally { folderLoading = false }
        }
    }
    fun back() {
        when {
            photoId != null -> photoId = null
            state.tab == LibraryTab.FOLDERS && folderIds.size > 1 -> {
                folderIds = folderIds.dropLast(1); folderNames = folderNames.dropLast(1)
            }
            group != null -> group = null
            else -> onBack()
        }
    }
    BackHandler { back() }
    val media = remember(state.items, contentType) { libraryMedia(state.items, contentType) }
    val grouped = state.tab in setOf(LibraryTab.GENRES, LibraryTab.TAGS) && group == null
    val collectionRoot = state.tab == LibraryTab.COLLECTIONS && group == null
    val source = remember(media, state.items, state.tab, folderItems, group, collectionContents, collections) {
        when (state.tab) {
            LibraryTab.ALL -> media
            LibraryTab.RESUME -> state.items.filter { it.isLibraryPlayable() && (it.userData?.playbackPositionTicks ?: 0) > 0 && it.userData?.played != true }
            LibraryTab.FAVORITES -> media.filter { it.userData?.isFavorite == true }
            LibraryTab.FOLDERS -> folderItems
            LibraryTab.COLLECTIONS -> if (group == null) collections else libraryMedia(collectionContents[group].orEmpty(), contentType)
            LibraryTab.GENRES -> media.filter { group == null || group in it.genres.orEmpty() }
            LibraryTab.TAGS -> media.filter { group == null || group in it.tags.orEmpty() }
        }
    }
    val filtered = remember(source, state.items, state.genres, state.watched, collectionContents, state.tab) {
        filterLibrary(source, state.items, state.genres, state.watched, collectionContents, state.tab == LibraryTab.FOLDERS)
    }
    val display = remember(filtered, state.sort, state.descending, state.randomSeed) {
        sortLibrary(filtered, state.sort, state.descending, state.randomSeed)
    }
    val groups = remember(filtered, state.tab, state.sort, state.descending, state.randomSeed) {
        filtered.flatMap { if (state.tab == LibraryTab.GENRES) it.genres.orEmpty() else it.tags.orEmpty() }
            .filter { it.isNotBlank() }.distinct().let { names ->
                sortLibrary(names.map { BaseItemDto(id = it, name = it) }, state.sort, state.descending, state.randomSeed).map { it.name!! }
            }
    }
    val busy = state.loading || (state.tab == LibraryTab.FOLDERS && folderLoading) ||
        (state.tab == LibraryTab.COLLECTIONS && collectionsLoading)
    val error = state.error ?: if (state.tab == LibraryTab.FOLDERS) folderError else if (state.tab == LibraryTab.COLLECTIONS) collectionsError else null
    val gridState = rememberLazyGridState()
    var visibleCount by remember(libraryId, state.tab, group, folderId, state.sort, state.descending, state.randomSeed, state.genres, state.watched) { mutableIntStateOf(60) }
    LaunchedEffect(state.tab, group, folderId, state.sort, state.descending, state.randomSeed, state.genres, state.watched) { gridState.scrollToItem(0) }
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
            IconButton(onClick = { model.open(libraryId, true); folderRetry++ }, enabled = !busy) {
                Icon(Icons.Default.Refresh, stringResource(R.string.library_refresh), tint = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            LibraryTab.entries.forEach { tab ->
                Column(Modifier.clickable { group = null; model.tab(tab) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(tab.label), color = if (state.tab == tab) LibraryBlue else Color(0xFFBABDC4))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(30.dp).height(3.dp).background(if (state.tab == tab) LibraryBlue else Color.Transparent, RoundedCornerShape(3.dp)))
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center) {
            Text(if (busy) stringResource(R.string.library_loading_count, state.loaded, state.total)
                else stringResource(R.string.library_count, if (grouped) groups.size else display.size),
                color = Color(0xFFBABDC4), modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onPlayQueue(playable.shuffled().mapNotNull { it.id }) }, enabled = playable.isNotEmpty()) {
                    Icon(Icons.Default.Shuffle, stringResource(R.string.library_shuffle), tint = if (playable.isEmpty()) Color.Gray else Color.White)
                }
                IconButton(onClick = model::view) { Icon(if (state.listView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    stringResource(R.string.library_view), tint = Color.White) }
                IconButton(onClick = { filterSheet = true }) { Icon(Icons.Default.FilterAlt, stringResource(R.string.library_filter),
                    tint = if (state.genres.isNotEmpty() || state.watched != null) LibraryBlue else Color.White) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { sortSheet = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, null, tint = LibraryBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp)); Text(stringResource(state.sort.label), color = LibraryBlue)
                }
                if (state.sort != LibrarySort.RANDOM) IconButton(onClick = model::direction) {
                    Icon(if (state.descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        stringResource(R.string.library_direction), tint = LibraryBlue)
                }
            }
        }
        when {
            busy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LibraryBlue) }
            error != null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.library_error), color = Color.White)
                Text(error, color = Color.Gray, maxLines = 3)
                Button(onClick = {
                    if (state.error != null) model.open(libraryId, true) else when (state.tab) {
                        LibraryTab.FOLDERS -> folderRetry++
                        LibraryTab.COLLECTIONS -> model.loadCollections()
                        else -> model.open(libraryId, true)
                    }
                }) { Text(stringResource(R.string.library_retry)) }
            }
            (grouped && groups.isEmpty()) || (!grouped && display.isEmpty()) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.library_empty), color = Color.Gray)
            }
            else -> LazyVerticalGrid(columns = if (state.listView || grouped) GridCells.Fixed(1) else GridCells.Adaptive(105.dp),
                state = gridState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (grouped) items(groups, key = { it }) { name ->
                    Surface(onClick = { group = name }, color = Color(0xFF1C1E22), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, color = Color.White)
                            Text(filtered.count { name in (if (state.tab == LibraryTab.GENRES) it.genres.orEmpty() else it.tags.orEmpty()) }.toString(), color = Color.Gray)
                        }
                    }
                } else {
                    items(display.take(visibleCount), key = { it.id!! }) { item ->
                        LibraryBrowserCard(item, state.listView) {
                            when {
                                collectionRoot -> group = item.id
                                item.type == "Photo" || item.mediaType == "Photo" -> photoId = item.id
                                state.tab == LibraryTab.FOLDERS && (item.isFolder == true || item.type == "Folder" || item.type == "PhotoAlbum") -> {
                                    folderIds = folderIds + item.id!!; folderNames = folderNames + item.name.orEmpty()
                                }
                                contentType == ContentType.HOME_MEDIA && item.isLibraryPlayable() -> onPlayQueue(listOf(item.id!!))
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
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            LibrarySort.entries.forEach { sort ->
                Row(Modifier.fillMaxWidth().clickable { model.sort(sort); sortSheet = false }.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(sort.label), color = if (sort == state.sort) LibraryBlue else Color.White, modifier = Modifier.weight(1f))
                    if (sort == state.sort) Icon(if (sort == LibrarySort.RANDOM) Icons.Default.Check else
                        if (state.descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, tint = LibraryBlue)
                }
            }
        }
    }
    if (filterSheet) ModalBottomSheet(onDismissRequest = { filterSheet = false }, containerColor = Color(0xFF101114)) {
        var draftGenres by remember { mutableStateOf(state.genres) }
        var draftWatched by remember { mutableStateOf(state.watched) }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding()) {
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
    val photos = display.filter { it.type == "Photo" || it.mediaType == "Photo" }
    photoId?.let { id ->
        val index = photos.indexOfFirst { it.id == id }
        if (index >= 0) LibraryPhotoViewer(photos, index, repository, false, photos.size, { photoId = null }, {})
    }
}

@Composable
private fun LibraryBrowserCard(item: BaseItemDto, listView: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { MediaRepositoryProvider.getInstance(context) }
    var image by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) {
        image = withContext(Dispatchers.IO) { repository.getImageUrlString(item.id!!, width = 360,
            enableImageEnhancers = false, imageTag = item.imageTags?.get("Primary"), autoOrient = true) }
    }
    val folder = item.isFolder == true || item.type in setOf("Folder", "PhotoAlbum", "BoxSet", "Series")
    @Composable fun Artwork(modifier: Modifier) {
        Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF22252A)), contentAlignment = Alignment.Center) {
            if (image != null) AsyncImage(image, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(if (folder) Icons.Default.Folder else Icons.Default.Image, null, tint = Color.Gray)
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
                item.runTimeTicks?.let { stringResource(R.string.library_minutes, it / 600000000L) },
                item.communityRating?.let { "★ $it" }).joinToString(" · ")
            Text(detail, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    } else Column(Modifier.clickable(onClick = onClick)) {
        Artwork(Modifier.fillMaxWidth().aspectRatio(if (item.type in setOf("Photo", "Video", "Folder", "PhotoAlbum")) 4f / 3 else 2f / 3))
        Text(item.name.orEmpty(), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}
