package com.jellycine.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.jellycine.app.R
import com.jellycine.app.ui.screens.detail.VideoDetailContent
import com.jellycine.app.ui.screens.detail.VideoDetailMoreActions
import com.jellycine.app.ui.screens.detail.VideoDetailMoreMenuContent
import com.jellycine.app.ui.screens.detail.VideoRatingButton
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaSourceInfo
import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.shared.ui.components.common.DetailDownloadActionState

@PreviewTest
@Preview(name = "Detail long text", widthDp = 412, heightDp = 1030, locale = "zh-rCN", showBackground = true)
@Preview(name = "Detail long small", widthDp = 360, heightDp = 1030, locale = "zh-rCN", fontScale = 1.15f, showBackground = true)
@Composable
fun VideoDetailLongTextPreview() {
    val title = "山野之间：穿越阿尔卑斯山谷与湖泊的秋日徒步旅行全记录（意大利多洛米蒂·第三集）"
    val fileName = "山野之间_穿越阿尔卑斯山谷与湖泊的秋日徒步旅行全记录_意大利多洛米蒂第三集_4K原画完整版.mp4"
    val streams = listOf(
        MediaStream(type = "Video", codec = "hevc", width = 3840, height = 2160, averageFrameRate = 30f),
        MediaStream(type = "Audio", codec = "aac", channels = 2)
    )
    val item = BaseItemDto(
        id = "preview", name = title, type = "Video", productionYear = 2026,
        overview = "走进山谷与湖泊，记录旅途中的风与光。", runTimeTicks = 11220000000L,
        userData = UserItemDataDto(playbackPositionTicks = 3780000000L), mediaStreams = streams,
        mediaSources = listOf(MediaSourceInfo(
            path = "/volume1/家庭影像/旅行记录/2026年欧洲自驾/意大利/多洛米蒂国家公园/第三天_山谷与湖泊/$fileName",
            container = "mp4", size = 2340000000L
        ))
    )
    MaterialTheme(colorScheme = darkColorScheme()) {
        VideoDetailContent(
            item = item, imageUrl = null, streams = streams, isFavorite = false, isWatched = false,
            onBack = {}, onFavorite = {}, onWatched = {}, onDelete = {}, canDelete = true,
            favoriteSaving = false, onPlay = {}, onImageError = {},
            rating = { VideoRatingButton(8f, onClick = {}) },
            backdrop = {
                Image(painterResource(R.drawable.playback_preview_mountain), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            },
            moreActions = {
                var expanded by remember { mutableStateOf(false) }
                VideoDetailMoreActions(itemId = item.id,
                    downloadState = DetailDownloadActionState.Idle, downloadProgress = 0f,
                    playbackEnabled = true, onDownload = {}, onPlayWithEngine = {},
                    expanded = expanded, onExpandedChange = { expanded = it })
            }
        )
    }
}


@PreviewTest
@Preview(name = "More downloading", widthDp = 300, heightDp = 210, locale = "zh-rCN", showBackground = true)
@Composable
fun VideoDetailMoreMenuPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        VideoDetailMoreMenuContent(downloadState = DetailDownloadActionState.Downloading,
            downloadProgress = 0.42f, playbackEnabled = true, onDownload = {}, onPlayWithEngine = {})
    }
}
