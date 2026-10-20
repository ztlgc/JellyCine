package com.jellycine.app.ui.screens.screentime

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.jellycine.app.ui.components.common.containerWidthDp
import com.jellycine.app.ui.components.common.isTabletLayout
import com.jellycine.data.model.DailyWatchTime
import com.jellycine.data.model.ScreenTimeStats
import com.jellycine.data.model.VideoWatchSummary
import com.jellycine.data.repository.computeScreenTimeStats
import com.jellycine.shared.R
import com.jellycine.shared.ui.components.screentime.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTimeScreen(
    onBack: () -> Unit,
    viewModel: ScreenTimeViewModel = viewModel(factory = ScreenTimeViewModelFactory(LocalContext.current.applicationContext))
) {
    val state by viewModel.screenTimeState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var dayFilter by remember(state.period, state.startDate, state.endDate) { mutableStateOf<Int?>(null) }
    var hourFilter by remember(state.period, state.startDate, state.endDate) { mutableStateOf<Int?>(null) }
    val selectedDays = remember(dayFilter, state.stats) {
        dayFilter?.let { daysForBucket(it, state.stats?.dailyBreakdown.orEmpty()) }
    }
    val dayStats = remember(state.stats, state.history, selectedDays) {
        if (selectedDays == null) state.stats else computeScreenTimeStats(
            state.history, state.scopeKey, state.startDate, state.endDate, selectedDays = selectedDays
        )
    }
    val filteredStats = remember(dayStats, state.history, selectedDays, hourFilter) {
        if (hourFilter == null) dayStats else computeScreenTimeStats(
            state.history, state.scopeKey, state.startDate, state.endDate,
            selectedDays = selectedDays, selectedHours = hourRanges[hourFilter!!]
        )
    }
    BackHandler { onBack() }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_screen_time)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.screen_time_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        val tablet = isTabletLayout(containerWidthDp())
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = if (tablet) 32.dp else 16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateRangeNavigator(state, viewModel)
                        Text(stringResource(R.string.screen_time_local_caption), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScreenTimePeriod.entries.forEach { period ->
                                val label = when (period) {
                                    ScreenTimePeriod.WEEK -> R.string.screen_time_period_week
                                    ScreenTimePeriod.MONTH -> R.string.screen_time_period_month
                                    ScreenTimePeriod.YEAR -> R.string.screen_time_period_year
                                }
                                FilterChip(selected = state.period == period, onClick = { viewModel.loadScreenTime(period, state.year) }, label = { Text(stringResource(label)) })
                            }
                        }
                    }
                }
                if (state.isLoading) {
                    item { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF67E8F9)) } }
                } else if (state.error != null) {
                    item {
                        SectionCard {
                            Text(stringResource(R.string.screen_time_load_failed), color = Color.White)
                            TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.screen_time_retry)) }
                        }
                    }
                } else if (filteredStats != null) {
                    val stats = filteredStats
                    item {
                        OverviewCards(stats, state.previousStats, dayFilter != null || hourFilter != null)
                    }
                    item {
                        SectionCard {
                            DailyBreakdownChart(
                                state.stats!!.dailyBreakdown, dayFilter,
                                onBucketClick = { dayFilter = if (dayFilter == it) null else it },
                                onClearSelection = { dayFilter = null }
                            )
                        }
                    }
                    item {
                        SectionCard {
                            PeakHoursChart(
                                dayStats!!.peakHours, hourFilter,
                                onBucketClick = { hourFilter = if (hourFilter == it) null else it },
                                onClearSelection = { hourFilter = null }
                            )
                        }
                    }
                    if (stats.videoCount == 0) {
                        item { SectionCard { Text(stringResource(R.string.screen_time_empty_history), color = Color.White.copy(alpha = 0.6f)) } }
                    }
                    item { RankingCard(stringResource(R.string.screen_time_top_duration), stats.topByWatchTime, false, viewModel) }
                    item { RankingCard(stringResource(R.string.screen_time_top_opens), stats.topByOpenCount, true, viewModel) }
                }
            }
        }
    }
}

