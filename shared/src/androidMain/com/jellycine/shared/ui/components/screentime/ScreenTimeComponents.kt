package com.jellycine.shared.ui.components.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.shared.R
import java.time.LocalDate

private val CardBackground = Color(0xFF1A1A1A)
private val CardBorder = Color.White.copy(alpha = 0.08f)
private val PositiveColor = Color(0xFF4ADE80)
private val NegativeColor = Color(0xFFFF6B6B)
private val AccentBlue = Color(0xFF67E8F9)
private val SubtextColor = Color.White.copy(alpha = 0.5f)

@Composable
fun YearNavigator(
    year: Int,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canGoNext = year < LocalDate.now().year

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousYear) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.screen_time_previous_year),
                tint = Color.White
            )
        }
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        IconButton(onClick = onNextYear, enabled = canGoNext) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.screen_time_next_year),
                tint = if (canGoNext) Color.White else Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    delta: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AccentBlue
        )
        BasicText(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = MaterialTheme.typography.headlineMedium.fontSize)
        )
        if (delta != null) {
            DeltaIndicator(delta = delta)
        }
    }
}


@Composable
fun DeltaIndicator(delta: String) {
    val noChangeLabel = stringResource(R.string.screen_time_no_change)
    val isNoChange = delta == NO_CHANGE_SENTINEL
    val isPositive = delta.startsWith("+")
    val color = when {
        isNoChange -> SubtextColor
        isPositive -> PositiveColor
        else -> NegativeColor
    }
    Text(
        text = if (isNoChange) noChangeLabel else delta,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

fun formatWatchTime(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1000
    val hours = seconds / 3600
    val mins = seconds / 60 % 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        seconds > 0 -> "${secs}s"
        durationMs > 0 -> "<1s"
        else -> "0s"
    }
}

private const val NO_CHANGE_SENTINEL = "__no_change__"

fun formatDelta(deltaMs: Long): String {
    if (deltaMs == 0L) return NO_CHANGE_SENTINEL
    val prefix = if (deltaMs > 0) "+" else "−"
    return "$prefix${formatWatchTime(kotlin.math.abs(deltaMs))}"
}
