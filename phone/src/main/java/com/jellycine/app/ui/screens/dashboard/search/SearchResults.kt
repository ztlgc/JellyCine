package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.jellycine.app.ui.components.common.dashboardContentBottomPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.shared.R
import com.jellycine.app.ui.components.common.SeerrTopBadges
import com.jellycine.shared.ui.components.common.ShimmerEffect
import com.jellycine.shared.ui.components.common.LazyImageLoader
import com.jellycine.shared.ui.components.common.episodeDisplaySubtitle
import com.jellycine.shared.ui.components.common.preferredDisplayTitle
import com.jellycine.app.ui.screens.dashboard.PosterSkeleton
import com.jellycine.app.ui.screens.dashboard.SectionTitleSkeleton
import com.jellycine.shared.util.image.rememberImageUrl
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.toSeerDetailItem
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import coil3.compose.AsyncImage
import java.util.Locale

@Composable
fun SearchResultsViewSkeleton(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = dashboardContentBottomPadding(keyboardHidesBar = true)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        searchPosterSectionSkeleton(titleWidth = 90.dp)
        searchPosterSectionSkeleton(titleWidth = 100.dp)

        item {
            SectionTitleSkeleton(
                modifier = Modifier.padding(bottom = 12.dp),
                width = 120.dp
            )
        }

        items(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerEffect(
                    modifier = Modifier.size(80.dp, 60.dp),
                    cornerRadius = 8f
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(16.dp),
                        cornerRadius = 4f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(12.dp),
                        cornerRadius = 4f
                    )
                }
            }
        }
    }
}

private fun LazyListScope.searchPosterSectionSkeleton(titleWidth: Dp) {
    item {
        SectionTitleSkeleton(
            modifier = Modifier.padding(bottom = 12.dp),
            width = titleWidth
        )
    }

    item {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(4) {
                PosterSkeleton(
                    width = 120.dp,
                    height = 230.dp,
                    cornerRadius = 12f
                )
            }
        }
    }
}

@Composable
fun SearchResultsView(
    uiState: SearchUiState,
    onItemClick: (BaseItemDto) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    key(uiState.searchGeneration) {
        SearchResultsList(uiState, onItemClick, onLoadMore, modifier)
    }
}

