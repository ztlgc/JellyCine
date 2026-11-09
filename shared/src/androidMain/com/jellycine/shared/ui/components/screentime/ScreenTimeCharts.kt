package com.jellycine.shared.ui.components.screentime

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.DailyWatchTime
import com.jellycine.data.model.PeakHourBucket
import com.jellycine.shared.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import java.time.format.TextStyle as DateTextStyle

private val BarColor = Color(0xFF22D3EE)
private val SubtextColor = Color.White.copy(alpha = 0.68f)
private val AxisColor = Color.White.copy(alpha = 0.46f)
private val GridColor = Color.White.copy(alpha = 0.08f)
private val TrackColor = Color(0xFF293236)

private data class ChartBucket(val days: List<DailyWatchTime>) {
    val watchTimeMs: Long = days.sumOf { it.watchTimeMs }
    val startDate: LocalDate = LocalDate.ofEpochDay(days.minOf { it.dateEpochDay })
    val endDate: LocalDate = LocalDate.ofEpochDay(days.maxOf { it.dateEpochDay })
}

@Composable
fun DailyBreakdownChart(
    dailyData: List<DailyWatchTime>,
    selectedIndex: Int? = null,
    onBucketClick: ((bucketIndex: Int) -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val buckets = aggregateBuckets(dailyData)
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val compactDateFormatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "Md"), locale)
    val bucketLabels = buckets.map { bucket ->
        when {
            dailyData.size <= 7 -> bucket.startDate.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale)
            dailyData.size <= 31 -> if (bucket.startDate.month == bucket.endDate.month) {
                stringResource(
                    R.string.screen_time_chart_day_range,
                    bucket.startDate.dayOfMonth,
                    bucket.endDate.dayOfMonth
                )
            } else {
                dateRangeLabel(bucket.startDate, bucket.endDate, compactDateFormatter)
            }
            else -> bucket.startDate.month.getDisplayName(DateTextStyle.SHORT, locale)
        }
    }
    val bucketDescriptions = buckets.map { bucket ->
        stringResource(
            R.string.screen_time_chart_date_value,
            dateRangeLabel(bucket.startDate, bucket.endDate, dateFormatter),
            formatWatchTime(bucket.watchTimeMs, locale)
        )
    }
    val selectedBucketIndex = selectedIndex?.takeIf { it in buckets.indices }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.screen_time_chart_trend),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(
                text = stringResource(
                    when {
                        dailyData.size <= 7 -> R.string.screen_time_chart_daily
                        dailyData.size <= 31 -> R.string.screen_time_chart_weekly
                        else -> R.string.screen_time_chart_monthly
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = SubtextColor,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (buckets.isNotEmpty()) {
            Text(
                text = dateRangeLabel(buckets.minOf { it.startDate }, buckets.maxOf { it.endDate }, dateFormatter),
                style = MaterialTheme.typography.bodySmall,
                color = SubtextColor
            )
            TrendPlot(buckets, bucketLabels, bucketDescriptions, selectedIndex, onBucketClick)
        } else {
            Text(
                text = stringResource(R.string.screen_time_chart_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = SubtextColor,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        if (selectedBucketIndex != null) {
            ChartSelection(
                description = bucketDescriptions[selectedBucketIndex],
                onClearSelection = onClearSelection
            )
        }
    }
}

@Composable
private fun TrendPlot(
    buckets: List<ChartBucket>,
    labels: List<String>,
    descriptions: List<String>,
    selectedIndex: Int?,
    onBucketClick: ((Int) -> Unit)?
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    val filterLabel = stringResource(R.string.screen_time_chart_filter_period)
    val maximum = buckets.maxOf { it.watchTimeMs }
    val valueIndex = selectedIndex?.takeIf { it in buckets.indices }
        ?: buckets.indexOfFirst { it.watchTimeMs == maximum }.takeIf { maximum > 0L }
    val tickStep = axisTickStep(maximum)
    val scaleMaximum = tickStep * 2.0
    val labelStyle = TextStyle(fontSize = 10.sp, color = SubtextColor)
    val axisStyle = TextStyle(fontSize = 10.sp, color = AxisColor)
    val valueStyle = TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val canvasWidth = with(density) { maxWidth.toPx() }
        val gap = with(density) { 6.dp.toPx() }
        // 按当前字体大小测量坐标轴，为文字独立预留空间。
        val tickConstraints = Constraints(maxWidth = (canvasWidth * 0.30f).toInt().coerceAtLeast(1))
        val ticks = listOf(0L, tickStep, (tickStep * 2.0).coerceAtMost(Long.MAX_VALUE.toDouble()).toLong())
        val tickLabels = ticks.map {
            textMeasurer.measure(if (it == 0L) "0" else formatWatchTime(it, locale), style = axisStyle, constraints = tickConstraints)
        }
        val plotLeft = (tickLabels.maxOf { it.size.width } + gap * 2).coerceAtMost(canvasWidth * 0.45f)
        val plotWidth = (canvasWidth - plotLeft).coerceAtLeast(1f)
        val slotWidth = plotWidth / buckets.size
        val barWidth = minOf(with(density) { 24.dp.toPx() }, (slotWidth - gap / 2).coerceAtLeast(1f))
        val plotConstraints = Constraints(maxWidth = plotWidth.toInt().coerceAtLeast(1))
        val measuredLabels = labels.map {
            textMeasurer.measure(it, style = labelStyle, constraints = plotConstraints)
        }
        // 标签重叠时按顺序换行，并对每行居中，避免出现读取顺序错乱。
        var currentRow = 0
        val rowEnds = mutableListOf(Float.NEGATIVE_INFINITY)
        val rowLabels = mutableListOf<MutableList<Int>>(mutableListOf())
        val rawPositions = measuredLabels.mapIndexed { index, label ->
            val centeredX = (plotLeft + (index + 0.5f) * slotWidth - label.size.width / 2f)
                .coerceIn(plotLeft, (canvasWidth - label.size.width).coerceAtLeast(plotLeft))
            if (centeredX < rowEnds[currentRow] + gap) {
                currentRow++
                rowEnds.add(Float.NEGATIVE_INFINITY)
                rowLabels.add(mutableListOf())
            }
            rowEnds[currentRow] = centeredX + label.size.width
            rowLabels[currentRow].add(index)
            centeredX to currentRow
        }
        val labelPositions = rawPositions.toMutableList()
        rowLabels.forEachIndexed { row, indexes ->
            if (indexes.isEmpty()) return@forEachIndexed
            val left = indexes.minOf { rawPositions[it].first }
            val right = indexes.maxOf { rawPositions[it].first + measuredLabels[it].size.width }
            val shift = ((plotLeft + plotWidth - (right - left)) / 2f - left)
                .coerceIn(-left, (canvasWidth - right).coerceAtLeast(-left))
            indexes.forEach { labelPositions[it] = rawPositions[it].first + shift to row }
        }
        val labelRowHeight = measuredLabels.maxOf { it.size.height }.toFloat() + gap
        val valueLabel = valueIndex?.let {
            textMeasurer.measure(formatWatchTime(buckets[it].watchTimeMs, locale), style = valueStyle, constraints = plotConstraints)
        }
        val topBand = maxOf(
            (valueLabel?.size?.height ?: 0).toFloat() + gap,
            tickLabels.last().size.height / 2f
        )
        val plotHeight = maxOf(
            with(density) { 116.dp.toPx() },
            tickLabels.maxOf { it.size.height } * 2f + gap * 4
        )
        val axisLabelTop = topBand + plotHeight + tickLabels.first().size.height / 2f + gap
        val canvasHeight = with(density) { (axisLabelTop + rowEnds.size * labelRowHeight + gap).toDp() }

        Box(modifier = Modifier.fillMaxWidth().height(canvasHeight)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val baseline = topBand + plotHeight
                tickLabels.forEachIndexed { index, tick ->
                    val y = baseline - index * plotHeight / 2f
                    drawLine(GridColor, Offset(plotLeft, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    drawText(tick, topLeft = Offset(plotLeft - gap - tick.size.width, y - tick.size.height / 2f))
                }
                buckets.forEachIndexed { index, bucket ->
                    val x = plotLeft + (index + 0.5f) * slotWidth - barWidth / 2f
                    val barHeight = (bucket.watchTimeMs / scaleMaximum).toFloat().coerceIn(0f, 1f) * plotHeight
                    val y = baseline - barHeight
                    if (barHeight > 0f) {
                        val radius = minOf(4.dp.toPx(), barWidth / 2, barHeight / 2)
                        val path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = x, top = y, right = x + barWidth, bottom = baseline,
                                    topLeftCornerRadius = CornerRadius(radius, radius),
                                    topRightCornerRadius = CornerRadius(radius, radius)
                                )
                            )
                        }
                        drawPath(
                            path,
                            color = if (selectedIndex == null || selectedIndex == index) BarColor
                            else BarColor.copy(alpha = 0.28f)
                        )
                    }
                    if (index == valueIndex && valueLabel != null) {
                        drawText(
                            valueLabel,
                            topLeft = Offset(
                                (x + (barWidth - valueLabel.size.width) / 2f)
                                    .coerceIn(plotLeft, (size.width - valueLabel.size.width).coerceAtLeast(plotLeft)),
                                y - valueLabel.size.height - gap
                            )
                        )
                    }
                    val (labelX, row) = labelPositions[index]
                    drawText(measuredLabels[index], topLeft = Offset(labelX, axisLabelTop + row * labelRowHeight))
                }
            }
            // 整列都能点击，包括零值；同时为 TalkBack 和键盘提供日期、数值及选择状态。
            Row(
                modifier = Modifier.matchParentSize().padding(start = with(density) { plotLeft.toDp() })
            ) {
                buckets.indices.forEach { index ->
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .semantics {
                                contentDescription = descriptions[index]
                                selected = selectedIndex == index
                            }
                            .then(
                                if (onBucketClick != null) Modifier.clickable(
                                    onClickLabel = filterLabel,
                                    role = Role.Button
                                ) { onBucketClick(index) } else Modifier
                            )
                    )
                }
            }
        }
    }
}

