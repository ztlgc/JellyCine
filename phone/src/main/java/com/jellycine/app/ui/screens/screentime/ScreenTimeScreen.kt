package com.jellycine.app.ui.screens.screentime

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlin.math.abs

private val ScreenTimeSecondary = Color(0xFFA9AFB4)
private val ScreenTimeAccent = Color(0xFF22D3EE)

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
    var dayFilter by remember(state.scopeKey, state.period, state.startDate, state.endDate) { mutableStateOf<Int?>(null) }
    var hourFilter by remember(state.scopeKey, state.period, state.startDate, state.endDate) { mutableStateOf<Int?>(null) }
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
    ScreenTimeContent(
        state = state,
        stats = filteredStats,
        dayStats = dayStats,
        dayFilter = dayFilter,
        hourFilter = hourFilter,
        onBack = onBack,
        onPeriodSelected = { viewModel.loadScreenTime(it, state.year) },
        onPrevious = {
            when (state.period) {
                ScreenTimePeriod.WEEK -> viewModel.loadScreenTime(state.period, state.year, weekOffset = state.weekOffset - 1)
                ScreenTimePeriod.MONTH -> viewModel.loadScreenTime(state.period, state.year, monthOffset = state.monthOffset - 1)
                ScreenTimePeriod.YEAR -> viewModel.loadScreenTime(state.period, state.year - 1)
            }
        },
        onNext = {
            when (state.period) {
                ScreenTimePeriod.WEEK -> viewModel.loadScreenTime(state.period, state.year, weekOffset = state.weekOffset + 1)
                ScreenTimePeriod.MONTH -> viewModel.loadScreenTime(state.period, state.year, monthOffset = state.monthOffset + 1)
                ScreenTimePeriod.YEAR -> viewModel.loadScreenTime(state.period, state.year + 1)
            }
        },
        onDaySelected = { dayFilter = if (dayFilter == it) null else it },
        onHourSelected = { hourFilter = if (hourFilter == it) null else it },
        onRetry = viewModel::refresh,
        getImageUrl = viewModel::getItemImageUrl
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenTimeContent(
    state: ScreenTimeUiState,
    stats: ScreenTimeStats? = state.stats,
    dayStats: ScreenTimeStats? = state.stats,
    dayFilter: Int? = null,
    hourFilter: Int? = null,
    onBack: () -> Unit = {},
    onPeriodSelected: (ScreenTimePeriod) -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onDaySelected: (Int?) -> Unit = {},
    onHourSelected: (Int?) -> Unit = {},
    onRetry: () -> Unit = {},
    getImageUrl: suspend (String) -> String? = { null }
) {
    var byOpens by rememberSaveable { mutableStateOf(false) }
    val filtered = dayFilter != null || hourFilter != null
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_screen_time), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.screen_time_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        val tablet = isTabletLayout(containerWidthDp())
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = if (tablet) 32.dp else 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "period") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ScreenTimeSwitch(
                            labels = listOf(
                                stringResource(R.string.screen_time_period_week_short),
                                stringResource(R.string.screen_time_period_month),
                                stringResource(R.string.screen_time_period_year)
                            ),
                            selectedIndex = state.period.ordinal,
                            onSelected = { onPeriodSelected(ScreenTimePeriod.entries[it]) }
                        )
                        DateRangeNavigator(state, onPrevious, onNext)
                        Text(stringResource(R.string.screen_time_local_caption), style = MaterialTheme.typography.bodySmall, color = ScreenTimeSecondary, textAlign = TextAlign.Center)
                    }
                }
                if (state.isLoading && state.stats == null) {
                    item(key = "loading") { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ScreenTimeAccent) } }
                } else if (state.error != null && state.stats == null) {
                    item(key = "load_error") {
                        SectionCard {
                            Text(stringResource(R.string.screen_time_load_failed), color = Color.White)
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.screen_time_retry), color = ScreenTimeAccent) }
                        }
                    }
                } else if (stats != null) {
                    if (state.error != null) {
                        item(key = "refresh_error") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.screen_time_refresh_failed), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = ScreenTimeSecondary)
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.screen_time_retry), color = ScreenTimeAccent) }
                            }
                        }
                    }
                    if (filtered) {
                        item(key = "filters") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.screen_time_filtered_caption), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = ScreenTimeSecondary)
                                TextButton(onClick = { onDaySelected(null); onHourSelected(null) }) {
                                    Text(stringResource(R.string.screen_time_clear_filters), color = ScreenTimeAccent)
                                }
                            }
                        }
                    }
                    item(key = "overview") {
                        OverviewCards(stats, state.previousStats, filtered, ChronoUnit.DAYS.between(state.startDate, state.endDate).toInt() + 1)
                    }
                    item(key = "trend") {
                        SectionCard {
                            DailyBreakdownChart(
                                state.stats?.dailyBreakdown.orEmpty(), dayFilter,
                                onBucketClick = onDaySelected,
                                onClearSelection = { onDaySelected(null) }
                            )
                        }
                    }
                    item(key = "peak_hours") {
                        SectionCard {
                            PeakHoursChart(
                                dayStats?.peakHours.orEmpty(), hourFilter,
                                onBucketClick = onHourSelected,
                                onClearSelection = { onHourSelected(null) }
                            )
                        }
                    }
                    if (stats.videoCount == 0) {
                        item(key = "empty") { SectionCard { Text(stringResource(R.string.screen_time_empty_history), color = ScreenTimeSecondary) } }
                    } else {
                        item(key = "rankings") { VideoRankingsCard(stats, byOpens, { byOpens = it }, getImageUrl) }
                    }
                    item(key = "average_caption") {
                        Text(stringResource(R.string.screen_time_average_caption), modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = ScreenTimeSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTimeSwitch(labels: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    val compact = LocalDensity.current.fontScale >= 1.5f
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF101213)).selectableGroup().padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Color(0xFF35393B) else Color.Transparent)
                    .selectable(selected, role = Role.Tab, onClick = { onSelected(index) })
                    .heightIn(min = 40.dp).padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else ScreenTimeSecondary, maxLines = 2, textAlign = TextAlign.Center,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun OverviewCards(stats: ScreenTimeStats, previous: ScreenTimeStats?, filtered: Boolean, comparisonDays: Int = stats.dailyBreakdown.size) {
    val locale = LocalConfiguration.current.locales[0]
    val duration = formatWatchTime(stats.totalWatchTimeMs, locale)
    val hero = buildAnnotatedString {
        Regex("\\d+|[^\\d]+").findAll(duration).forEach { match ->
            if (match.value.first().isDigit()) append(match.value) else {
                withStyle(SpanStyle(fontSize = 0.36.em, color = ScreenTimeSecondary)) { append(" ${match.value.trim()} ") }
            }
        }
    }
    SectionCard {
        Text(stringResource(R.string.screen_time_watched_duration), style = MaterialTheme.typography.bodySmall, color = ScreenTimeSecondary)
        Spacer(Modifier.height(12.dp))
        BasicText(hero, modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp, color = Color.White, fontWeight = FontWeight.SemiBold),
            maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 48.sp))
        if (!filtered && previous != null && comparisonDays > 0) {
            val delta = stats.totalWatchTimeMs - previous.totalWatchTimeMs
            val comparison = when {
                delta > 0 -> stringResource(R.string.screen_time_compare_more, comparisonDays, formatWatchTime(delta, locale))
                delta < 0 -> stringResource(R.string.screen_time_compare_less, comparisonDays, formatWatchTime(abs(delta), locale))
                else -> stringResource(R.string.screen_time_compare_same, comparisonDays)
            }
            Spacer(Modifier.height(12.dp))
            Text(comparison, style = MaterialTheme.typography.bodySmall, color = ScreenTimeSecondary)
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Color(0xFF303234))
        Spacer(Modifier.height(16.dp))
        val metrics = listOf(
            stringResource(R.string.screen_time_daily_average) to formatWatchTime(stats.dailyAverageMs, locale),
            stringResource(R.string.screen_time_video_count) to stats.videoCount.toString(),
            stringResource(R.string.screen_time_open_count) to stats.openCount.toString()
        )
        val largeFont = LocalDensity.current.fontScale >= 1.5f
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (largeFont || maxWidth < 260.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    metrics.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = ScreenTimeSecondary)
                            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    metrics.forEach { (label, value) ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = ScreenTimeSecondary)
                            BasicText(value, style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                                maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 18.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VideoRankingsCard(
    stats: ScreenTimeStats,
    byOpens: Boolean,
    onSortChanged: (Boolean) -> Unit,
    getImageUrl: suspend (String) -> String? = { null }
) {
    val videos = if (byOpens) stats.topByOpenCount else stats.topByWatchTime
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.screen_time_ranking), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(stringResource(R.string.screen_time_top_five), style = MaterialTheme.typography.bodySmall, color = ScreenTimeSecondary)
        }
        Spacer(Modifier.height(16.dp))
        ScreenTimeSwitch(listOf(stringResource(R.string.screen_time_watched_duration), stringResource(R.string.screen_time_open_count)), if (byOpens) 1 else 0) { onSortChanged(it == 1) }
        Spacer(Modifier.height(16.dp))
        if (videos.isEmpty()) {
            Text(stringResource(R.string.screen_time_no_ranking), style = MaterialTheme.typography.bodyMedium, color = ScreenTimeSecondary)
        }
        videos.forEachIndexed { index, video ->
            key(video.itemId) {
                var posterUrl by remember(video.itemId, getImageUrl) { mutableStateOf<String?>(null) }
                LaunchedEffect(video.itemId, getImageUrl) { posterUrl = getImageUrl(video.itemId) }
                RankingRow(index + 1, video, byOpens, posterUrl)
                if (index < videos.lastIndex) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(Modifier.padding(start = 86.dp), color = Color(0xFF2A2B2D))
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
internal fun RankingRow(rank: Int, video: VideoWatchSummary, byOpens: Boolean, posterUrl: String?) {
    val locale = LocalConfiguration.current.locales[0]
    val opens = stringResource(R.string.screen_time_times, video.openCount)
    val duration = formatWatchTime(video.watchTimeMs, locale)
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(rank.toString(), color = ScreenTimeSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(min = 14.dp))
        AsyncImage(posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(58.dp, 42.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF252525)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(video.name.ifBlank { stringResource(R.string.screen_time_unnamed_video) }, color = Color.White,
                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (largeFont) {
                Text(if (byOpens) opens else duration, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(if (byOpens) duration else opens, color = ScreenTimeSecondary, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (byOpens) opens else duration, modifier = Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(if (byOpens) duration else opens, color = ScreenTimeSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DateRangeNavigator(state: ScreenTimeUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dateLabel = remember(state.startDate, state.endDate, state.period, locale) {
        when (state.period) {
            ScreenTimePeriod.WEEK -> {
                val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                "${state.startDate.format(formatter)} – ${state.endDate.format(formatter)}"
            }
            ScreenTimePeriod.MONTH -> state.startDate.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale))
            ScreenTimePeriod.YEAR -> state.startDate.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "y"), locale))
        }
    }
    val canGoNext = when (state.period) {
        ScreenTimePeriod.WEEK -> state.weekOffset < 0
        ScreenTimePeriod.MONTH -> state.monthOffset < 0
        ScreenTimePeriod.YEAR -> state.year < LocalDate.now().year
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.screen_time_previous_period), tint = ScreenTimeSecondary) }
        Text(dateLabel, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(enabled = canGoNext, onClick = onNext) {
            Icon(Icons.Rounded.ChevronRight, stringResource(R.string.screen_time_next_period), tint = if (canGoNext) ScreenTimeSecondary else Color.White.copy(alpha = 0.3f))
        }
    }
}

private val hourRanges = listOf(6..11, 12..17, 18..23, 0..5)

private fun daysForBucket(index: Int, days: List<DailyWatchTime>): Set<Long> {
    val buckets = when {
        days.size <= 7 -> days.map { listOf(it) }
        days.size <= 31 -> days.groupBy { LocalDate.ofEpochDay(it.dateEpochDay).get(WeekFields.of(java.util.Locale.getDefault()).weekOfMonth()) }.toSortedMap().values.toList()
        else -> days.groupBy { LocalDate.ofEpochDay(it.dateEpochDay).monthValue }.toSortedMap().values.toList()
    }
    return buckets.getOrNull(index).orEmpty().map { it.dateEpochDay }.toSet()
}
