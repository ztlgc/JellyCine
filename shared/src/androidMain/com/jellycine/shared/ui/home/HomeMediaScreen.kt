package com.jellycine.shared.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.jellycine.data.model.*
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.shared.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FolderPage(
    val items: List<BaseItemDto> = emptyList(),
    val offset: Int = 0,
    val more: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null
)

private class HomeMediaViewModel(private val repository: MediaRepository) : ViewModel() {
    val folders = mutableStateMapOf<String, FolderPage>()
    private var job: Job? = null

    fun load(folderId: String, refresh: Boolean = false) {
        val previous = folders[folderId] ?: FolderPage()
        if (previous.loading || (!refresh && !previous.more)) return
        job?.cancel()
        // A cancelled request must not leave its directory permanently loading.
        folders.keys.toList().forEach { id -> folders[id]?.let { folders[id] = it.copy(loading = false) } }
        val page = if (refresh) FolderPage() else previous
        folders[folderId] = page.copy(loading = true, error = null)
        job = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getUserItems(
                    parentId = folderId, recursive = false, includeItemTypes = null,
                    startIndex = page.offset, limit = 60, sortBy = "SortName", sortOrder = "Ascending",
                    fields = "ParentId,DateCreated,PrimaryImageAspectRatio,ChildCount",
                    enableUserData = true
                )
            }
            result.fold(
                onSuccess = { response ->
                    val fetched = response.items.orEmpty()
                    val offset = page.offset + fetched.size
                    folders[folderId] = FolderPage(
                        items = (page.items + fetched).filter { !it.id.isNullOrBlank() }.distinctBy { it.id },
                        offset = offset,
                        more = fetched.isNotEmpty() && (response.totalRecordCount?.let { offset < it } ?: (fetched.size == 60))
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    folders[folderId] = page.copy(error = error.message ?: "", loading = false)
                }
            )
        }
    }
}

@Composable
fun HomeMediaScreen(
    libraryId: String,
    title: String,
    isTv: Boolean = false,
    onBack: () -> Unit,
    onVideoClick: (BaseItemDto) -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { MediaRepositoryProvider.getInstance(context) }
    val model: HomeMediaViewModel = viewModel(key = "home-media-$libraryId", factory = remember(repository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeMediaViewModel(repository) as T
        }
    })
    var ids by rememberSaveable(libraryId) { mutableStateOf(listOf(libraryId)) }
    var names by rememberSaveable(libraryId) { mutableStateOf(listOf(title)) }
    var photoId by rememberSaveable(libraryId) { mutableStateOf<String?>(null) }
    val folderId = ids.last()
    val state = model.folders[folderId] ?: FolderPage()
    val holder = rememberSaveableStateHolder()
    fun back() {
        if (photoId != null) photoId = null
        else if (ids.size > 1) { ids = ids.dropLast(1); names = names.dropLast(1) }
        else onBack()
    }
    BackHandler { back() }
    LaunchedEffect(folderId) { if (model.folders[folderId] == null) model.load(folderId) }

    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(if (isTv) 24.dp else 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.home_media_back), tint = Color.White) }
            Text(names.last(), color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { model.load(folderId, refresh = true) }, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, stringResource(R.string.home_media_refresh), tint = Color.White)
            }
        }
        if (state.error != null) {
            Text(stringResource(R.string.home_media_load_error), color = MaterialTheme.colorScheme.error)
            Button(onClick = { model.load(folderId) }) { Text(stringResource(R.string.home_media_retry)) }
        }
        if (state.loading && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.items.isEmpty() && state.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.home_media_empty), color = Color.White) }
        } else holder.SaveableStateProvider(folderId) {
            val gridState = rememberLazyGridState()
            var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
            val focusId = selectedId?.takeIf { selected -> state.items.any { it.id == selected && it.homeMediaKind() != HomeMediaKind.UNSUPPORTED } }
                ?: state.items.firstOrNull { it.homeMediaKind() != HomeMediaKind.UNSUPPORTED }?.id
            val returnFocus = remember { FocusRequester() }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 200.dp else 130.dp), state = gridState,
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.items, key = { it.id!! }) { item ->
                    val kind = item.homeMediaKind()
                    var focused by remember { mutableStateOf(false) }
                    Card(
                        onClick = {
                            selectedId = item.id
                            when (kind) {
                                HomeMediaKind.FOLDER -> { ids = ids + item.id!!; names = names + item.name.orEmpty() }
                                HomeMediaKind.PHOTO -> photoId = item.id
                                HomeMediaKind.VIDEO -> onVideoClick(item)
                                HomeMediaKind.UNSUPPORTED -> Unit
                            }
                        }, enabled = kind != HomeMediaKind.UNSUPPORTED,
                        modifier = Modifier.then(if (item.id == focusId) Modifier.focusRequester(returnFocus) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF202020))
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentAlignment = Alignment.Center) {
                            MediaImage(item, repository, fullScreen = false, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (kind == HomeMediaKind.FOLDER || kind == HomeMediaKind.VIDEO) Icon(
                                if (kind == HomeMediaKind.FOLDER) Icons.Default.Folder else Icons.Default.PlayCircle,
                                contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            )
                        }
                        Text(item.name.orEmpty(), maxLines = 2, color = Color.White, modifier = Modifier.padding(8.dp))
                        if (kind == HomeMediaKind.UNSUPPORTED) Text(stringResource(R.string.home_media_unsupported),
                            color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                if (state.more) item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.loading) CircularProgressIndicator()
                        else Button(onClick = { model.load(folderId) }) { Text(stringResource(R.string.home_media_more)) }
                    }
                }
            }
            LaunchedEffect(photoId, state.items.isEmpty()) {
                if (isTv && photoId == null && focusId != null && state.items.any { it.id == focusId }) {
                    // The selected card can be outside the composed viewport after restoration.
                    val index = state.items.indexOfFirst { it.id == focusId }
                    gridState.scrollToItem(index)
                    withFrameNanos { }
                    returnFocus.requestFocus()
                }
            }
        }
    }
    val photos = state.items.filter { it.homeMediaKind() == HomeMediaKind.PHOTO }
    photoId?.let { id ->
        val index = photos.indexOfFirst { it.id == id }
        if (index >= 0) LibraryPhotoViewer(photos, index, repository, isTv, state.offset, onClose = { photoId = null },
            onLoadMore = { if (state.more && !state.loading && state.error == null) model.load(folderId) })
    }
}