// 分桶顺序和边界必须与 ScreenTimeScreen.daysForBucket 一致。
// 月内周数由地区决定，展示日期范围时不能改变点击后实际选中的日期。
private fun aggregateBuckets(dailyData: List<DailyWatchTime>): List<ChartBucket> = when {
    dailyData.isEmpty() -> emptyList()
    dailyData.size <= 7 -> dailyData.map { ChartBucket(listOf(it)) }
    dailyData.size <= 31 -> dailyData.groupBy {
        LocalDate.ofEpochDay(it.dateEpochDay).get(WeekFields.of(Locale.getDefault()).weekOfMonth())
    }.toSortedMap().values.map { ChartBucket(it) }
    else -> dailyData.groupBy {
        LocalDate.ofEpochDay(it.dateEpochDay).monthValue
    }.toSortedMap().values.map { ChartBucket(it) }
}

private fun axisTickStep(maximumMs: Long): Long {
    val half = maximumMs / 2.0
    val timeSteps = longArrayOf(
        1_000L, 5_000L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L,
        600_000L, 900_000L, 1_800_000L, 3_600_000L, 7_200_000L,
        14_400_000L, 21_600_000L, 43_200_000L, 86_400_000L
    )
    if (maximumMs <= 0L) return 30_000L
    timeSteps.firstOrNull { it >= half }?.let { return it }
    val days = half / 86_400_000.0
    val magnitude = 10.0.pow(floor(log10(days)))
    val rounded = ceil(days / magnitude) * magnitude * 86_400_000.0
    return rounded.coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
}

