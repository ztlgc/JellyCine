package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.ui.components.common.DownloadActionMenu
import com.jellycine.app.ui.components.common.DownloadContent
import com.jellycine.app.ui.components.common.DownloadPanelState
import com.jellycine.app.ui.components.common.detailActionWidth
import com.jellycine.data.model.DownloadStatus
import com.jellycine.data.model.ItemDownloadState
import com.jellycine.shared.R
import com.jellycine.shared.ui.components.common.DetailDownloadActionButton
import com.jellycine.shared.ui.components.common.DetailDownloadActionState
import com.jellycine.shared.ui.components.common.DetailPlayActionButton
import com.jellycine.shared.ui.components.common.DetailTrailerButton
import com.jellycine.shared.ui.components.common.FavoriteActionButton

@Composable
private fun DetailActionRow(
    screenWidthDp: Dp,
    useTabletLayout: Boolean,
    topPadding: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth(
                    detailActionWidth(
                        screenWidthDp,
                        useTabletLayout = useTabletLayout,
                        availableWidth = maxWidth
                    )
                )
                .padding(top = topPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
internal fun ActionSection(
    screenWidthDp: Dp,
    useTabletLayout: Boolean,
    buttonHeight: Dp,
    playButtonText: String,
    isPartiallyWatched: Boolean,
    resumeProgress: Float,
    canDownloadItem: Boolean,
    itemDownloadState: ItemDownloadState,
    isPausedDownload: Boolean,
    hasActiveDownload: Boolean,
    downloadActionMenu: Boolean,
    downloadProgress: Float,
    isFavorite: Boolean,
    hasTrailer: Boolean,
    onPlayClick: () -> Unit,
    onTrailerClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDownloadMenuChange: (Boolean) -> Unit,
    onPauseResumeDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconOnlyDownload = !useTabletLayout
    // Shared progress buttons fill their height, so reserve scaled text space explicitly.
    val textButtonHeight = buttonHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)

    DetailActionRow(
        screenWidthDp = screenWidthDp,
        useTabletLayout = useTabletLayout,
        topPadding = 12.dp,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(textButtonHeight)
                .clip(RoundedCornerShape(24.dp))
        ) {
            DetailPlayActionButton(
                text = playButtonText,
                isPartiallyWatched = isPartiallyWatched,
                resumeProgress = resumeProgress,
                onClick = onPlayClick,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .then(
                    if (iconOnlyDownload) {
                        Modifier.size(buttonHeight)
                    } else {
                        Modifier
                            .weight(1f)
                            .height(textButtonHeight)
                    }
                )
        ) {
            val downloadActionState = when {
                !canDownloadItem -> DetailDownloadActionState.Unavailable
                itemDownloadState.status == DownloadStatus.COMPLETED ->
                    DetailDownloadActionState.Completed
                itemDownloadState.status == DownloadStatus.DOWNLOADING ->
                    DetailDownloadActionState.Downloading
                isPausedDownload -> DetailDownloadActionState.Paused
                itemDownloadState.status == DownloadStatus.QUEUED ->
                    DetailDownloadActionState.Queued
                else -> DetailDownloadActionState.Idle
            }

            DetailDownloadActionButton(
                state = downloadActionState,
                progress = downloadProgress,
                onClick = {
                    when {
                        !canDownloadItem -> Unit
                        hasActiveDownload -> onDownloadMenuChange(true)
                        else -> onDownloadClick()
                    }
                },
                modifier = Modifier.fillMaxSize(),
                iconOnly = iconOnlyDownload
            )

            DownloadActionMenu(
                expanded = downloadActionMenu,
                canResume = isPausedDownload,
                hasActiveDownloads = hasActiveDownload,
                onDismissRequest = { onDownloadMenuChange(false) },
                onPauseResume = onPauseResumeDownload,
                onCancel = onCancelDownload
            )
        }

        if (hasTrailer) {
            DetailTrailerButton(
                onClick = onTrailerClick,
                size = buttonHeight
            )
        }

        FavoriteActionButton(
            isFavorite = isFavorite,
            onClick = onFavoriteClick,
            size = buttonHeight
        )
    }
}

@Composable
internal fun SeriesActionSection(
    screenWidthDp: Dp,
    useTabletLayout: Boolean,
    buttonHeight: Dp,
    seriesDownload: DownloadPanelState,
    seriesQueueInProgress: Boolean,
    seriesDownloadProgress: Float,
    seriesDownloadActionMenu: Boolean,
    canResumeSeriesDownloads: Boolean,
    hasActiveSeriesDownloads: Boolean,
    isFavorite: Boolean,
    hasTrailer: Boolean,
    onTrailerClick: () -> Unit,
    onSeriesDownloadClick: () -> Unit,
    onSeriesDownloadMenuChange: (Boolean) -> Unit,
    onPauseResumeSeriesDownloads: () -> Unit,
    onCancelSeriesDownloads: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DetailActionRow(
        screenWidthDp = screenWidthDp,
        useTabletLayout = useTabletLayout,
        topPadding = 14.dp,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = buttonHeight)
        ) {
            OutlinedButton(
                onClick = {
                    if (hasActiveSeriesDownloads) {
                        onSeriesDownloadMenuChange(true)
                    } else {
                        onSeriesDownloadClick()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = buttonHeight),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.18f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF1F1F24),
                    contentColor = Color.White
                )
            ) {
                DownloadContent(
                    panelState = seriesDownload,
                    isQueueing = seriesQueueInProgress,
                    progress = seriesDownloadProgress,
                    idleLabelRes = R.string.downloads_action_download_series,
                    fontSize = 14.sp,
                    iconSize = 18.dp,
                    progressSize = 18.dp
                )
            }

            DownloadActionMenu(
                expanded = seriesDownloadActionMenu,
                canResume = canResumeSeriesDownloads,
                hasActiveDownloads = hasActiveSeriesDownloads,
                onDismissRequest = { onSeriesDownloadMenuChange(false) },
                onPauseResume = onPauseResumeSeriesDownloads,
                onCancel = onCancelSeriesDownloads
            )
        }

        if (hasTrailer) {
            DetailTrailerButton(
                onClick = onTrailerClick,
                size = buttonHeight
            )
        }

        FavoriteActionButton(
            isFavorite = isFavorite,
            onClick = onFavoriteClick,
            size = buttonHeight
        )
    }
}