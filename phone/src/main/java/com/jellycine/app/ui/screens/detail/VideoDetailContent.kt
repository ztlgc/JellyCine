package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.ui.components.common.containerHeightDp
import com.jellycine.app.ui.components.common.containerWidthDp
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaStream
import com.jellycine.detail.CodecUtils
import com.jellycine.shared.R
import com.jellycine.shared.ui.components.common.activeDetailMediaSources
import com.jellycine.shared.util.image.JellyfinPosterImage
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VideoPageBackground = Color(0xFF0B0F15)
private val VideoAccent = Color(0xFF9FC9FB)
private val VideoSecondaryText = Color(0xFFADB9C9)

/** The approved compact phone layout. Playback selections live in the player. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoDetailContent(
    item: BaseItemDto,
    imageUrl: String?,
    streams: List<MediaStream>,
    isFavorite: Boolean,
    isWatched: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onWatched: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    favoriteSaving: Boolean,
    onPlay: () -> Unit,
    onImageError: (Boolean) -> Unit,
    rating: @Composable () -> Unit,
    moreActions: @Composable () -> Unit,
    backdrop: (@Composable () -> Unit)? = null
) {
    val source = item.activeDetailMediaSources().firstOrNull()
    val path = source?.path?.takeIf(String::isNotBlank) ?: item.path
    val video = streams.firstOrNull { it.type.equals("Video", true) }
    val audio = streams.firstOrNull { it.type.equals("Audio", true) }
    val audioLabel = audio?.let { stream ->
        when (stream.channels) {
            1, 2 -> listOfNotNull(stream.codec?.uppercase(Locale.ROOT),
                stringResource(if (stream.channels == 1) R.string.video_detail_mono else R.string.video_detail_stereo)).joinToString(" · ")
            else -> CodecUtils.getAudioInfo(stream)
        }.takeIf(String::isNotBlank)
    }
    val runtime = (item.runTimeTicks ?: source?.runTimeTicks ?: 0L).coerceAtLeast(0L)
    val position = (item.userData?.playbackPositionTicks ?: 0L).coerceIn(0L, runtime)
    val canResume = position > 0L && position < runtime
    val pageWidth = containerWidthDp()
    val pageHeight = containerHeightDp()
    val heroHeight = if (pageWidth > pageHeight) (pageHeight * 0.66f).coerceIn(180.dp, 280.dp)
        else (pageWidth * 0.76f).coerceIn(240.dp, 340.dp)
    val codec = video?.codec?.uppercase(Locale.ROOT)
    val fps = (video?.averageFrameRate ?: video?.realFrameRate)?.takeIf { it > 0 && it.isFinite() }
        ?.let { String.format(Locale.ROOT, if (it % 1f < 0.01f) "%.0f fps" else "%.2f fps", it) }
    val badges = listOfNotNull(video?.let(CodecUtils::getVideoQualityBadge), codec, fps)
    Box(Modifier.fillMaxSize().background(VideoPageBackground)) {
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(heroHeight)) {
                        if (backdrop != null) backdrop() else JellyfinPosterImage(
                            imageUrl = imageUrl, contentDescription = null,
                            context = LocalContext.current, modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onErrorStateChange = onImageError
                        )
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                            0f to VideoPageBackground,
                            0.06f to VideoPageBackground,
                            0.28f to Color.Transparent,
                            0.66f to Color.Transparent,
                            0.93f to VideoPageBackground,
                            1f to VideoPageBackground
                        )))
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = heroHeight - 32.dp)) {
                        Text(item.name.orEmpty().ifBlank { stringResource(R.string.detail_video_fallback) },
                            color = Color.White, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Clip)
                        val metadata = listOfNotNull(item.productionYear?.toString(),
                            runtime.takeIf { it > 0 }?.let { videoDetailTime(it) }).joinToString("  ·  ")
                        if (metadata.isNotBlank()) Text(metadata, color = VideoSecondaryText, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 5.dp))
                        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            rating()
                            badges.forEach { VideoInfoBadge(it) }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = onPlay, enabled = !item.id.isNullOrBlank() && item.playAccess != "None",
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VideoAccent, contentColor = Color(0xFF08243A))) {
                                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (canResume) stringResource(R.string.video_detail_resume_at,
                                    videoDetailTime(position)) else stringResource(R.string.player_control_play),
                                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            moreActions()
                        }
                        if (canResume) {
                            LinearProgressIndicator(progress = { (if (runtime > 0L) position.toFloat() / runtime else 0f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(3.dp),
                                color = VideoAccent, trackColor = Color.White.copy(alpha = 0.16f), gapSize = 0.dp,
                                drawStopIndicator = {})
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.video_detail_played,
                                    videoDetailTime(position)), color = VideoSecondaryText, fontSize = 11.sp)
                                Text(stringResource(R.string.video_detail_remaining,
                                    videoDetailTime(runtime - position)), color = VideoSecondaryText, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            if (!item.overview.isNullOrBlank()) item(key = "overview") {
                Column(Modifier.padding(horizontal = 18.dp).padding(top = 24.dp)) {
                    Text(stringResource(R.string.video_detail_overview), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.overview.orEmpty(), color = VideoSecondaryText, fontSize = 13.sp, lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
            item(key = "file") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 20.dp)) {
                    HorizontalDivider(color = Color(0xFF202A36))
                    Text(stringResource(R.string.video_detail_media_info), color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
                    val fileName = path?.replace('\\', '/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = Color(0xFFD9E1EC), modifier = Modifier.size(22.dp))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            if (fileName != null) Text(fileName, color = Color(0xFFE0E6EE), fontSize = 13.sp,
                                lineHeight = 20.sp)
                            val fileMeta = listOfNotNull((source?.container ?: item.container)?.uppercase(Locale.ROOT),
                                source?.size?.takeIf { it >= 0 }?.let(::videoFileSize)).joinToString(" · ")
                            if (fileMeta.isNotBlank()) Text(fileMeta, color = VideoSecondaryText, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp))
                            val mediaMeta = listOfNotNull(
                                if (video?.width != null && video.height != null) "${video.width} × ${video.height}" else null,
                                listOfNotNull(codec, fps).takeIf { it.isNotEmpty() }?.joinToString(" · "),
                                audioLabel)
                            if (mediaMeta.isNotEmpty()) Text(mediaMeta.joinToString("   |   "), color = VideoSecondaryText,
                                fontSize = 11.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                            if (!path.isNullOrBlank()) Text(path, color = Color(0xFF7F90A5), fontSize = 11.sp,
                                lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                            if (path.isNullOrBlank() && fileMeta.isBlank() && mediaMeta.isEmpty())
                                Text(stringResource(R.string.video_detail_no_info), color = VideoSecondaryText, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
            .statusBarsPadding().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back_button), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onWatched) { Icon(if (isWatched) Icons.Outlined.CheckCircle else Icons.Outlined.CheckCircleOutline,
                stringResource(if (isWatched) R.string.unwatched else R.string.watched), tint = if (isWatched) VideoAccent else Color.White) }
            IconButton(onClick = onFavorite, enabled = !favoriteSaving) { Icon(if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                stringResource(if (isFavorite) R.string.unfavorite else R.string.favorite), tint = if (isFavorite) VideoAccent else Color.White) }
            IconButton(onClick = onDelete, enabled = canDelete) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.video_detail_delete),
                tint = if (canDelete) Color.White else Color.White.copy(alpha = 0.35f)) }
        }
    }
}

private fun videoDetailTime(ticks: Long): String = CodecUtils.formatRuntime(ticks, includeSeconds = true).let {
    if (it.count { character -> character == ':' } == 1) it.padStart(5, '0') else it
}

@Composable
private fun VideoInfoBadge(value: String) {
    Surface(shape = RoundedCornerShape(5.dp), color = Color(0xFF101720), border = BorderStroke(1.dp, Color(0xFF293340))) {
        Text(value, color = Color(0xFFD6DFEA), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

internal fun videoFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.2f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.getDefault(), "%.2f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

internal fun videoAddedDate(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val instant = runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull() ?: return value
    return DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault()).format(instant)
}
