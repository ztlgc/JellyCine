package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.shared.R
import java.util.Locale
import kotlin.math.roundToInt

private val RatingAccent = Color(0xFF9FC9FB)
private val RatingSecondaryText = Color(0xFFADB9C9)
private val RatingBorder = Color(0xFF293340)
private val RatingTrack = Color(0xFF505D6F)
private val RatingSurface = Color(0xFF101720)

@Composable
internal fun VideoRatingDialogContent(
    currentRating: Float?,
    selectedScoreTenths: Int?,
    lowScoresExpanded: Boolean,
    enabled: Boolean,
    loading: Boolean,
    saving: Boolean,
    errorMessage: String?,
    onScoreSelected: (Int) -> Unit,
    onLowScoresToggle: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val displayedScore = selectedScoreTenths ?: VideoRatingScale.fromServer(currentRating)
    val scoreText = displayedScore?.let(::formatRatingScore)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = RatingSurface,
        contentColor = Color(0xFFF1F4F8),
        border = BorderStroke(1.dp, RatingBorder)
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, tint = RatingAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.video_rating_title), Modifier.weight(1f),
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(12.dp))
                if (scoreText != null) {
                    Text(scoreText, color = RatingAccent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.video_rating_score_unit),
                        color = RatingAccent, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                } else {
                    Text(stringResource(R.string.video_rating_unrated), color = RatingSecondaryText, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    color = RatingAccent, trackColor = RatingBorder)
            }
            Text(stringResource(R.string.video_rating_high_scores), color = RatingSecondaryText, fontSize = 13.sp)
            RatingScoreSlider(VideoRatingScale.highScores, selectedScoreTenths, enabled,
                stringResource(R.string.video_rating_high_scores), onScoreSelected)
            HorizontalDivider(color = RatingBorder)
            TextButton(
                onClick = onLowScoresToggle,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = RatingSecondaryText)
            ) {
                Text(stringResource(R.string.video_rating_other_scores), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(stringResource(if (lowScoresExpanded) R.string.video_rating_collapse else R.string.video_rating_expand), fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Icon(if (lowScoresExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null, modifier = Modifier.size(16.dp))
            }
            if (lowScoresExpanded) {
                RatingScoreSlider(VideoRatingScale.lowScores, selectedScoreTenths, enabled,
                    stringResource(R.string.video_rating_other_scores), onScoreSelected)
            }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                if (onRetry != null) {
                    TextButton(onClick = onRetry, enabled = !loading && !saving,
                        colors = ButtonDefaults.textButtonColors(contentColor = RatingAccent)) {
                        Text(stringResource(R.string.video_rating_retry))
                    }
                }
            }
            HorizontalDivider(color = RatingBorder)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, enabled = !saving,
                    colors = ButtonDefaults.textButtonColors(contentColor = RatingSecondaryText)) {
                    Text(stringResource(R.string.video_rating_cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = enabled && selectedScoreTenths != null &&
                        selectedScoreTenths != VideoRatingScale.fromServer(currentRating),
                    modifier = Modifier.widthIn(min = 106.dp).heightIn(min = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RatingAccent,
                        contentColor = Color(0xFF08243A), disabledContainerColor = Color(0xFF243245),
                        disabledContentColor = Color(0xFF95A5BA))
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = RatingAccent, strokeWidth = 2.dp)
                    else Text(if (selectedScoreTenths == null) stringResource(R.string.video_rating_save)
                        else stringResource(R.string.video_rating_save_score, formatRatingScore(selectedScoreTenths)),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingScoreSlider(
    range: IntRange,
    selectedScoreTenths: Int?,
    enabled: Boolean,
    description: String,
    onScoreSelected: (Int) -> Unit
) {
    val selected = selectedScoreTenths != null && selectedScoreTenths in range
    val value = (selectedScoreTenths ?: range.first).coerceIn(range) / 10f
    var gestureValue by remember(value) { mutableFloatStateOf(value) }
    val selectionDescription = if (selected) stringResource(R.string.video_rating_score_description, formatRatingScore(selectedScoreTenths!!))
        else stringResource(R.string.video_rating_not_selected)
    val colors = SliderDefaults.colors(
        activeTrackColor = if (selected) RatingAccent else RatingTrack,
        inactiveTrackColor = RatingTrack,
        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
        disabledActiveTrackColor = if (selected) RatingAccent.copy(alpha = 0.4f) else RatingTrack,
        disabledInactiveTrackColor = RatingTrack.copy(alpha = 0.6f),
        disabledActiveTickColor = Color.Transparent, disabledInactiveTickColor = Color.Transparent
    )
    Box(Modifier.fillMaxWidth().height(72.dp)) {
        Slider(
            value = value,
            onValueChange = {
                gestureValue = it
                onScoreSelected((it * 10).roundToInt().coerceIn(range))
            },
            // An inactive range can already be positioned at its boundary; a tap still selects it.
            onValueChangeFinished = { onScoreSelected((gestureValue * 10).roundToInt().coerceIn(range)) },
            valueRange = range.first / 10f..range.last / 10f,
            steps = range.last - range.first - 1,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(56.dp).semantics {
                contentDescription = description
                stateDescription = selectionDescription
            },
            colors = colors,
            thumb = {
                Box(Modifier.size(28.dp).then(if (selected) Modifier
                    .background(if (enabled) RatingAccent else RatingAccent.copy(alpha = 0.4f), CircleShape)
                    .border(1.5.dp, RatingSurface, CircleShape) else Modifier))
            },
            track = { state ->
                SliderDefaults.Track(sliderState = state, colors = colors, enabled = enabled,
                    modifier = Modifier.height(12.dp), thumbTrackGapSize = 0.dp, drawStopIndicator = null)
            }
        )
        val ticks = if (range == VideoRatingScale.highScores) listOf(60, 70, 80, 90, 100)
            else listOf(10, 20, 30, 40, 50, 59)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 46.dp, start = 14.dp, end = 14.dp)) {
            ticks.forEach { tick ->
                val fraction = (tick - range.first).toFloat() / (range.last - range.first)
                Column(Modifier.offset(x = maxWidth * fraction - 16.dp).width(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(1.dp).height(5.dp).background(RatingSecondaryText))
                    Spacer(Modifier.height(3.dp))
                    Text(if (tick % 10 == 0) (tick / 10).toString() else formatRatingScore(tick),
                        color = RatingSecondaryText, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun formatRatingScore(tenths: Int): String =
    String.format(Locale.getDefault(), "%.1f", VideoRatingScale.toServer(tenths))
