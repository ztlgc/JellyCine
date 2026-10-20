package com.jellycine.app.ui.screens.player

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import com.jellycine.player.core.AudioTrackInfo
import com.jellycine.player.core.PlaybackOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
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
    audioNormalizationEnabled: Boolean = false,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    onAudioGainChange: (Int) -> Unit = {},
    onAudioNormalizationChange: (Boolean) -> Unit = {},
    onToggleOrientation: () -> Unit = {},
    videoFormatLabel: String = "",
    aspectRatioMode: String = "Fit",
    audioTracks: List<AudioTrackInfo> = emptyList(),
    currentAudioTrack: AudioTrackInfo? = null,
    onSelectAudioTrack: ((String) -> Unit)? = null,
    initialSoundPanelVisible: Boolean = false
) {
    var scrubPreviewProgress by remember { mutableStateOf<Float?>(null) }
    var optionsMenu by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var soundPanel by remember { mutableStateOf(initialSoundPanelVisible) }
    var expandAudioTracks by remember { mutableStateOf(false) }
    val latestScrubCallback by rememberUpdatedState(onScrubStateChange)
    DisposableEffect(optionsMenu, speedDialog, soundPanel) {
        val open = optionsMenu || speedDialog || soundPanel
        if (open) latestScrubCallback(true)
        onDispose { if (open) latestScrubCallback(false) }
    }
    LaunchedEffect(isLocked) {
        if (isLocked) { optionsMenu = false; speedDialog = false; soundPanel = false }
    }
    val displayedPosition = scrubPreviewProgress?.let { (duration * it).toLong() } ?: currentPosition
    val speedLabel = String.format(Locale.ROOT, if (playbackSpeed % 1f == 0f) "%.1f×" else "%s×", playbackSpeed)
    val qualityLabel = if (currentStreamingQuality.isBlank() || currentStreamingQuality.equals("Original", true))
        stringResource(R.string.player_quality_original_short) else currentStreamingQuality
    val gainLabel = if (audioGainPercent == 0) stringResource(R.string.player_gain_off_short) else "+$audioGainPercent%"
    val openSound: (Boolean) -> Unit = { expandAudioTracks = it; soundPanel = true }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val portrait = maxHeight > maxWidth
        val compact = maxHeight < 360.dp || maxWidth < 400.dp
        val edge = if (compact) 12.dp else 22.dp
        if (!isLocked) {
            Box(Modifier.fillMaxWidth().height(if (portrait) 144.dp else 104.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.68f), Color.Transparent))))
            if (!soundPanel) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (portrait) 200.dp else 148.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f)))))
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding()
                .padding(horizontal = edge, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.player_control_back), tint = Color.White) }
                Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                    Text(title, color = Color.White, fontSize = if (compact) 16.sp else 18.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val detail = listOfNotNull(seasonEpisodeLabel?.takeIf(String::isNotBlank), videoFormatLabel.takeIf(String::isNotBlank),
                        if (isHdrEnabled) osdHdrLabel(hdrFormat) else null).joinToString(" · ")
                    if (detail.isNotBlank()) Text(detail, color = Color(0xFFBDC8D5), fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                IconButton(onClick = onToggleOrientation) { Icon(Icons.Outlined.ScreenRotation,
                    stringResource(R.string.player_control_rotate), tint = Color.White, modifier = Modifier.size(22.dp)) }
                TextButton(onClick = onCycleAspectRatio, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Icon(Icons.Outlined.AspectRatio, stringResource(R.string.player_control_aspect), tint = Color.White,
                        modifier = Modifier.size(21.dp))
                    if (!portrait) {
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(when (aspectRatioMode) {
                            "Fill" -> R.string.player_aspect_fill_short
                            "Zoom" -> R.string.player_aspect_zoom_short
                            else -> R.string.player_aspect_fit_short
                        }), color = Color.White, fontSize = 12.sp)
                    }
                }
                Box {
                    IconButton(onClick = { optionsMenu = true }) { Icon(Icons.Outlined.MoreHoriz,
                        stringResource(R.string.player_control_more), tint = Color.White) }
                    DropdownMenu(expanded = optionsMenu, onDismissRequest = { optionsMenu = false }) {
                        if (portrait && canPlayPreviousEpisode) DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_previous_episode)) },
                            onClick = { optionsMenu = false; onPlayPreviousEpisode() })
                        if (portrait && canPlayNextEpisode) DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_next_episode)) },
                            onClick = { optionsMenu = false; onPlayNextEpisode() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.player_speed_title)) },
                            onClick = { optionsMenu = false; speedDialog = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.player_sound_title)) },
                            onClick = { optionsMenu = false; openSound(false) })
                        if (showPlaybackSettingsButton) DropdownMenuItem(text = { Text(stringResource(R.string.player_control_settings)) },
                            onClick = { optionsMenu = false; onShowPlaybackSettings() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.player_control_info)) },
                            onClick = { optionsMenu = false; onShowMediaInfo() })
                    }
                }
            }
        }
        if (!soundPanel) PlayerRoundButton(if (isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
            stringResource(if (isLocked) R.string.player_control_unlock else R.string.player_control_lock), onToggleLock,
            Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(start = edge))
        if (!isLocked && !soundPanel) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 30.dp)) {
                if (canPlayPreviousEpisode && !portrait) PlayerRoundButton(Icons.Filled.SkipPrevious,
                    stringResource(R.string.player_previous_episode), onPlayPreviousEpisode)
                PlayerRoundButton(replayIcon(seekBackwardSeconds), stringResource(R.string.player_control_rewind, seekBackwardSeconds),
                    onSeekBackward, size = if (compact) 52.dp else 64.dp)
                PlayerRoundButton(if (isPlaying) Icons.Filled.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (isPlaying) R.string.player_control_pause else R.string.player_control_play), onPlayPause,
                    size = if (compact) 72.dp else 84.dp)
                PlayerRoundButton(replayforwardIcon(seekForwardSeconds), stringResource(R.string.player_control_forward, seekForwardSeconds),
                    onSeekForward, size = if (compact) 52.dp else 64.dp)
                if (canPlayNextEpisode && !portrait) PlayerRoundButton(Icons.Filled.SkipNext,
                    stringResource(R.string.player_next_episode), onPlayNextEpisode)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding()
                .padding(horizontal = edge, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(formatTime(displayedPosition), color = Color.White, fontSize = 12.sp)
                    SeekBar(progress = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        bufferedProgress = if (duration > 0) (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        duration = duration, chapterMarkers = chapterMarkers, onSeek = onSeek,
                        onScrubProgressChange = { scrubPreviewProgress = it; onScrubStateChange(it != null) }, modifier = Modifier.weight(1f))
                    Text(formatTime(duration.coerceAtLeast(0)), color = Color.White, fontSize = 12.sp)
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val toolbarMinWidth = maxWidth
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).widthIn(min = toolbarMinWidth), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (portrait) Arrangement.spacedBy(6.dp) else Arrangement.SpaceEvenly) {
                        Surface(color = Color.Black.copy(alpha = 0.24f), shape = RoundedCornerShape(7.dp),
                            border = BorderStroke(1.dp, Color(0xFF394652))) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onPlaybackSpeedChange(PlaybackOptions.stepSpeed(playbackSpeed, -1)) },
                                    enabled = playbackSpeed > 0.5f, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Filled.Remove, stringResource(R.string.player_speed_slower),
                                        tint = Color.White.copy(alpha = if (playbackSpeed > 0.5f) 1f else 0.35f), modifier = Modifier.size(20.dp)) }
                                TextButton(onClick = { speedDialog = true }, contentPadding = PaddingValues(horizontal = 2.dp)) {
                                    Text(speedLabel, color = Color.White, fontSize = 13.sp) }
                                IconButton(onClick = { onPlaybackSpeedChange(PlaybackOptions.stepSpeed(playbackSpeed, 1)) },
                                    enabled = playbackSpeed < 2f, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Filled.Add, stringResource(R.string.player_speed_faster),
                                        tint = Color.White.copy(alpha = if (playbackSpeed < 2f) 1f else 0.35f), modifier = Modifier.size(20.dp)) }
                            }
                        }
                        if (!portrait) PlayerToolbarDivider()
                        PlayerToolbarAction(Icons.Outlined.Image, qualityLabel, onShowPlaybackSettings,
                            enabled = showPlaybackSettingsButton)
                        if (!portrait) PlayerToolbarDivider()
                        PlayerToolbarAction(Icons.Outlined.Audiotrack, stringResource(R.string.player_audio_short), { openSound(true) })
                        if (!portrait) PlayerToolbarDivider()
                        PlayerToolbarAction(Icons.Outlined.Subtitles, stringResource(R.string.player_subtitles_short), onShowSubtitleTrackSelection)
                        if (!portrait) PlayerToolbarDivider()
                        PlayerToolbarAction(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.player_gain_short, gainLabel), { openSound(false) })
                    }
                }
            }
        }
    }
    if (speedDialog) AlertDialog(onDismissRequest = { speedDialog = false },
        containerColor = Color(0xFF151C26), titleContentColor = Color.White,
        title = { Text(stringResource(R.string.player_speed_title)) },
        text = { Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            PlaybackOptions.speeds.forEach { speed ->
                TextButton(onClick = { onPlaybackSpeedChange(speed); speedDialog = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("${if (speed == playbackSpeed) "✓  " else ""}$speed×", color = if (speed == playbackSpeed) PlayerAccent else Color.White)
                }
            }
        } }, confirmButton = { TextButton(onClick = { speedDialog = false }) { Text(stringResource(R.string.player_control_close)) } })
    if (soundPanel) PlayerSoundPanel(audioGainPercent = audioGainPercent, audioGainAvailable = audioGainAvailable,
        normalizationEnabled = audioNormalizationEnabled, audioTracks = audioTracks, currentAudioTrack = currentAudioTrack,
        initiallyExpandTracks = expandAudioTracks, onGainChange = onAudioGainChange, onNormalizationChange = onAudioNormalizationChange,
        onTrackSelected = onSelectAudioTrack, onOpenTrackDialog = { soundPanel = false; onShowAudioTrackSelection() },
        onDismiss = { soundPanel = false })
}

