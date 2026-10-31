package com.jellycine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import com.jellycine.app.ui.components.common.DownloadPanelState
import com.jellycine.app.ui.components.common.containerWidthDp
import com.jellycine.app.ui.screens.detail.ActionSection
import com.jellycine.app.ui.screens.detail.DetailCastName
import com.jellycine.app.ui.screens.detail.DetailFallbackTitle
import com.jellycine.app.ui.screens.detail.SeriesActionSection
import com.jellycine.app.ui.screens.detail.TrackSection
import com.jellycine.data.model.ItemDownloadState

@PreviewTest
@Preview(name = "Detail landscape 600", widthDp = 600, heightDp = 360, locale = "zh-rCN")
@Preview(name = "Detail landscape 640", widthDp = 640, heightDp = 320, locale = "zh-rCN")
@Preview(name = "Detail landscape large text", widthDp = 600, heightDp = 360, fontScale = 1.5f, locale = "zh-rCN")
@Composable
fun DetailLandscapeResponsivePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black)
                .verticalScroll(rememberScrollState()).padding(horizontal = 30.dp, vertical = 12.dp)
        ) {
            DetailPreviewTracks()
            DetailPreviewActions()
            SeriesActionSection(
                screenWidthDp = containerWidthDp(), useTabletLayout = true, buttonHeight = 40.dp,
                seriesDownload = DownloadPanelState(), seriesQueueInProgress = false,
                seriesDownloadProgress = 0f, seriesDownloadActionMenu = false,
                canResumeSeriesDownloads = false, hasActiveSeriesDownloads = false,
                isFavorite = false, hasTrailer = true, onTrailerClick = {},
                onSeriesDownloadClick = {}, onSeriesDownloadMenuChange = {},
                onPauseResumeSeriesDownloads = {}, onCancelSeriesDownloads = {}, onFavoriteClick = {}
            )
        }
    }
}

@Composable
private fun DetailPreviewTracks() {
    TrackSection(
        isWidescreenLayout = true,
        displayedSelectedVideo = "4K HEVC HDR10 原盘版本",
        videoOptions = listOf("4K HEVC HDR10 原盘版本", "1080p H.264"),
        videoInlineMetaText = null,
        selectedAudio = "普通话 Dolby Atmos 7.1",
        audioOptions = listOf("普通话 Dolby Atmos 7.1", "英语 DTS-HD 5.1"),
        selectedSubtitle = "简体中文与英文双语字幕",
        subtitleOptions = listOf("Off", "简体中文与英文双语字幕"),
        onVideoOptionSelected = {}, onAudioOptionSelected = {}, onSubtitleOptionSelected = {}
    )
}

@Composable
private fun DetailPreviewActions() {
    ActionSection(
        screenWidthDp = containerWidthDp(), useTabletLayout = true, buttonHeight = 40.dp,
        playButtonText = "继续播放", isPartiallyWatched = true, resumeProgress = 0.4f,
        canDownloadItem = true, itemDownloadState = ItemDownloadState(), isPausedDownload = false,
        hasActiveDownload = false, downloadActionMenu = false, downloadProgress = 0f,
        isFavorite = false, hasTrailer = true, onPlayClick = {}, onTrailerClick = {},
        onDownloadClick = {}, onDownloadMenuChange = {}, onPauseResumeDownload = {},
        onCancelDownload = {}, onFavoriteClick = {}
    )
}

@PreviewTest
@Preview(name = "Detail fallback 320 large", widthDp = 320, heightDp = 800, fontScale = 1.5f, locale = "zh-rCN")
@Preview(name = "Detail fallback 360 largest", widthDp = 360, heightDp = 900, fontScale = 2f, locale = "zh-rCN")
@Composable
fun DetailFallbackResponsivePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black)
                .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailFallbackTitle(text = "山野之间：穿越漫长冬夜的旅人", minHeight = 78.dp)
            DetailFallbackTitle(
                text = "漫长季节里相遇的我们与远方的故乡", minHeight = 58.dp,
                fontSize = 17.sp, lineHeight = 20.sp, contentAlignment = Alignment.CenterStart
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("张小远", "亚历山大·斯卡斯加德", "凯瑟琳·泽塔琼斯").forEach { name ->
                    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(66.dp).clip(CircleShape).background(Color(0xFF2A2A2A)))
                        DetailCastName(name, Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.size(4.dp))
            DetailPreviewTracks()
        }
    }
}