@Composable
internal fun OverviewCards(stats: ScreenTimeStats, previous: ScreenTimeStats?, filtered: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(stringResource(R.string.screen_time_watched_duration), formatWatchTime(stats.totalWatchTimeMs),
                if (!filtered && previous != null) formatDelta(stats.totalWatchTimeMs - previous.totalWatchTimeMs) else null, Modifier.weight(1f))
            StatCard(stringResource(R.string.screen_time_daily_average), formatWatchTime(stats.dailyAverageMs),
                if (!filtered && previous != null) formatDelta(stats.dailyAverageMs - previous.dailyAverageMs) else null, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(stringResource(R.string.screen_time_video_count), stats.videoCount.toString(), modifier = Modifier.weight(1f))
            StatCard(stringResource(R.string.screen_time_open_count), stats.openCount.toString(), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RankingCard(title: String, videos: List<VideoWatchSummary>, byOpens: Boolean, viewModel: ScreenTimeViewModel) {
    VideoRankingCard(title, videos, byOpens, viewModel::getItemImageUrl)
}

@Composable
internal fun VideoRankingCard(
    title: String,
    videos: List<VideoWatchSummary>,
    byOpens: Boolean,
    getImageUrl: suspend (String) -> String? = { null }
) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(12.dp))
        if (videos.isEmpty()) {
            Text(stringResource(R.string.screen_time_no_ranking), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f))
        }
        videos.forEachIndexed { index, video ->
            key(video.itemId) {
                var posterUrl by remember(video.itemId) { mutableStateOf<String?>(null) }
                LaunchedEffect(video.itemId) { posterUrl = getImageUrl(video.itemId) }
                RankingRow(index + 1, video, byOpens, posterUrl)
                if (index < videos.lastIndex) Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
internal fun RankingRow(rank: Int, video: VideoWatchSummary, byOpens: Boolean, posterUrl: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(rank.toString(), color = Color(0xFF67E8F9), fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
        AsyncImage(posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp, 44.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF252525)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(video.name.ifBlank { stringResource(R.string.screen_time_unnamed_video) }, color = Color.White,
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val opens = stringResource(R.string.screen_time_times, video.openCount)
            val duration = formatWatchTime(video.watchTimeMs)
            Text(if (byOpens) "$opens · $duration" else "$duration · $opens",
                color = Color(0xFF67E8F9), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DateRangeNavigator(state: ScreenTimeUiState, viewModel: ScreenTimeViewModel) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
    val canGoNext = when (state.period) {
        ScreenTimePeriod.WEEK -> state.weekOffset < 0
        ScreenTimePeriod.MONTH -> state.monthOffset < 0
        ScreenTimePeriod.YEAR -> state.year < LocalDate.now().year
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            when (state.period) {
                ScreenTimePeriod.WEEK -> viewModel.loadScreenTime(state.period, state.year, weekOffset = state.weekOffset - 1)
                ScreenTimePeriod.MONTH -> viewModel.loadScreenTime(state.period, state.year, monthOffset = state.monthOffset - 1)
                ScreenTimePeriod.YEAR -> viewModel.loadScreenTime(state.period, state.year - 1)
            }
        }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.screen_time_previous_period), tint = Color.White) }
        Text("${state.startDate.format(formatter)} – ${state.endDate.format(formatter)}", color = Color.White,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(enabled = canGoNext, onClick = {
            when (state.period) {
                ScreenTimePeriod.WEEK -> viewModel.loadScreenTime(state.period, state.year, weekOffset = state.weekOffset + 1)
                ScreenTimePeriod.MONTH -> viewModel.loadScreenTime(state.period, state.year, monthOffset = state.monthOffset + 1)
                ScreenTimePeriod.YEAR -> viewModel.loadScreenTime(state.period, state.year + 1)
            }
        }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.screen_time_next_period), tint = if (canGoNext) Color.White else Color.White.copy(alpha = 0.3f)) }
    }
}

private val hourRanges = listOf(6..11, 12..17, 18..23, 0..5)

private fun daysForBucket(index: Int, days: List<DailyWatchTime>): Set<Long> {
    val buckets = when {
        days.size <= 7 -> days.map { listOf(it) }
        days.size <= 31 -> days.groupBy { LocalDate.ofEpochDay(it.dateEpochDay).get(WeekFields.of(Locale.getDefault()).weekOfMonth()) }.toSortedMap().values.toList()
        else -> days.groupBy { LocalDate.ofEpochDay(it.dateEpochDay).monthValue }.toSortedMap().values.toList()
    }
    return buckets.getOrNull(index).orEmpty().map { it.dateEpochDay }.toSet()
}
