package com.jellycine.data.repository

import com.jellycine.data.model.DailyWatchTime
import com.jellycine.data.model.PeakHourBucket
import com.jellycine.data.model.ScreenTimeStats
import com.jellycine.data.model.VideoWatchHistory
import com.jellycine.data.model.VideoWatchSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Clip playback intervals to the requested dates, then split them at local hour boundaries. */
fun computeScreenTimeStats(
    history: VideoWatchHistory,
    scopeKey: String,
    startDate: LocalDate,
    endDate: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    selectedDays: Set<Long>? = null,
    selectedHours: IntRange? = null
): ScreenTimeStats {
    val rangeStart = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val rangeEnd = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val sessions = history.sessions.filter { it.scopeKey == scopeKey }.associateBy { it.id }
    val watchedByItem = mutableMapOf<String, Long>()
    val opensByItem = mutableMapOf<String, Int>()
    val names = sessions.values.sortedBy { it.openedAtMs }.associate { it.itemId to it.name }
    val daily = mutableMapOf<Long, Long>()
    val peaks = LongArray(4)

    fun isSelected(epochDay: Long, hour: Int) =
        (selectedDays == null || epochDay in selectedDays) &&
            (selectedHours == null || hour in selectedHours)

    sessions.values.forEach { session ->
        if (session.openedAtMs in rangeStart until rangeEnd) {
            val opened = Instant.ofEpochMilli(session.openedAtMs).atZone(zone)
            if (isSelected(opened.toLocalDate().toEpochDay(), opened.hour)) {
                opensByItem[session.itemId] = (opensByItem[session.itemId] ?: 0) + 1
            }
        }
    }

    history.spans.forEach { span ->
        val session = sessions[span.sessionId] ?: return@forEach
        var cursor = maxOf(span.startMs, rangeStart)
        val end = minOf(span.endMs, rangeEnd)
        while (cursor < end) {
            val time = Instant.ofEpochMilli(cursor).atZone(zone)
            val nextHour = time.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val partEnd = minOf(nextHour, end)
            val day = time.toLocalDate().toEpochDay()
            if (isSelected(day, time.hour)) {
                val elapsed = partEnd - cursor
                watchedByItem[session.itemId] = (watchedByItem[session.itemId] ?: 0L) + elapsed
                daily[day] = (daily[day] ?: 0L) + elapsed
                val bucket = when (time.hour) {
                    in 6..11 -> 0
                    in 12..17 -> 1
                    in 18..23 -> 2
                    else -> 3
                }
                peaks[bucket] += elapsed
            }
            cursor = partEnd
        }
    }

    val videos = (watchedByItem.keys + opensByItem.keys).map { id ->
        VideoWatchSummary(id, names[id].orEmpty(), watchedByItem[id] ?: 0L, opensByItem[id] ?: 0)
    }
    val days = generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.toList()
    val divisor = days.count { selectedDays == null || it.toEpochDay() in selectedDays }
    val total = watchedByItem.values.sum()
    return ScreenTimeStats(
        totalWatchTimeMs = total,
        dailyAverageMs = if (divisor > 0) total / divisor else 0L,
        dailyBreakdown = days.map { DailyWatchTime(it.toEpochDay(), daily[it.toEpochDay()] ?: 0L) },
        peakHours = listOf("Morning", "Afternoon", "Evening", "Night").mapIndexed { i, label ->
            PeakHourBucket(label, peaks[i])
        },
        videoCount = videos.size,
        openCount = opensByItem.values.sum(),
        topByWatchTime = videos.filter { it.watchTimeMs > 0 }.sortedWith(
            compareByDescending<VideoWatchSummary> { it.watchTimeMs }
                .thenByDescending { it.openCount }.thenBy { it.itemId }
        ).take(5),
        topByOpenCount = videos.filter { it.openCount > 0 }.sortedWith(
            compareByDescending<VideoWatchSummary> { it.openCount }
                .thenByDescending { it.watchTimeMs }.thenBy { it.itemId }
        ).take(5)
    )
}
