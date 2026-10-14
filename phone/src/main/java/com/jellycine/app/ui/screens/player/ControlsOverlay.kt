package com.jellycine.app.ui.screens.player

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jellycine.shared.R
import com.jellycine.detail.SpatializationResult
import com.jellycine.player.core.ChapterMarker
import com.jellycine.player.core.PlayerConstants.PROGRESS_BAR_HEIGHT_DP
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ControlsOverlay(
    title: String,
    mediaLogoUrl: String? = null,
    seasonEpisodeLabel: String? = null,
    chapterMarkers: List<ChapterMarker> = emptyList(),
    isPlaying: Boolean,
    currentPosition: Long,
    bufferedPosition: Long = 0L,
    duration: Long,
    onBackClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    spatializationResult: SpatializationResult? = null,
    isSpatialAudioEnabled: Boolean = false,
    isHdrEnabled: Boolean = false,
    hdrFormat: String = "",
    onShowMediaInfo: () -> Unit = {},
    isLocked: Boolean = false,
    onToggleLock: () -> Unit = {},
    currentStreamingQuality: String = "",
    showPlaybackSettingsButton: Boolean = true,
    onShowPlaybackSettings: () -> Unit = {},
    onShowAudioTrackSelection: () -> Unit = {},
    onShowSubtitleTrackSelection: () -> Unit = {},
    onCycleAspectRatio: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    seekBackwardSeconds: Int = 30,
    seekForwardSeconds: Int = 30,
    canPlayPreviousEpisode: Boolean = false,
    canPlayNextEpisode: Boolean = false,
    onPlayPreviousEpisode: () -> Unit = {},
    onPlayNextEpisode: () -> Unit = {},
    onScrubStateChange: (Boolean) -> Unit = {},
    playbackSpeed: Float = 1f,
    audioGainPercent: Int = 0,
    audioGainAvailable: Boolean = false,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    onAudioGainChange: (Int) -> Unit = {},
    onToggleOrientation: () -> Unit = {}
) {
    var scrubPreviewProgress by remember { mutableStateOf<Float?>(null) }
    var optionsMenu by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var gainDialog by remember { mutableStateOf(false) }
    val latestScrubCallback by rememberUpdatedState(onScrubStateChange)
    DisposableEffect(optionsMenu, speedDialog, gainDialog) {
        if (optionsMenu || speedDialog || gainDialog) latestScrubCallback(true)
        onDispose { if (optionsMenu || speedDialog || gainDialog) latestScrubCallback(false) }
    }
    val displayedPosition = scrubPreviewProgress?.let { (duration * it).toLong() } ?: currentPosition
    val panelColor = Color.Black.copy(alpha = 0.55f)
    val panelShape = RoundedCornerShape(28.dp)
    val speedLabel = "${playbackSpeed}×"
    BoxWithConstraints(modifier.fillMaxSize().safeDrawingPadding()) {
        val portrait = maxHeight > maxWidth
        val compact = maxWidth < 400.dp || maxHeight < 360.dp
        val edge = if (compact) 12.dp else 24.dp
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(edge),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isLocked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerRoundButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.player_control_back), onBackClick)
                    Text(title, Modifier.weight(1f).padding(horizontal = 12.dp), color = Color.White,
                        fontSize = if (compact) 16.sp else 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!portrait) {
                        PlayerTopActions(onToggleOrientation, onCycleAspectRatio, { optionsMenu = true })
                    }
                }
                if (portrait) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PlayerTopActions(onToggleOrientation, onCycleAspectRatio, { optionsMenu = true })
                    }
                }
            }
        }
        PlayerRoundButton(if (isLocked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
            stringResource(if (isLocked) R.string.player_control_unlock else R.string.player_control_lock),
            onToggleLock, Modifier.align(Alignment.CenterStart).padding(start = edge)
                .offset(y = if (portrait) (-100).dp else 0.dp))
        if (!isLocked) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 24.dp)) {
                if (canPlayPreviousEpisode) PlayerRoundButton(Icons.Filled.SkipPrevious,
                    stringResource(R.string.player_previous_episode), onPlayPreviousEpisode)
                PlayerRoundButton(replayIcon(seekBackwardSeconds),
                    stringResource(R.string.player_control_rewind, seekBackwardSeconds), onSeekBackward)
                PlayerRoundButton(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(if (isPlaying) R.string.player_control_pause else R.string.player_control_play),
                    onPlayPause, size = if (compact) 64.dp else 80.dp)
                PlayerRoundButton(replayforwardIcon(seekForwardSeconds),
                    stringResource(R.string.player_control_forward, seekForwardSeconds), onSeekForward)
                if (canPlayNextEpisode) PlayerRoundButton(Icons.Filled.SkipNext,
                    stringResource(R.string.player_next_episode), onPlayNextEpisode)
            }
            if (!portrait) {
                Column(Modifier.align(Alignment.CenterEnd).padding(end = edge)
                    .background(panelColor, panelShape), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { onPlaybackSpeedChange(com.jellycine.player.core.PlaybackOptions.stepSpeed(playbackSpeed, -1)) },
                        enabled = playbackSpeed > 0.5f) {
                        Icon(Icons.Filled.Remove, stringResource(R.string.player_speed_slower), tint = Color.White)
                    }
                    TextButton(onClick = { speedDialog = true }) { Text(speedLabel, color = Color.White) }
                    IconButton(onClick = { onPlaybackSpeedChange(com.jellycine.player.core.PlaybackOptions.stepSpeed(playbackSpeed, 1)) },
                        enabled = playbackSpeed < 2f) {
                        Icon(Icons.Filled.Add, stringResource(R.string.player_speed_faster), tint = Color.White)
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(edge),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!seasonEpisodeLabel.isNullOrBlank()) Text(seasonEpisodeLabel, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Column(Modifier.fillMaxWidth().background(panelColor, panelShape).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatTime(displayedPosition)} / ${formatTime(duration.coerceAtLeast(0))}",
                            Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
                        if (portrait) {
                            IconButton(onClick = { onPlaybackSpeedChange(com.jellycine.player.core.PlaybackOptions.stepSpeed(playbackSpeed, -1)) },
                                enabled = playbackSpeed > 0.5f) {
                                Icon(Icons.Filled.Remove, stringResource(R.string.player_speed_slower), tint = Color.White)
                            }
                            TextButton(onClick = { speedDialog = true }, contentPadding = PaddingValues(4.dp)) {
                                Text(speedLabel, color = Color.White)
                            }
                            IconButton(onClick = { onPlaybackSpeedChange(com.jellycine.player.core.PlaybackOptions.stepSpeed(playbackSpeed, 1)) },
                                enabled = playbackSpeed < 2f) {
                                Icon(Icons.Filled.Add, stringResource(R.string.player_speed_faster), tint = Color.White)
                            }
                        } else {
                            if (isHdrEnabled) Text(osdHdrLabel(hdrFormat), color = Color.White, fontSize = 12.sp)
                            if (isSpatialAudioEnabled) Icon(ImageVector.vectorResource(R.drawable.ic_spatial_audio),
                                contentDescription = null, tint = Color.White, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SeekBar(progress = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            bufferedProgress = if (duration > 0) (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            duration = duration, chapterMarkers = chapterMarkers, onSeek = onSeek,
                            onScrubProgressChange = { scrubPreviewProgress = it; onScrubStateChange(it != null) },
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = onShowAudioTrackSelection) {
                            Icon(Icons.Outlined.Audiotrack, stringResource(R.string.player_control_audio), tint = Color.White)
                        }
                        IconButton(onClick = onShowSubtitleTrackSelection) {
                            Icon(Icons.Outlined.Subtitles, stringResource(R.string.player_control_subtitles), tint = Color.White)
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = if (portrait) 116.dp else 68.dp, end = edge)) {
                DropdownMenu(expanded = optionsMenu, onDismissRequest = { optionsMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.player_speed_title)) },
                        onClick = { optionsMenu = false; speedDialog = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.player_gain_title)) },
                        onClick = { optionsMenu = false; gainDialog = true })
                    if (showPlaybackSettingsButton) DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_control_settings)) },
                        onClick = { optionsMenu = false; onShowPlaybackSettings() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.player_control_info)) },
                        onClick = { optionsMenu = false; onShowMediaInfo() })
                }
            }
        }
    }
    if (speedDialog) AlertDialog(
        onDismissRequest = { speedDialog = false }, title = { Text(stringResource(R.string.player_speed_title)) },
        text = { Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            com.jellycine.player.core.PlaybackOptions.speeds.forEach { speed ->
                TextButton(onClick = { onPlaybackSpeedChange(speed); speedDialog = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("${if (speed == playbackSpeed) "✓  " else ""}$speed×")
                }
            }
        } }, confirmButton = { TextButton(onClick = { speedDialog = false }) { Text(stringResource(R.string.player_control_close)) } })
    if (gainDialog) AlertDialog(
        onDismissRequest = { gainDialog = false }, title = { Text(stringResource(R.string.player_gain_title)) },
        text = { Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            if (!audioGainAvailable) Text(stringResource(R.string.player_gain_unavailable))
            com.jellycine.player.core.PlaybackOptions.gainPercentages.forEach { percent ->
                TextButton(onClick = { onAudioGainChange(percent); gainDialog = false }, enabled = percent == 0 || audioGainAvailable,
                    modifier = Modifier.fillMaxWidth()) {
                    Text((if (percent == audioGainPercent) "✓  " else "") +
                        if (percent == 0) stringResource(R.string.player_gain_off) else "+$percent%")
                }
            }
        } }, confirmButton = { TextButton(onClick = { gainDialog = false }) { Text(stringResource(R.string.player_control_close)) } })
}

