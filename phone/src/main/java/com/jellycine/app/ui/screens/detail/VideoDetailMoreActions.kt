package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.shared.R
import com.jellycine.shared.ui.components.common.DetailDownloadActionState
import kotlinx.coroutines.launch

/** A menu that stays below its button, scrolling when the remaining space is short. */
@Composable
internal fun VideoDetailMoreActions(
    itemId: String?,
    downloadState: DetailDownloadActionState,
    downloadProgress: Float,
    playbackEnabled: Boolean,
    onDownload: () -> Unit,
    onPlayWithEngine: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val bottomInset = WindowInsets.safeDrawing.getBottom(density)
    val gap = with(density) { 6.dp.roundToPx() }
    val margin = with(density) { 8.dp.roundToPx() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var anchorBottom by remember(itemId) { mutableIntStateOf(0) }
    val availableHeight = with(density) {
        (windowHeight - bottomInset - anchorBottom - gap - margin).coerceAtLeast(1).toDp()
    }
    Box(Modifier.size(44.dp).bringIntoViewRequester(bringIntoViewRequester)
        .onGloballyPositioned { anchorBottom = it.boundsInWindow().bottom.toInt() }) {
        OutlinedButton(
            onClick = {
                if (expanded) onExpandedChange(false) else scope.launch {
                    // Reserve room below the button before opening in short/landscape windows.
                    bringIntoViewRequester.bringIntoView(with(density) { Rect(0f, 0f, 44.dp.toPx(), 238.dp.toPx()) })
                    onExpandedChange(true)
                }
            }, modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1F1F24), contentColor = Color.White)
        ) {
            Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.video_detail_more_actions), Modifier.size(22.dp))
        }
        if (expanded) {
            Popup(
                popupPositionProvider = remember(gap, margin) { VideoDetailMenuPositionProvider(gap, margin) },
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                VideoDetailMoreMenuContent(
                    downloadState = downloadState, downloadProgress = downloadProgress,
                    playbackEnabled = playbackEnabled,
                    onDownload = {
                        onExpandedChange(false)
                        onDownload()
                    },
                    onPlayWithEngine = { engine ->
                        onExpandedChange(false)
                        onPlayWithEngine(engine)
                    },
                    modifier = Modifier.heightIn(max = availableHeight)
                )
            }
        }
    }
}

@Composable
internal fun VideoDetailMoreMenuContent(
    downloadState: DetailDownloadActionState,
    downloadProgress: Float,
    playbackEnabled: Boolean,
    onDownload: () -> Unit,
    onPlayWithEngine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadStatus = when (downloadState) {
        DetailDownloadActionState.Downloading -> "${stringResource(R.string.downloads_status_downloading_short)} ${(downloadProgress.coerceIn(0f, 1f) * 100).toInt()}%"
        DetailDownloadActionState.Queued -> stringResource(R.string.downloads_status_queued)
        DetailDownloadActionState.Paused -> stringResource(R.string.downloads_status_paused)
        DetailDownloadActionState.Completed -> stringResource(R.string.downloads_status_downloaded)
        DetailDownloadActionState.Unavailable -> stringResource(R.string.settings_unavailable)
        DetailDownloadActionState.Idle -> null
    }
    Surface(
        modifier = modifier.widthIn(min = 248.dp, max = 300.dp), shape = RoundedCornerShape(12.dp),
        color = Color(0xFF141C27), contentColor = Color(0xFFE0E6EE), shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0xFF293340))
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.downloads_action_download), fontSize = 14.sp)
                        if (downloadStatus != null) Text(downloadStatus, fontSize = 11.sp, color = Color(0xFFADB9C9))
                    }
                },
                leadingIcon = { Icon(Icons.Outlined.Download, null) },
                enabled = downloadState != DetailDownloadActionState.Unavailable && downloadState != DetailDownloadActionState.Completed,
                onClick = onDownload
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.video_detail_play_mpv), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.PlayCircleOutline, null) },
                enabled = playbackEnabled,
                onClick = { onPlayWithEngine(PlayerPreferences.PLAYER_ENGINE_MPV) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.video_detail_play_exoplayer), fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.PlayCircleOutline, null) },
                enabled = playbackEnabled,
                onClick = { onPlayWithEngine(PlayerPreferences.PLAYER_ENGINE_EXO) }
            )
        }
    }
}

internal class VideoDetailMenuPositionProvider(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val preferredX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val minimumX = margin.coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val maximumX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(minimumX)
        return IntOffset(preferredX.coerceIn(minimumX, maximumX), anchorBounds.bottom + gap)
    }
}
