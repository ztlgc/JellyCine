package com.jellycine.shared.ui.components.screentime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.DailyWatchTime
import com.jellycine.data.model.PeakHourBucket
import com.jellycine.shared.R
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val BarColor = Color(0xFF22D3EE)
private val BarColorDark = Color(0xFF0891B2)
private val SubtextColor = Color.White.copy(alpha = 0.68f)

private data class ChartBucket(
    val label: String,
    val watchTimeMs: Long,
    val compactLabel: String = label
)

@Composable
fun DailyBreakdownChart(
    dailyData: List<DailyWatchTime>,
    selectedIndex: Int? = null,
    onBucketClick: ((bucketIndex: Int) -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val buckets = aggregateBuckets(dailyData)
    val maxWatchTimeMs = buckets.maxOfOrNull { it.watchTimeMs } ?: 1L

    val barStartX = remember { mutableFloatStateOf(0f) }
    val barWidthPx = remember { mutableFloatStateOf(0f) }
    val barSpacingPx = remember { mutableFloatStateOf(0f) }
    val barCountState = remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.screen_time_daily_breakdown),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (selectedIndex != null && onClearSelection != null) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onClearSelection() }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val barCount = buckets.size
            val isAnnual = dailyData.size > 31
            val canvasWidth = with(density) { maxWidth.toPx() }
            val labelGap = with(density) { 4.dp.toPx() }
            val barSpacing = with(density) { (if (barCount <= 7) 6.dp else 3.dp).toPx() }
            val totalSpacing = barSpacing * (barCount - 1).coerceAtLeast(0)
            val barWidth = if (barCount == 0) 0f else
                ((canvasWidth - totalSpacing) / barCount).coerceIn(0f, with(density) { 40.dp.toPx() })
            val stepWidth = barWidth + barSpacing
            val startX = (canvasWidth - barWidth * barCount - totalSpacing) / 2
            val textConstraints = Constraints(maxWidth = canvasWidth.toInt().coerceAtLeast(1))
            val dayStyle = TextStyle(fontSize = 10.sp, color = SubtextColor)
            val fullLabels = buckets.map { textMeasurer.measure(it.label, style = dayStyle, constraints = textConstraints) }
            val compactMonths = isAnnual && fullLabels.any { it.size.width + labelGap > stepWidth }
            val dayLabels = if (compactMonths) buckets.map {
                textMeasurer.measure(it.compactLabel, style = dayStyle, constraints = textConstraints)
            } else fullLabels
            // Allocate another label row only when measured neighbours would overlap.
            val rowEnds = mutableListOf<Float>()
            val dayPositions = dayLabels.mapIndexed { index, label ->
                val x = (startX + index * stepWidth + (barWidth - label.size.width) / 2)
                    .coerceIn(0f, (canvasWidth - label.size.width).coerceAtLeast(0f))
                val row = rowEnds.indexOfFirst { x >= it + labelGap }.let {
                    if (it >= 0) it else rowEnds.size.also { rowEnds.add(Float.NEGATIVE_INFINITY) }
                }
                rowEnds[row] = x + label.size.width
                x to row
            }
            val dayRowHeight = dayLabels.maxOfOrNull { it.size.height }?.toFloat() ?: 0f
            val timeLabels = buckets.map {
                textMeasurer.measure(
                    formatWatchTime(it.watchTimeMs),
                    style = TextStyle(fontSize = 9.sp, color = Color.White),
                    constraints = textConstraints
                )
            }
            val topLabelHeight = ((timeLabels.maxOfOrNull { it.size.height } ?: 0) + labelGap)
                .coerceAtLeast(with(density) { 20.dp.toPx() })
            val bottomLabelHeight = (dayRowHeight * rowEnds.size + labelGap * 2)
                .coerceAtLeast(with(density) { 24.dp.toPx() })
            val canvasHeight = with(density) {
                // Preserve the default 116dp plot; text bands grow with the font.
                (116.dp.toPx() + topLabelHeight + bottomLabelHeight).toDp()
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
                    .then(
                        if (onBucketClick != null) Modifier.pointerInput(buckets.size) {
                            detectTapGestures { offset ->
                                val count = barCountState.intValue
                                if (count == 0) return@detectTapGestures
                                val bw = barWidthPx.floatValue
                                val bs = barSpacingPx.floatValue
                                val sx = barStartX.floatValue
                                val tappedIndex = ((offset.x - sx) / (bw + bs)).toInt()
                                if (tappedIndex in 0 until count) onBucketClick(tappedIndex)
                            }
                        } else Modifier
                    )
            ) {
                if (barCount == 0) return@Canvas
                val chartHeight = (size.height - bottomLabelHeight - topLabelHeight).coerceAtLeast(0f)
                barStartX.floatValue = startX
                barWidthPx.floatValue = barWidth
                barSpacingPx.floatValue = barSpacing
                barCountState.intValue = barCount

                buckets.forEachIndexed { index, bucket ->
                    val x = startX + index * stepWidth
                    val barHeight = if (maxWatchTimeMs > 0)
                        (bucket.watchTimeMs.toFloat() / maxWatchTimeMs) * chartHeight
                    else 0f
                    val barY = topLabelHeight + chartHeight - barHeight

                    if (barHeight > 0) {
                        val isSelected = selectedIndex == null || selectedIndex == index
                        val barColor = if (bucket.watchTimeMs == maxWatchTimeMs) BarColor else BarColorDark
                        drawRoundRect(
                            color = if (isSelected) barColor else barColor.copy(alpha = 0.25f),
                            topLeft = Offset(x, barY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                    if (if (isAnnual) selectedIndex == index else barCount <= 12 && barHeight > 0) {
                        val timeMeasured = timeLabels[index]
                        drawText(
                            textLayoutResult = timeMeasured,
                            topLeft = Offset(
                                (x + (barWidth - timeMeasured.size.width) / 2)
                                    .coerceIn(0f, (size.width - timeMeasured.size.width).coerceAtLeast(0f)),
                                barY - timeMeasured.size.height - labelGap
                            )
                        )
                    }
                    val (labelX, labelRow) = dayPositions[index]
                    drawText(
                        textLayoutResult = dayLabels[index],
                        topLeft = Offset(
                            labelX,
                            topLabelHeight + chartHeight + labelGap + labelRow * dayRowHeight
                        )
                    )
                }
            }
        }
    }
}