@Composable
private fun MediaImage(item: BaseItemDto, repository: MediaRepository, fullScreen: Boolean, modifier: Modifier, contentScale: ContentScale) {
    var url by remember(item.id, fullScreen) { mutableStateOf<String?>(null) }
    var failed by remember(item.id, fullScreen) { mutableStateOf(false) }
    var loading by remember(item.id, fullScreen) { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(item.id, fullScreen, retry) {
        failed = false; loading = true
        url = withContext(Dispatchers.IO) {
            repository.getImageUrlString(item.id!!, width = if (fullScreen) 2560 else 480,
                enableImageEnhancers = false, imageTag = item.imageTags?.get("Primary"), autoOrient = true)
        }
        if (url == null) { failed = true; loading = false }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(
            model = url, contentDescription = item.name, contentScale = contentScale, modifier = Modifier.fillMaxSize(),
            onLoading = { loading = true }, onSuccess = { loading = false; failed = false }, onError = { loading = false; failed = true }
        )
        if (loading && fullScreen) CircularProgressIndicator()
        if (failed) {
            if (fullScreen) Button(onClick = { url = null; retry++ }) { Text(stringResource(R.string.home_media_retry)) }
            else Icon(Icons.Default.Image, null, tint = Color.Gray)
        }
    }
}

@Composable
fun LibraryPhotoViewer(photos: List<BaseItemDto>, initialIndex: Int, repository: MediaRepository, isTv: Boolean, loadedOffset: Int,
    onClose: () -> Unit, onLoadMore: () -> Unit) {
    val pager = rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(pager.currentPage, photos.size, loadedOffset) {
        if (pager.currentPage == photos.lastIndex) onLoadMore()
    }
    fun move(delta: Int) {
        val target = (pager.currentPage + delta).coerceIn(0, photos.lastIndex)
        scope.launch { pager.animateScrollToPage(target) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()
            .onPreviewKeyEvent {
                if (isTv && it.type == KeyEventType.KeyDown) when (it.key) {
                    Key.DirectionLeft -> { move(-1); true }
                    Key.DirectionRight -> { move(1); true }
                    else -> false
                } else false
            }.focusRequester(focus).focusable()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.home_media_back), tint = Color.White) }
                Text(photos[pager.currentPage].name.orEmpty(), color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                Text("${pager.currentPage + 1} / ${photos.size}", color = Color.White, modifier = Modifier.padding(12.dp))
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), key = { photos[it].id!! }) { page ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                var size by remember { mutableStateOf(IntSize.Zero) }
                val transform = rememberTransformableState { zoom, pan, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    val next = offset + pan
                    offset = Offset(next.x.coerceIn(-size.width * (scale - 1) / 2, size.width * (scale - 1) / 2),
                        next.y.coerceIn(-size.height * (scale - 1) / 2, size.height * (scale - 1) / 2))
                }
                LaunchedEffect(pager.currentPage) { if (page != pager.currentPage) { scale = 1f; offset = Offset.Zero } }
                Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }
                    .transformable(state = transform, canPan = { scale > 1f }, enabled = !isTv)) {
                    MediaImage(photos[page], repository, true,
                        Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, ContentScale.Fit)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { move(-1) }, enabled = pager.currentPage > 0) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.home_media_previous), tint = if (pager.currentPage > 0) Color.White else Color.Gray)
                }
                IconButton(onClick = { move(1) }, enabled = pager.currentPage < photos.lastIndex) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.home_media_next), tint = if (pager.currentPage < photos.lastIndex) Color.White else Color.Gray)
                }
            }
        }
        LaunchedEffect(Unit) { if (isTv) focus.requestFocus() }
    }
}