internal val PlayerAccent = Color(0xFF9FC9FB)

@Composable
private fun PlayerToolbarDivider() {
    VerticalDivider(Modifier.height(22.dp), color = Color(0xFF394652))
}

@Composable
private fun PlayerToolbarAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Icon(icon, null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.55f), modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.55f), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun PlayerRoundButton(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 48.dp) {
    IconButton(onClick = onClick, modifier = modifier.size(size).background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(50))
        .then(if (size > 70.dp) Modifier.border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(50)) else Modifier)) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (size > 70.dp) 38.dp else 28.dp))
    }
}

private fun replayIcon(seconds: Int): ImageVector = when (seconds) {
    5 -> Icons.Filled.Replay5
    10 -> Icons.Filled.Replay10
    30 -> Icons.Filled.Replay30
    else -> Icons.Filled.Replay
}
private fun replayforwardIcon(seconds: Int): ImageVector = when (seconds) {
    5 -> Icons.Filled.Forward5
    10 -> Icons.Filled.Forward10
    30 -> Icons.Filled.Forward30
    else -> Icons.Filled.FastForward
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
        targetValue = if (dragActive) 0.30f else 0.17f,
        label = "seekTrackHeight"
    )
    val thumbRadiusFraction by animateFloatAsState(
        targetValue = if (dragActive) 0.70f else 0.58f,
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
            .height(40.dp)
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
                .align(Alignment.Center)
        ) {
            val yOffset = size.height / 2
            val trackInset = 2.dp.toPx()
            val trackStart = Offset(trackInset, yOffset)
            val trackEnd = Offset(size.width - trackInset, yOffset)
            val trackHeight = size.height * trackHeightFraction
            val markerSpacingPx = 6.dp.toPx()
            val markerStrokeWidth = 1.5.dp.toPx()
            val markerHalfHeight = maxOf(trackHeight / 2f, 3.dp.toPx())
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
                    color = PlayerAccent,
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
                        start = Offset(markerX, yOffset - markerHalfHeight),
                        end = Offset(markerX, yOffset + markerHalfHeight),
                        strokeWidth = markerStrokeWidth,
                        cap = StrokeCap.Round
                    )
                    lastMarkerX = markerX
                }
            }

            progressX?.let { thumbX ->
                drawCircle(
                    color = PlayerAccent,
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