@Composable
private fun SearchResultsList(
    uiState: SearchUiState,
    onItemClick: (BaseItemDto) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    SearchScrollLoader(
        listState = listState,
        enabled = uiState.hasMoreVideos && (uiState.videoResults.isNotEmpty() || uiState.episodeResults.isNotEmpty()) &&
            !uiState.isLoadingMore && !uiState.searchLoadFailed,
        loadedPages = uiState.loadedSearchPages,
        onLoadMore = onLoadMore
    )
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = dashboardContentBottomPadding(keyboardHidesBar = true)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Movies Section
        if (uiState.movieResults.isNotEmpty() || uiState.seerrMovieResults.isNotEmpty()) {
            item(key = "movie-title") {
                Text(
                    text = stringResource(R.string.movies),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            item(key = "movie-results") {
                val rowState = rememberLazyListState()
                SearchScrollLoader(rowState, uiState.hasMoreVideos && !uiState.isLoadingMore && !uiState.searchLoadFailed,
                    uiState.loadedSearchPages, onLoadMore)
                LazyRow(
                    state = rowState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(uiState.movieResults, key = { it.id ?: it.hashCode() }) { movie ->
                        SearchResultCard(
                            item = movie,
                            onItemClick = { onItemClick(movie) }
                        )
                    }

                    itemsIndexed(
                        items = uiState.seerrMovieResults,
                        key = { index, seerrItem -> "seerr-movie-${seerrItem.tmdbId}_$index" }
                    ) { _, seerrItem ->
                        SearchSeerrResultCard(
                            item = seerrItem,
                            onItemClick = { onItemClick(seerrItem.toSeerDetailItem()) }
                        )
                    }
                    if (uiState.hasMoreVideos || uiState.searchLoadFailed) {
                        item(key = "movie-load-more") {
                            SearchLoadMore(uiState, onLoadMore, Modifier.width(120.dp).heightIn(min = 180.dp))
                        }
                    }
                }
            }
        }
        
        // TV Shows Section
        if (uiState.showResults.isNotEmpty() || uiState.seerrShowResults.isNotEmpty()) {
            item(key = "series-title") {
                Text(
                    text = stringResource(R.string.search_results_shows),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            item(key = "series-results") {
                val rowState = rememberLazyListState()
                SearchScrollLoader(rowState, uiState.hasMoreSeries && !uiState.isLoadingMore && !uiState.searchLoadFailed,
                    uiState.loadedSearchPages, onLoadMore)
                LazyRow(
                    state = rowState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(uiState.showResults, key = { it.id ?: it.hashCode() }) { show ->
                        SearchResultCard(
                            item = show,
                            onItemClick = { onItemClick(show) }
                        )
                    }

                    itemsIndexed(
                        items = uiState.seerrShowResults,
                        key = { index, seerrItem -> "seerr-show-${seerrItem.tmdbId}_$index" }
                    ) { _, seerrItem ->
                        SearchSeerrResultCard(
                            item = seerrItem,
                            onItemClick = { onItemClick(seerrItem.toSeerDetailItem()) }
                        )
                    }
                    if (uiState.hasMoreSeries || uiState.searchLoadFailed) {
                        item(key = "series-load-more") {
                            SearchLoadMore(uiState, onLoadMore, Modifier.width(120.dp).heightIn(min = 180.dp))
                        }
                    }
                }
            }
        }
        
        if (uiState.videoResults.isNotEmpty()) {
            item(key = "video-title") {
                Text(
                    text = stringResource(R.string.home_other_videos),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(uiState.videoResults, key = { "video-${it.id ?: it.hashCode()}" }) { video ->
                VideoResultCard(
                    item = video,
                    onItemClick = { onItemClick(video) }
                )
            }
        }

        // Episodes Section
        if (uiState.episodeResults.isNotEmpty()) {
            item(key = "episode-title") {
                Text(
                    text = stringResource(R.string.search_results_episodes),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            items(uiState.episodeResults, key = { "episode-${it.id ?: it.hashCode()}" }) { episode ->
                VideoResultCard(
                    item = episode,
                    onItemClick = { onItemClick(episode) }
                )
            }
        }
        if (uiState.hasMoreResults || uiState.searchLoadFailed || uiState.isLoadingMore) {
            item(key = "search-load-more") {
                SearchLoadMore(uiState, onLoadMore, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SearchScrollLoader(listState: LazyListState, enabled: Boolean, loadedPages: Int, onLoadMore: () -> Unit) {
    val nearEnd by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            listState.canScrollBackward && layout.totalItemsCount > 0 &&
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, enabled, loadedPages) {
        if (nearEnd && enabled) onLoadMore()
    }
}

@Composable
private fun SearchLoadMore(uiState: SearchUiState, onLoadMore: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (uiState.isLoadingMore || uiState.isSearching) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            if (uiState.searchLoadFailed) {
                Text(stringResource(R.string.search_load_failed), color = Color.Gray, fontSize = 12.sp)
            }
            TextButton(onClick = onLoadMore) {
                Text(stringResource(if (uiState.searchLoadFailed) R.string.home_media_retry else R.string.home_media_more))
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: BaseItemDto,
    onItemClick: () -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val unknownTitle = stringResource(R.string.search_result_unknown_title)
    val unknownEpisode = stringResource(R.string.search_result_unknown_episode)
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onItemClick() }
    ) {
        // Poster
        Card(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(0.67f),
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            val imageUrl = rememberImageUrl(
                itemId = item.id,
                imageType = "Primary",
                width = 300,
                height = 450,
                quality = 80,
                mediaRepository = mediaRepository
            )
            LazyImageLoader(
                imageUrl = imageUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                cornerRadius = 12
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Title
        Text(
            text = item.preferredDisplayTitle(
                unknownTitle = unknownTitle,
                unknownEpisode = unknownEpisode
            ),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        
        SearchResultMetadata(item)

        val yearAndGenre = item.searchYearAndGenre()
        if (yearAndGenre.isNotBlank()) {
            Text(
                text = yearAndGenre,
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VideoResultCard(
    item: BaseItemDto,
    onItemClick: () -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val unknownTitle = stringResource(R.string.search_result_unknown_title)
    val unknownEpisode = stringResource(R.string.search_result_unknown_episode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode Thumbnail
        Card(
            modifier = Modifier.size(80.dp, 60.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            val imageUrl = rememberImageUrl(
                itemId = item.id,
                imageType = "Primary",
                width = 240,
                height = 180,
                quality = 75,
                enableImageEnhancers = false,
                mediaRepository = mediaRepository
            )
            LazyImageLoader(
                imageUrl = imageUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                cornerRadius = 8
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Episode Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.preferredDisplayTitle(
                    unknownTitle = unknownTitle,
                    unknownEpisode = unknownEpisode
                ),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))

            SearchResultMetadata(item)
            
            val subtitle = if (item.type.equals("Episode", ignoreCase = true)) {
                item.episodeDisplaySubtitle(fallback = unknownEpisode)
            } else {
                item.searchYearAndGenre()
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun BaseItemDto.searchYearAndGenre(): String = listOfNotNull(
    productionYear?.toString(),
    genres?.firstOrNull { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
).joinToString(" | ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResultMetadata(item: BaseItemDto) {
    val details = searchResultDetails(item)
    if (details.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            details.forEach { detail ->
                Text(detail, color = Color(0xFFBABDC4), fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun SearchSeerrResultCard(
    item: SeerrRecommendationTitle,
    onItemClick: () -> Unit
) {
    val posterUrl = remember(item.posterUrl) { item.posterUrl?.takeIf { it.isNotBlank() } }

    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onItemClick() }
    ) {
        Card(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(0.67f),
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                SeerrTopBadges(
                    requestState = item.requestState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )

        Text(
            text = item.productionYear?.toString()
                ?: item.mediaType.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
                },
            color = Color.Gray,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