@Composable
private fun PlayerTopActions(onRotate: () -> Unit, onAspect: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(28.dp))) {
        IconButton(onClick = onRotate) { Icon(Icons.Filled.ScreenRotation, stringResource(R.string.player_control_rotate), tint = Color.White) }
        IconButton(onClick = onAspect) { Icon(Icons.Outlined.AspectRatio, stringResource(R.string.player_control_aspect), tint = Color.White) }
        IconButton(onClick = onMore) { Icon(Icons.Filled.MoreHoriz, stringResource(R.string.player_control_more), tint = Color.White) }
    }
}

@Composable
private fun PlayerRoundButton(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 48.dp) {
    IconButton(onClick = onClick, modifier = modifier.size(size)
        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (size > 48.dp) 40.dp else 28.dp))
    }
}

private fun replayIcon(seconds: Int): ImageVector {
    return when (seconds) {
        5 -> Icons.Filled.Replay5
        10 -> Icons.Filled.Replay10
        30 -> Icons.Filled.Replay30
        else -> Icons.Filled.Replay
    }
}

private fun replayforwardIcon(seconds: Int): ImageVector {
    return when (seconds) {
        5 -> Icons.Filled.Forward5
        10 -> Icons.Filled.Forward10
        30 -> Icons.Filled.Forward30
        else -> Icons.Filled.Replay
    }
}

