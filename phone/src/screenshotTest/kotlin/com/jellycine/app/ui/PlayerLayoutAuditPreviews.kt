package com.jellycine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.jellycine.app.ui.screens.cast.CastTrackOption
import com.jellycine.app.ui.screens.cast.TrackOptionRow
import com.jellycine.app.ui.screens.player.ControlsOverlay
import com.jellycine.app.ui.screens.player.PlayerSegmentActions
import com.jellycine.app.ui.screens.player.TrackRow
import com.jellycine.player.core.SkippableSegmentAction
import com.jellycine.player.core.SkippableSegmentType

@PreviewTest
@Preview(name = "Intro controls compact", widthDp = 640, heightDp = 320, locale = "zh-rCN")
@Preview(name = "Intro controls large font", widthDp = 360, heightDp = 800, fontScale = 2f, locale = "zh-rCN")
@Composable
fun PlayerIntroLayoutAuditPreview() {
    PlayerSegmentLayoutPreview(credits = false)
}

@PreviewTest
@Preview(name = "Credits controls compact", widthDp = 640, heightDp = 320, locale = "zh-rCN")
@Preview(name = "Credits controls large font", widthDp = 360, heightDp = 800, fontScale = 2f, locale = "zh-rCN")
@Composable
fun PlayerCreditsLayoutAuditPreview() {
    PlayerSegmentLayoutPreview(credits = true)
}

@PreviewTest
@Preview(name = "Credits locked", widthDp = 640, heightDp = 320, locale = "zh-rCN")
@Composable
fun PlayerLockedCreditsLayoutAuditPreview() {
    PlayerSegmentLayoutPreview(credits = true, locked = true)
}

@PreviewTest
@Preview(name = "Credits sound drawer", widthDp = 640, heightDp = 320, locale = "zh-rCN")
@Composable
fun PlayerSoundCreditsLayoutAuditPreview() {
    PlayerSegmentLayoutPreview(credits = true, soundPanel = true)
}

@PreviewTest
@Preview(name = "Credits without controls large font", widthDp = 360, heightDp = 800, fontScale = 2f, locale = "zh-rCN")
@Composable
fun PlayerHiddenControlsCreditsLayoutAuditPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 28.dp)) {
                AuditSegmentActions(credits = true)
            }
        }
    }
}

@Composable
private fun PlayerSegmentLayoutPreview(credits: Boolean, locked: Boolean = false, soundPanel: Boolean = false) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ControlsOverlay(
                title = "山野之间",
                videoFormatLabel = "4K · HEVC",
                isPlaying = true,
                currentPosition = 378000L,
                duration = 1122000L,
                bufferedPosition = 650000L,
                onBackClick = {},
                onPlayPause = {},
                onSeek = {},
                canPlayPreviousEpisode = true,
                canPlayNextEpisode = true,
                isLocked = locked,
                initialSoundPanelVisible = soundPanel,
                segmentActions = { AuditSegmentActions(credits) }
            )
        }
    }
}

@Composable
private fun AuditSegmentActions(credits: Boolean) {
    val segment = SkippableSegmentAction(
        type = if (credits) SkippableSegmentType.CREDITS else SkippableSegmentType.INTRO,
        startMs = 1000L,
        endMs = 90000L,
        seekToMs = 90000L
    )
    PlayerSegmentActions(
        activeSkippableSegment = segment,
        activeCreditsSegment = segment.takeIf { credits },
        dismissedCreditsPrompt = false,
        canWatchNextEpisode = true,
        nextEpisodeButtonProgress = 0.45f,
        onSkipSegment = {},
        onWatchCredits = {},
        onWatchNextEpisode = {}
    )
}

@PreviewTest
@Preview(name = "Full track names large font", widthDp = 360, heightDp = 800, fontScale = 1.5f, locale = "zh-rCN")
@Composable
fun FullTrackNamesLayoutAuditPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
            TrackRow(
                indexLabel = "1",
                title = "普通话原声配音 · 导演评论与幕后解说完整无删减版本 Dolby TrueHD Atmos 7.1",
                subtitle = "简体中文 · Dolby TrueHD · 7.1",
                description = "无损音频 | 环绕声",
                isSelected = true,
                accentColor = Color(0xFF00A9D6),
                onSelected = {}
            )
            TrackRow(
                indexLabel = "2",
                title = "简体中文与英文双语字幕 · 特效字幕制作组完整翻译修订版（默认）",
                subtitle = "简体中文 | 默认 | 强制",
                description = "默认轨道 | 强制字幕",
                isSelected = false,
                accentColor = Color(0xFFFF6B3B),
                onSelected = {}
            )
        }
    }
}

@PreviewTest
@Preview(name = "Cast full selected track 320", widthDp = 320, heightDp = 640, locale = "zh-rCN")
@Preview(name = "Cast full selected track large font", widthDp = 360, heightDp = 800, fontScale = 2f, locale = "zh-rCN")
@Composable
fun CastTrackNamesLayoutAuditPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
            TrackOptionRow(
                label = "音轨",
                options = listOf(
                    CastTrackOption("普通话原声配音 · 导演评论与幕后解说完整无删减版本 Dolby TrueHD Atmos 7.1", 1),
                    CastTrackOption("普通话原声配音 · 影院公映版本 Dolby Digital Plus 5.1", 2)
                ),
                selectedTrackIndex = 1,
                onTrackSelected = {},
                enabled = true
            )
            TrackOptionRow(
                label = "字幕",
                options = listOf(
                    CastTrackOption("关闭", -1),
                    CastTrackOption("简体中文与英文双语字幕 · 特效字幕制作组完整翻译修订版（默认）", 3)
                ),
                selectedTrackIndex = 3,
                onTrackSelected = {},
                enabled = true
            )
        }
    }
}