private fun aggregateBuckets(dailyData: List<DailyWatchTime>): List<ChartBucket> {
    if (dailyData.isEmpty()) return emptyList()

    val days = dailyData.size

    return when {
        days <= 7 -> {
            dailyData.map { data ->
                val date = LocalDate.ofEpochDay(data.dateEpochDay)
                val label = date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
                ChartBucket(label, data.watchTimeMs)
            }
        }
        days <= 31 -> {
            dailyData.groupBy { data ->
                val date = LocalDate.ofEpochDay(data.dateEpochDay)
                val weekField = java.time.temporal.WeekFields.of(Locale.getDefault()).weekOfMonth()
                date.get(weekField)
            }.toSortedMap().entries.mapIndexed { index, (_, weekDays) ->
                val totalWatchTimeMs = weekDays.sumOf { it.watchTimeMs }
                ChartBucket("W${index + 1}", totalWatchTimeMs)
            }
        }
        else -> {
            dailyData.groupBy { data ->
                val date = LocalDate.ofEpochDay(data.dateEpochDay)
                date.month
            }.entries.sortedBy { it.key }.map { (month, monthDays) ->
                val totalWatchTimeMs = monthDays.sumOf { it.watchTimeMs }
                val label = month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
                ChartBucket(label, totalWatchTimeMs, month.value.toString())
            }
        }
    }
}

@Composable
fun PeakHoursChart(
    peakHours: List<PeakHourBucket>,
    selectedIndex: Int? = null,
    onBucketClick: ((bucketIndex: Int) -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val maxTimeMs = peakHours.maxOfOrNull { it.watchTimeMs } ?: 1

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.screen_time_peak_hours),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (selectedIndex != null && onClearSelection != null) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onClearSelection() }
                )
            }
        }

        peakHours.forEachIndexed { index, bucket ->
            val localizedLabel = when (bucket.label) {
                "Morning" -> stringResource(R.string.screen_time_morning)
                "Afternoon" -> stringResource(R.string.screen_time_afternoon)
                "Evening" -> stringResource(R.string.screen_time_evening)
                "Night" -> stringResource(R.string.screen_time_night)
                else -> bucket.label
            }
            val isActive = selectedIndex == null || selectedIndex == index
            Row(
                modifier = Modifier.fillMaxWidth()
                    .then(if (onBucketClick != null) Modifier.clickable { onBucketClick(index) } else Modifier)
                    .then(if (!isActive) Modifier.alpha(0.3f) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = localizedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(0.25f)
                )

                Box(modifier = Modifier.weight(0.45f)) {
                    val fraction = if (maxTimeMs > 0) bucket.watchTimeMs.toFloat() / maxTimeMs else 0f
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    ) {
                        if (fraction > 0f) {
                            drawRoundRect(
                                color = BarColor,
                                topLeft = Offset.Zero,
                                size = Size(size.width * fraction, size.height),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        } else {
                            drawRoundRect(
                                color = BarColor.copy(alpha = 0.15f),
                                topLeft = Offset.Zero,
                                size = Size(4.dp.toPx(), size.height),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }
                    }
                }

                Text(
                    text = formatWatchTime(bucket.watchTimeMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(0.30f)
                )
            }
        }
    }
}
