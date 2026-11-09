package com.jellycine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.jellycine.app.ui.screens.screentime.ScreenTimeContent
import com.jellycine.app.ui.screens.screentime.ScreenTimePeriod
import com.jellycine.app.ui.screens.screentime.ScreenTimeUiState
import com.jellycine.app.ui.screens.screentime.VideoRankingsCard
import com.jellycine.data.model.DailyWatchTime
import com.jellycine.data.model.PeakHourBucket
import com.jellycine.data.model.ScreenTimeStats
import com.jellycine.data.model.VideoWatchSummary
import com.jellycine.shared.ui.theme.JellyCineTheme
import java.time.LocalDate

private fun screenTimePreviewState(): ScreenTimeUiState {
    val start = LocalDate.of(2026, 10, 1)
    val videos = listOf(
        VideoWatchSummary("1", "山野之间 · 秋天的第一次远行", 156 * 60_000L, 8),
        VideoWatchSummary("2", "2026 国庆家庭聚会", 124 * 60_000L, 6),
        VideoWatchSummary("3", "海边日落", 102 * 60_000L, 5),
        VideoWatchSummary("4", "城市漫步：一段很长的视频标题，用于检查小屏幕上的换行与省略", 86 * 60_000L, 7),
        VideoWatchSummary("5", "周末的森林徒步", 68 * 60_000L, 4)
    )
    val durations = mapOf(1 to 108L, 4 to 165L, 11 to 240L, 18 to 156L, 25 to 99L)
    val stats = ScreenTimeStats(
        totalWatchTimeMs = 768 * 60_000L,
        dailyAverageMs = 768 * 60_000L / 31,
        dailyBreakdown = List(31) { index -> DailyWatchTime(start.plusDays(index.toLong()).toEpochDay(), (durations[index + 1] ?: 0L) * 60_000) },
        peakHours = listOf("Morning" to 92L, "Afternoon" to 158L, "Evening" to 428L, "Night" to 90L).map { (label, minutes) -> PeakHourBucket(label, minutes * 60_000) },
        videoCount = 18,
        openCount = 57,
        topByWatchTime = videos,
        topByOpenCount = videos.sortedByDescending { it.openCount }
    )
    return ScreenTimeUiState(
        period = ScreenTimePeriod.MONTH, year = 2026, monthOffset = -1,
        startDate = start, endDate = start.plusDays(30), stats = stats,
        previousStats = stats.copy(totalWatchTimeMs = (768 - 72) * 60_000L), isLoading = false
    )
}

@PreviewTest
@Preview(name = "Screen time month 412", widthDp = 412, heightDp = 1600, locale = "zh-rCN", showBackground = true)
@Preview(name = "Screen time month 320", widthDp = 320, heightDp = 1800, locale = "zh-rCN", fontScale = 1.2f, showBackground = true)
@Preview(name = "Screen time month 320 font 200", widthDp = 320, heightDp = 3400, locale = "zh-rCN", fontScale = 2f, showBackground = true)
@Preview(name = "Screen time month English", widthDp = 412, heightDp = 1800, locale = "en", showBackground = true)
@Composable
fun ScreenTimeMonthPreview() {
    JellyCineTheme { ScreenTimeContent(screenTimePreviewState()) }
}

@PreviewTest
@Preview(name = "Video ranking by opens", widthDp = 412, heightDp = 720, locale = "zh-rCN")
@Preview(name = "Video ranking by opens 320 font 200", widthDp = 320, heightDp = 1400, locale = "zh-rCN", fontScale = 2f)
@Composable
fun ScreenTimeRankingsPreview() {
    JellyCineTheme {
        Column(
            Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VideoRankingsCard(screenTimePreviewState().stats!!, byOpens = true, onSortChanged = {})
        }
    }
}

@PreviewTest
@Preview(name = "Screen time filtered", widthDp = 412, heightDp = 1600, locale = "zh-rCN")
@Composable
fun ScreenTimeFilteredPreview() {
    val state = screenTimePreviewState()
    val filtered = state.stats!!.copy(
        totalWatchTimeMs = 240 * 60_000L, dailyAverageMs = 240 * 60_000L / 7,
        videoCount = 1, openCount = 2,
        topByWatchTime = listOf(VideoWatchSummary("1", "山野之间 · 秋天的第一次远行", 240 * 60_000L, 2)),
        topByOpenCount = listOf(VideoWatchSummary("1", "山野之间 · 秋天的第一次远行", 240 * 60_000L, 2)),
        peakHours = listOf("Morning", "Afternoon", "Evening", "Night").map { PeakHourBucket(it, if (it == "Evening") 240 * 60_000L else 0L) }
    )
    JellyCineTheme { ScreenTimeContent(state, stats = filtered, dayStats = filtered, dayFilter = 2) }
}

@PreviewTest
@Preview(name = "Screen time empty", widthDp = 320, heightDp = 1400, locale = "zh-rCN")
@Composable
fun ScreenTimeEmptyPreview() {
    val state = screenTimePreviewState()
    val empty = state.stats!!.copy(
        totalWatchTimeMs = 0, dailyAverageMs = 0, videoCount = 0, openCount = 0,
        dailyBreakdown = state.stats.dailyBreakdown.map { it.copy(watchTimeMs = 0) },
        peakHours = state.stats.peakHours.map { it.copy(watchTimeMs = 0) },
        topByWatchTime = emptyList(), topByOpenCount = emptyList()
    )
    JellyCineTheme { ScreenTimeContent(state.copy(stats = empty, previousStats = empty)) }
}

@PreviewTest
@Preview(name = "Screen time loading", widthDp = 320, heightDp = 640, locale = "zh-rCN")
@Composable
fun ScreenTimeLoadingPreview() {
    JellyCineTheme { ScreenTimeContent(screenTimePreviewState().copy(stats = null, previousStats = null, isLoading = true)) }
}

@PreviewTest
@Preview(name = "Screen time error", widthDp = 320, heightDp = 640, locale = "zh-rCN", fontScale = 2f)
@Composable
fun ScreenTimeErrorPreview() {
    JellyCineTheme { ScreenTimeContent(screenTimePreviewState().copy(stats = null, previousStats = null, error = "watch_history_load_failed")) }
}

@PreviewTest
@Preview(name = "Screen time refreshing", widthDp = 412, heightDp = 1600, locale = "zh-rCN")
@Composable
fun ScreenTimeRefreshingPreview() {
    JellyCineTheme { ScreenTimeContent(screenTimePreviewState().copy(isLoading = true)) }
}

@PreviewTest
@Preview(name = "Screen time refresh failed", widthDp = 412, heightDp = 1800, locale = "zh-rCN")
@Composable
fun ScreenTimeRefreshFailedPreview() {
    JellyCineTheme { ScreenTimeContent(screenTimePreviewState().copy(error = "watch_history_load_failed")) }
}
