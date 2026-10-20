package com.jellycine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.jellycine.app.ui.screens.screentime.OverviewCards
import com.jellycine.app.ui.screens.screentime.VideoRankingCard
import com.jellycine.data.model.*
import com.jellycine.shared.R

@PreviewTest
@Preview(name = "Video statistics", widthDp = 412, heightDp = 1120, locale = "zh-rCN", showBackground = true)
@Preview(name = "Video statistics small", widthDp = 320, heightDp = 1280, locale = "zh-rCN", fontScale = 1.2f, showBackground = true)
@Composable
fun ScreenTimeRankingsPreview() {
    val videos = listOf(
        VideoWatchSummary("1", "山野之间 · 秋天的第一次远行", 5_412_000, 12),
        VideoWatchSummary("2", "2026 国庆家庭聚会", 2_000_000, 6),
        VideoWatchSummary("3", "海边日落", 1_210_000, 8),
        VideoWatchSummary("4", "城市漫步：一段很长的视频标题，用于检查小屏幕上的换行与省略", 110_000, 9),
        VideoWatchSummary("5", "第一次练习", 450, 2)
    )
    val byOpens = videos.sortedByDescending { it.openCount }
    val stats = ScreenTimeStats(videos.sumOf { it.watchTimeMs }, 1_234_500, emptyList(), emptyList(), 8, 42, videos, byOpens)
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.settings_screen_time), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(stringResource(R.string.screen_time_local_caption), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            OverviewCards(stats, null, false)
            VideoRankingCard(stringResource(R.string.screen_time_top_duration), videos, false)
            VideoRankingCard(stringResource(R.string.screen_time_top_opens), byOpens, true)
        }
    }
}