@Composable
private fun SeekBar(
    progress: Float,
    bufferedProgress: Float = 0f,
    duration: Long,
    chapterMarkers: List<ChapterMarker>,
    onSeek: (Float) -> Unit,
    onScrubProgressChange: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    var scrubProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var dragActive by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val trackHeightFraction by animateFloatAsState(
        targetValue = if (dragActive) 0.95f else 0.55f,
        label = "seekTrackHeight"
    )
    val thumbRadiusFraction by animateFloatAsState(
        targetValue = if (dragActive) 0.52f else 0.36f,
        label = "seekThumbRadius"
    )
    val bubbleYOffsetPx = with(density) { (-42).dp.roundToPx() }

    LaunchedEffect(progress) {
        if (!dragActive) {
            scrubProgress = progress.coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .height(PROGRESS_BAR_HEIGHT_DP.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInteropFilter { event ->
                if (widthPx <= 0) return@pointerInteropFilter false

                val newProgress = (event.x / widthPx.toFloat()).coerceIn(0f, 1f)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragActive = true
                        scrubProgress = newProgress
                        onScrubProgressChange(scrubProgress)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        scrubProgress = newProgress
                        onScrubProgressChange(scrubProgress)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        scrubProgress = newProgress
                        dragActive = false
                        onSeek(scrubProgress)
                        onScrubProgressChange(null)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        dragActive = false
                        scrubProgress = progress.coerceIn(0f, 1f)
                        onScrubProgressChange(null)
                        true
                    }
                    else -> false
                }
            }
    ) {
        val renderedProgress = scrubProgress.coerceIn(0f, 1f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(PROGRESS_BAR_HEIGHT_DP.dp)
                .align(Alignment.BottomCenter)
        ) {
            val yOffset = size.height / 2
            val trackInset = 2.dp.toPx()
            val trackStart = Offset(trackInset, yOffset)
            val trackEnd = Offset(size.width - trackInset, yOffset)
            val trackHeight = size.height * trackHeightFraction
            val markerSpacingPx = 6.dp.toPx()
            val markerStrokeWidth = 1.5.dp.toPx()
            val markerVerticalInset = 1.dp.toPx()
            var progressX: Float? = null

            // Background full track
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = trackStart,
                end = trackEnd,
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )

            // Buffered progress track
            val clampedBufferedProgress = bufferedProgress.coerceIn(0f, 1f)
            if (clampedBufferedProgress > 0f) {
                val bufferedX = trackStart.x + (trackEnd.x - trackStart.x) * clampedBufferedProgress
                drawLine(
                    color = Color.White.copy(alpha = 0.55f),
                    start = trackStart,
                    end = Offset(bufferedX, yOffset),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            // Played progress track
            if (renderedProgress > 0f) {
                progressX = trackStart.x + (trackEnd.x - trackStart.x) * renderedProgress
                drawLine(
                    color = Color.White.copy(alpha = 0.95f),
                    start = trackStart,
                    end = Offset(progressX, yOffset),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round
                )
            }

            if (duration > 0L && chapterMarkers.isNotEmpty()) {
                var lastMarkerX = Float.NEGATIVE_INFINITY
                chapterMarkers.forEach { marker ->
                    val markerProgress = (marker.positionMs.toFloat() / duration.toFloat())
                        .coerceIn(0f, 1f)
                    if (markerProgress <= 0f || markerProgress >= 1f) return@forEach

                    val markerX = trackStart.x + (trackEnd.x - trackStart.x) * markerProgress
                    if (abs(markerX - lastMarkerX) < markerSpacingPx) return@forEach

                    drawLine(
                        color = Color.White.copy(alpha = 0.95f),
                        start = Offset(markerX, yOffset - trackHeight / 2f + markerVerticalInset),
                        end = Offset(markerX, yOffset + trackHeight / 2f - markerVerticalInset),
                        strokeWidth = markerStrokeWidth,
                        cap = StrokeCap.Round
                    )
                    lastMarkerX = markerX
                }
            }

            progressX?.let { thumbX ->
                drawCircle(
                    color = Color.White,
                    radius = size.height * thumbRadiusFraction,
                    center = Offset(thumbX, yOffset)
                )
            }
        }

        AnimatedVisibility(
            visible = dragActive && duration > 0L && widthPx > 0,
            modifier = Modifier
                .align(Alignment.TopStart)
                .wrapContentSize(unbounded = true)
                .zIndex(1f)
                .offset {
                    val thumbCenterX = (widthPx * renderedProgress).roundToInt()
                    IntOffset(thumbCenterX - 32.dp.roundToPx(), bubbleYOffsetPx)
                },
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.92f),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 4.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Text(
                    text = formatTime((duration * renderedProgress).toLong()),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Preview(
    name = "Controls - Playing",
    showBackground = true,
    widthDp = 800,
    heightDp = 450,
    backgroundColor = 0xFF000000
)
@Composable
fun ControlsOverlayPreviewPlaying() {
    ControlsOverlay(
        title = "The Matrix Reloaded",
        chapterMarkers = listOf(
            ChapterMarker(positionMs = 540000L, label = "Chapter 1"),
            ChapterMarker(positionMs = 1740000L, label = "Chapter 2"),
            ChapterMarker(positionMs = 3120000L, label = "Chapter 3")
        ),
        isPlaying = true,
        currentPosition = 1800000L,
        duration = 8280000L,
        onBackClick = { },
        onPlayPause = { },
        onSeek = { },
        spatializationResult = SpatializationResult(
            canSpatialize = true,
            reason = "Content and device support spatial audio",
            spatialFormat = "Dolby Atmos"
        ),
        isSpatialAudioEnabled = true,
        onShowMediaInfo = { },
        isLocked = false,
        onToggleLock = { },
        onShowAudioTrackSelection = { },
        onShowSubtitleTrackSelection = { },
        onCycleAspectRatio = { },
        onSeekBackward = { },
        onSeekForward = { },
        canPlayPreviousEpisode = true,
        canPlayNextEpisode = true
    )
}

@Preview(
    name = "Controls - Paused",
    showBackground = true,
    widthDp = 800,
    heightDp = 450,
    backgroundColor = 0xFF000000
)
@Composable
fun ControlsOverlayPreviewPaused() {
    ControlsOverlay(
        title = "Inception",
        chapterMarkers = listOf(
            ChapterMarker(positionMs = 600000L, label = "Dream 1"),
            ChapterMarker(positionMs = 2520000L, label = "Dream 2"),
            ChapterMarker(positionMs = 5100000L, label = "Dream 3")
        ),
        isPlaying = false,
        currentPosition = 3600000L,
        duration = 8880000L,
        onBackClick = { },
        onPlayPause = { },
        onSeek = { },
        spatializationResult = SpatializationResult(
            canSpatialize = true,
            reason = "Content and device support spatial audio",
            spatialFormat = "DTS:X"
        ),
        isSpatialAudioEnabled = false,
        onShowMediaInfo = { },
        isLocked = false,
        onToggleLock = { },
        onShowAudioTrackSelection = { },
        onShowSubtitleTrackSelection = { },
        onCycleAspectRatio = { },
        onSeekBackward = { },
        onSeekForward = { }
    )
}

@Preview(
    name = "Seekbar",
    showBackground = true,
    widthDp = 400,
    heightDp = 50,
    backgroundColor = 0xFF000000
)
@Composable
fun SeekBarPreview() {
    SeekBar(
        progress = 0.35f,
        bufferedProgress = 0.60f,
        duration = 7200000L,
        chapterMarkers = listOf(
            ChapterMarker(positionMs = 900000L, label = "Intro"),
            ChapterMarker(positionMs = 2400000L, label = "Chapter 2"),
            ChapterMarker(positionMs = 4800000L, label = "Chapter 3"),
            ChapterMarker(positionMs = 6300000L, label = "Credits")
        ),
        onSeek = { },
        onScrubProgressChange = { },
        modifier = Modifier.padding(16.dp)
    )
}

private fun osdHdrLabel(format: String): String {
    val trimmedFormat = format.trim()
    return when {
        trimmedFormat.contains("dolby vision", ignoreCase = true) -> "DV"
        trimmedFormat.contains("hdr10+", ignoreCase = true) -> "HDR10+"
        trimmedFormat.contains("hdr10", ignoreCase = true) -> "HDR10"
        trimmedFormat.equals("hdr", ignoreCase = true) -> "HDR"
        else -> ""
    }
}

private fun osdDescription(label: String): String {
    return if (label == "DV") "Dolby Vision" else label
}
