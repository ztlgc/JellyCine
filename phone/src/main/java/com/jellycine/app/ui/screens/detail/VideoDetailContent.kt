package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val VideoPageBackground = Color(0xFF111216)
private val VideoAccent = Color(0xFF9FC9FB)

@Composable
internal fun VideoTrackChip(
    selected: String,
    options: List<String>,
    icon: ImageVector,
    onSelected: (String) -> Unit,
    highlighted: Boolean = false
) {
    if (options.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Box {
        Surface(
            shape = shape,
            color = if (highlighted) Color(0xFF28313D) else Color.Transparent,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier.widthIn(max = 300.dp).heightIn(min = 36.dp).clip(shape).clickable(enabled = options.size > 1) { expanded = true }
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color(0xFFD0DCEC), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(selected, color = Color(0xFFD0DCEC), fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
                if (options.size > 1) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = VideoAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 340.dp)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { expanded = false; onSelected(option) },
                    trailingIcon = { if (option == selected) Icon(Icons.Rounded.Check, null, tint = VideoAccent) }
                )
            }
        }
    }
}

/** File videos use the same playback and user actions as the main detail screen. */
@Composable
internal fun VideoDetailContent(
    item: BaseItemDto,
    imageUrl: String?,
    streams: List<MediaStream>,
    isFavorite: Boolean,
    isWatched: Boolean,
    playButtonText: String,
    resumeProgress: Float,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onWatched: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    favoriteSaving: Boolean,
    onPlay: () -> Unit,
    onImageError: (Boolean) -> Unit,
    rating: @Composable () -> Unit,
    tracks: @Composable () -> Unit,
    downloadAction: @Composable () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val source = item.activeDetailMediaSources().firstOrNull()
    val path = source?.path?.takeIf(String::isNotBlank) ?: item.path
    val pageWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val heroHeight = (pageWidth * 1.28f).coerceIn(340f, 520f).dp
    Box(Modifier.fillMaxSize().background(VideoPageBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(heroHeight)) {
                        JellyfinPosterImage(
                            imageUrl = imageUrl,
                            contentDescription = null,
                            context = context,
                            modifier = Modifier.fillMaxSize(),
                            onErrorStateChange = onImageError
                        )
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.22f),
                            0.45f to Color.Black.copy(alpha = 0.22f),
                            0.75f to VideoPageBackground.copy(alpha = 0.78f),
                            1f to VideoPageBackground
                        )))
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = heroHeight * 0.60f)) {
                        Text(
                            text = item.name.orEmpty().ifBlank { stringResource(R.string.detail_video_fallback) },
                            color = Color.White,
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val metadata = listOfNotNull(
                            item.productionYear?.toString(),
                            (item.runTimeTicks ?: source?.runTimeTicks)?.takeIf { it > 0 }?.let(CodecUtils::formatRuntime)
                        ).joinToString("   ")
                        if (metadata.isNotBlank()) {
                            Text(metadata, color = Color(0xFFD6D8DE), fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        rating()
                        Spacer(Modifier.height(8.dp))
                        tracks()
                        Row(
                            Modifier.padding(top = 10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onPlay,
                                enabled = !item.id.isNullOrBlank() && item.playAccess != "None",
                                modifier = Modifier.widthIn(min = 128.dp).heightIn(min = 40.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VideoAccent, contentColor = Color(0xFF0A3454))
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(playButtonText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            downloadAction()
                        }
                        if (resumeProgress > 0f && resumeProgress < 1f) {
                            LinearProgressIndicator(
                                progress = { resumeProgress },
                                modifier = Modifier.width(150.dp).padding(top = 8.dp),
                                color = VideoAccent,
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )
                        }
                    }
                }
            }
            if (!item.overview.isNullOrBlank()) {
                item(key = "overview") {
                    Text(item.overview.orEmpty(), color = Color(0xFFD6D8DE), modifier = Modifier.padding(24.dp), lineHeight = 24.sp)
                }
            }
            item(key = "file") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)) {
                    Text(stringResource(R.string.video_detail_media_info), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (!path.isNullOrBlank()) {
                        Text(path, color = Color(0xFFE0E2E8), fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 20.dp))
                    }
                    val fileMetadata = listOfNotNull(
                        (source?.container ?: item.container)?.uppercase(Locale.ROOT),
                        source?.size?.takeIf { it >= 0 }?.let(::videoFileSize),
                        videoAddedDate(item.dateCreated)?.let { stringResource(R.string.video_detail_added, it) }
                    ).joinToString("   ")
                    if (fileMetadata.isNotBlank()) {
                        Text(fileMetadata, color = Color(0xFFD6D8DE), fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (fileMetadata.isBlank() && path.isNullOrBlank() && streams.isEmpty()) {
                        Text(stringResource(R.string.video_detail_no_info), color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
            if (streams.isNotEmpty()) {
                item(key = "streams") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(streams) { stream -> VideoStreamCard(stream) }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.48f), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back_button), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onWatched) {
                Icon(if (isWatched) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
                    stringResource(if (isWatched) R.string.unwatched else R.string.watched), tint = if (isWatched) VideoAccent else Color.White)
            }
            IconButton(onClick = onFavorite, enabled = !favoriteSaving) {
                Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(if (isFavorite) R.string.unfavorite else R.string.favorite), tint = if (isFavorite) VideoAccent else Color.White)
            }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.video_detail_delete),
                    tint = if (canDelete) Color.White else Color.White.copy(alpha = 0.35f))
            }
        }
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
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: return value
    return DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault()).format(instant)
}

