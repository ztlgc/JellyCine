package com.jellycine.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.jellycine.app.R
import com.jellycine.app.ui.screens.detail.VideoDetailContent
import com.jellycine.app.ui.screens.detail.VideoRatingButton
import com.jellycine.app.ui.screens.player.ControlsOverlay
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaSourceInfo
import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.app.ui.screens.detail.VideoDetailMoreActions
import com.jellycine.shared.ui.components.common.DetailDownloadActionState
import com.jellycine.player.core.AudioTrackInfo

private val streams = listOf(
    MediaStream(type = "Video", codec = "hevc", width = 3840, height = 2160, averageFrameRate = 30f),
    MediaStream(type = "Audio", codec = "aac", channels = 2)
)
private val sample = BaseItemDto(id = "preview", name = "山野之间", type = "Video", productionYear = 2026,
    overview = "走进山谷与湖泊，记录旅途中的风与光。", runTimeTicks = 11220000000L,
    userData = UserItemDataDto(playbackPositionTicks = 3780000000L), mediaStreams = streams,
    mediaSources = listOf(MediaSourceInfo(path = "/旅行/山野之间_4K.mp4", container = "mp4", size = 2340000000L)))
private val audio = AudioTrackInfo("0", "原声 · AAC 双声道", "zh", 2, "aac")

@Composable
private fun PreviewPhoto(contentScale: ContentScale = ContentScale.Crop) {
    Image(painterResource(R.drawable.playback_preview_mountain), null, Modifier.fillMaxSize(), contentScale = contentScale)
}

@PreviewTest
@Preview(name = "Detail 412", widthDp = 412, heightDp = 892, locale = "zh-rCN", showBackground = true)
@Preview(name = "Detail small", widthDp = 360, heightDp = 800, locale = "zh-rCN", fontScale = 1.15f, showBackground = true)
@Composable
fun VideoDetailDesignPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        VideoDetailContent(item = sample, imageUrl = null, streams = streams, isFavorite = false, isWatched = false,
            onBack = {}, onFavorite = {}, onWatched = {}, onDelete = {},
            canDelete = true, favoriteSaving = false, onPlay = {}, onImageError = {},
            rating = { VideoRatingButton(8f, onClick = {}) }, backdrop = { PreviewPhoto(ContentScale.Fit) },
            moreActions = { VideoDetailMoreActions(itemId = sample.id,
                downloadState = DetailDownloadActionState.Idle, downloadProgress = 0f,
                playbackEnabled = true, onDownload = {}, onPlayWithEngine = {},
                expanded = false, onExpandedChange = {}) })
    }
}

@PreviewTest
@Preview(name = "Player 892", widthDp = 892, heightDp = 412, locale = "zh-rCN", showBackground = true)
@Preview(name = "Player compact", widthDp = 640, heightDp = 320, locale = "zh-rCN", showBackground = true)
@Preview(name = "Player portrait", widthDp = 360, heightDp = 800, locale = "zh-rCN", showBackground = true)
@Composable
fun PlayerDesignPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PreviewPhoto()
            ControlsOverlay(title = "山野之间", videoFormatLabel = "4K · HEVC", isPlaying = true,
                currentPosition = 378000L, duration = 1122000L, bufferedPosition = 650000L,
                onBackClick = {}, onPlayPause = {}, onSeek = {}, audioGainAvailable = true,
                audioTracks = listOf(audio), currentAudioTrack = audio, onSelectAudioTrack = {})
        }
    }
}

@PreviewTest
@Preview(name = "Sound 892", widthDp = 892, heightDp = 412, locale = "zh-rCN", showBackground = true)
@Preview(name = "Sound compact", widthDp = 640, heightDp = 320, locale = "zh-rCN", showBackground = true)
@Composable
fun SoundDesignPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            PreviewPhoto()
            ControlsOverlay(title = "山野之间", videoFormatLabel = "4K · HEVC", isPlaying = true,
                currentPosition = 378000L, duration = 1122000L, onBackClick = {}, onPlayPause = {},
                onSeek = {}, audioGainPercent = 80, audioGainAvailable = true, initialSoundPanelVisible = true,
                audioTracks = listOf(audio), currentAudioTrack = audio, onSelectAudioTrack = {})
        }
    }
}
