package com.jellycine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.jellycine.app.ui.components.common.SelectionDialogContent
import com.jellycine.app.ui.components.common.SelectionOption
import com.jellycine.app.ui.screens.auth.ConnectionForm
import com.jellycine.app.ui.screens.auth.LoginForm
import com.jellycine.data.model.DailyWatchTime
import com.jellycine.data.model.DownloadStatus
import com.jellycine.data.model.ItemDownloadState
import com.jellycine.data.model.TrackedDownload
import com.jellycine.app.ui.screens.dashboard.settings.DownloadMediaRow
import com.jellycine.app.ui.screens.dashboard.settings.artwork
import com.jellycine.shared.preferences.Preferences
import com.jellycine.shared.ui.components.common.ThemeMusicSettingsItem
import com.jellycine.shared.ui.components.screentime.DailyBreakdownChart
import java.time.LocalDate

@PreviewTest
@Preview(name = "Auth forms 320", widthDp = 320, heightDp = 1000, locale = "zh-rCN")
@Preview(name = "Auth forms 320 font 200", widthDp = 320, heightDp = 1800, locale = "zh-rCN", fontScale = 2f)
@Composable
fun AuthFormsResponsivePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionForm(
                serverUrl = "https://jellyfin.example.com",
                isLoading = false,
                errorMessage = null,
                onServerUrlChange = {},
                onConnect = {}
            )
            LoginForm(
                username = "影音用户",
                password = "password",
                isLoading = false,
                errorMessage = null,
                showQuickConnect = true,
                isQuickConnectLoading = true,
                quickConnectCode = "123456789012345678901234567890",
                onUsernameChange = {},
                onPasswordChange = {},
                onLogin = {},
                onQuickConnect = {}
            )
        }
    }
}

@PreviewTest
@Preview(name = "Theme music 320", widthDp = 320, heightDp = 420, locale = "zh-rCN")
@Preview(name = "Theme music 320 font 200", widthDp = 320, heightDp = 700, locale = "zh-rCN", fontScale = 2f)
@Composable
fun ThemeMusicResponsivePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())) {
            ThemeMusicSettingsItem(
                selectedMode = Preferences.THEME_MUSIC_ONCE,
                onModeSelected = {},
                accentColor = Color(0xFF22D3EE),
                volume = 0.65f
            )
        }
    }
}

@PreviewTest
@Preview(name = "Selection 320", widthDp = 320, heightDp = 560, locale = "zh-rCN")
@Preview(name = "Selection 320 font 200", widthDp = 320, heightDp = 720, locale = "zh-rCN", fontScale = 2f)
@Composable
fun SelectionDialogResponsivePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
            SelectionDialogContent(
                title = "播放器",
                options = listOf(
                    SelectionOption("exo", "ExoPlayer / MediaCodec 硬件解码", "使用系统媒体编解码器播放视频", isDefault = true),
                    SelectionOption("mpv", "MPV 播放器", "使用 MPV 播放视频")
                ),
                selectedValue = "exo",
                accentColor = Color(0xFF22D3EE),
                onOptionSelected = {}
            )
        }
    }
}

@PreviewTest
@Preview(name = "Downloads 320", widthDp = 320, heightDp = 420, locale = "zh-rCN")
@Preview(name = "Downloads 320 font 200", widthDp = 320, heightDp = 850, locale = "zh-rCN", fontScale = 2f)
@Composable
fun DownloadRowsResponsivePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf("Movie", "Episode").forEach { type ->
                val title = if (type == "Movie") "山野之间的第一次远行" else "第 12 集 · 秋天的第一次远行"
                val entry = TrackedDownload(
                    itemId = type, item = null, title = title, mediaType = type, year = 2026,
                    state = ItemDownloadState(status = DownloadStatus.DOWNLOADING, progress = 0.52f,
                        downloadedBytes = 5_200_000_000L, totalBytes = 10_000_000_000L),
                    isOfflineAvailable = false, fileSizeBytes = null, requestedAt = 0L, completedAt = null
                )
                val preferredWidth = if (type == "Movie") 148.dp else 136.dp
                DownloadMediaRow(
                    entry = entry, title = title, metaText = "2026 · 10.0 GB",
                    statusLabel = "正在下载 52% · 5.2 GB / 10.0 GB",
                    titleMaxLines = if (type == "Movie") 1 else 2, verticalPadding = 5.dp,
                    canPlay = false, deleting = false, onDelete = {}, onPlay = {}, onPause = {}, onCancel = {},
                    preferredArtworkWidth = preferredWidth,
                    artwork = { width ->
                        artwork(title, imageUrl = null, logoUrl = null, width = width,
                            height = width * if (type == "Movie") (84f / 148f) else (74f / 136f))
                    }
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "Annual labels 320", widthDp = 320, heightDp = 620, locale = "zh-rCN")
@Preview(name = "Annual labels 320 font 200", widthDp = 320, heightDp = 850, locale = "zh-rCN", fontScale = 2f)
@Preview(name = "Annual labels 320 English font 200", widthDp = 320, heightDp = 850, locale = "en", fontScale = 2f)
@Composable
fun AnnualChartResponsivePreview() {
    val start = LocalDate.of(2026, 1, 1)
    val dailyData = List(start.lengthOfYear()) { index ->
        val date = start.plusDays(index.toLong())
        DailyWatchTime(date.toEpochDay(), if (date.dayOfMonth == 1) 361_800_000L else 0L)
    }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            DailyBreakdownChart(dailyData, selectedIndex = null, onBucketClick = {})
            DailyBreakdownChart(dailyData, selectedIndex = 0, onBucketClick = {}, onClearSelection = {})
            DailyBreakdownChart(dailyData, selectedIndex = 11, onBucketClick = {}, onClearSelection = {})
        }
    }
}