@Composable
private fun VideoStreamCard(stream: MediaStream) {
    val rows = buildList<Pair<Int, String>> {
        fun addValue(label: Int, value: Any?) {
            value?.toString()?.takeIf { it.isNotBlank() }?.let { add(label to it) }
        }
        addValue(R.string.video_detail_type, stream.type)
        addValue(R.string.video_detail_index, stream.index)
        addValue(R.string.video_detail_title, stream.displayTitle)
        addValue(R.string.video_detail_language, stream.language)
        addValue(R.string.video_detail_codec, stream.codec)
        if (stream.width != null && stream.height != null) addValue(R.string.video_detail_resolution, "${stream.width} x ${stream.height}")
        addValue(R.string.video_detail_frame_rate, stream.averageFrameRate ?: stream.realFrameRate)
        addValue(R.string.video_detail_range, stream.videoRangeType ?: stream.videoRange)
        addValue(R.string.video_detail_profile, stream.profile)
        addValue(R.string.video_detail_bitrate, stream.bitRate?.let { "${it / 1000} kbps" })
        addValue(R.string.video_detail_bit_depth, stream.bitDepth)
        addValue(R.string.video_detail_color_space, stream.colorSpace)
        addValue(R.string.video_detail_pixel_format, stream.pixelFormat)
        addValue(R.string.video_detail_channels, stream.channels)
        addValue(R.string.video_detail_sample_rate, stream.sampleRate?.let { "$it Hz" })
        addValue(R.string.video_detail_default, stream.isDefault)
        addValue(R.string.video_detail_forced, stream.isForced)
        addValue(R.string.video_detail_external, stream.isExternal)
    }
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF25272D), modifier = Modifier.width(200.dp)) {
        Column(Modifier.padding(12.dp)) {
            Icon(
                when {
                    stream.type.equals("Audio", true) -> Icons.Rounded.MusicNote
                    stream.type.equals("Subtitle", true) -> Icons.Rounded.Subtitles
                    else -> Icons.Rounded.Movie
                }, null, tint = Color(0xFFCAC9D3), modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(6.dp))
            rows.forEach { (label, value) ->
                Text("${stringResource(label)}: $value", color = Color(0xFFCAC9D3), fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}