@Composable
private fun dateRangeLabel(start: LocalDate, end: LocalDate, formatter: DateTimeFormatter): String =
    if (start == end) start.format(formatter) else stringResource(
        R.string.screen_time_chart_date_range,
        start.format(formatter),
        end.format(formatter)
    )

@Composable
private fun ChartSelection(description: String, onClearSelection: (() -> Unit)?) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.screen_time_chart_selected_value, description),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )
        if (onClearSelection != null) {
            TextButton(onClick = onClearSelection) {
                Text(text = stringResource(R.string.screen_time_chart_clear_selection), color = Color.White)
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
    val maxTimeMs = peakHours.maxOfOrNull { it.watchTimeMs } ?: 0L
    val locale = LocalConfiguration.current.locales[0]
    val filterLabel = stringResource(R.string.screen_time_chart_filter_hours)
    val labels = peakHours.map { bucket ->
        when (bucket.label) {
            "Morning" -> stringResource(R.string.screen_time_chart_morning)
            "Afternoon" -> stringResource(R.string.screen_time_chart_afternoon)
            "Evening" -> stringResource(R.string.screen_time_chart_evening)
            "Night" -> stringResource(R.string.screen_time_chart_night)
            else -> bucket.label
        }
    }
    val ranges = peakHours.map { bucket ->
        when (bucket.label) {
            "Morning" -> stringResource(R.string.screen_time_chart_morning_range)
            "Afternoon" -> stringResource(R.string.screen_time_chart_afternoon_range)
            "Evening" -> stringResource(R.string.screen_time_chart_evening_range)
            "Night" -> stringResource(R.string.screen_time_chart_night_range)
            else -> ""
        }
    }
    val descriptions = peakHours.mapIndexed { index, bucket ->
        stringResource(
            R.string.screen_time_chart_hour_value,
            labels[index], ranges[index], formatWatchTime(bucket.watchTimeMs, locale)
        )
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.screen_time_chart_peak_hours),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.screen_time_chart_peak_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = SubtextColor
            )
        }
        peakHours.forEachIndexed { index, bucket ->
            val isActive = selectedIndex == null || selectedIndex == index
            Column(
                modifier = Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = descriptions[index]
                        selected = selectedIndex == index
                    }
                    .then(
                        if (onBucketClick != null) Modifier.clickable(
                            onClickLabel = filterLabel,
                            role = Role.Button
                        ) { onBucketClick(index) } else Modifier
                    )
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val fontScale = LocalDensity.current.fontScale
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 300.dp && fontScale <= 1.3f) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FlowRow(
                                modifier = Modifier.width(100.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(labels[index], style = MaterialTheme.typography.bodySmall, color = if (isActive) Color.White else SubtextColor)
                                Text(ranges[index], style = MaterialTheme.typography.labelSmall, color = if (isActive) SubtextColor else AxisColor)
                            }
                            PeakHourBar(bucket.watchTimeMs, maxTimeMs, isActive, Modifier.weight(1f))
                            Text(
                                text = formatWatchTime(bucket.watchTimeMs, locale),
                                modifier = Modifier.width(84.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isActive) Color.White else SubtextColor,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.screen_time_chart_hour_label, labels[index], ranges[index]),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isActive) SubtextColor else AxisColor,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Text(
                                    text = formatWatchTime(bucket.watchTimeMs, locale),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isActive) Color.White else SubtextColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            PeakHourBar(bucket.watchTimeMs, maxTimeMs, isActive, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        if (selectedIndex != null && selectedIndex in peakHours.indices) {
            ChartSelection(descriptions[selectedIndex], onClearSelection)
        }
    }
}

@Composable
private fun PeakHourBar(watchTimeMs: Long, maximumMs: Long, active: Boolean, modifier: Modifier) {
    Canvas(modifier = modifier.height(8.dp)) {
        drawRoundRect(TrackColor, cornerRadius = CornerRadius(size.height / 2))
        val fraction = if (maximumMs > 0L) (watchTimeMs.toDouble() / maximumMs).toFloat().coerceIn(0f, 1f) else 0f
        if (fraction > 0f) {
            drawRoundRect(
                color = if (active) BarColor else BarColor.copy(alpha = 0.28f),
                size = Size(size.width * fraction, size.height),
                cornerRadius = CornerRadius(size.height / 2)
            )
        }
    }
}
