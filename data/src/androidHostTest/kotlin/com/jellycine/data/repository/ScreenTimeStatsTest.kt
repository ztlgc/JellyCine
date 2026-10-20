package com.jellycine.data.repository

import com.jellycine.data.model.VideoWatchHistory
import com.jellycine.data.model.VideoWatchSession
import com.jellycine.data.model.VideoWatchSpan
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenTimeStatsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 10, 19)
    private fun at(value: String) = ZonedDateTime.parse(value + "+08:00").toInstant().toEpochMilli()
    private fun session(id: String, item: String = id, time: String = "2026-10-19T10:00:00", scope: String = "server|user") =
        VideoWatchSession(id, scope, item, "视频 $item", at(time))
    private fun span(id: String, start: String, end: String) = VideoWatchSpan(id, at(start), at(end))
    private fun stats(history: VideoWatchHistory) = computeScreenTimeStats(history, "server|user", day, day, zone)

    @Test fun repeatedVisitsAccumulateActualTimeAndCountEveryOpen() {
        val result = stats(VideoWatchHistory(
            listOf(session("first", "video"), session("second", "video")),
            listOf(span("first", "2026-10-19T10:00:00", "2026-10-19T10:00:12"),
                span("second", "2026-10-19T11:00:00", "2026-10-19T11:00:03"))
        ))
        assertEquals(15_000L, result.totalWatchTimeMs)
        assertEquals(1, result.videoCount)
        assertEquals(2, result.openCount)
        assertEquals(15_000L, result.topByWatchTime.single().watchTimeMs)
        assertEquals(2, result.topByOpenCount.single().openCount)
    }

    @Test fun midnightPlaybackIsClippedToEachDayWithoutInventingAnOpen() {
        val history = VideoWatchHistory(listOf(session("overnight", time = "2026-10-18T23:59:50")),
            listOf(span("overnight", "2026-10-18T23:59:50", "2026-10-19T00:00:20")))
        val current = stats(history)
        val previous = computeScreenTimeStats(history, "server|user", day.minusDays(1), day.minusDays(1), zone)
        assertEquals(20_000L, current.totalWatchTimeMs)
        assertEquals(10_000L, previous.totalWatchTimeMs)
        assertEquals(0, current.openCount)
        assertEquals(1, previous.openCount)
        assertEquals(20_000L, current.dailyBreakdown.single().watchTimeMs)
        assertEquals(20_000L, current.peakHours.last().watchTimeMs)
        assertTrue(current.topByOpenCount.isEmpty())
        assertEquals("overnight", current.topByWatchTime.single().itemId)
    }

    @Test fun hourBucketsUseTimeSpentAcrossBoundariesRatherThanVideoCounts() {
        val history = VideoWatchHistory(listOf(session("boundary")),
            listOf(span("boundary", "2026-10-19T11:59:50", "2026-10-19T12:00:30")))
        val result = stats(history)
        assertEquals(listOf(10_000L, 30_000L, 0L, 0L), result.peakHours.map { it.watchTimeMs })
        val afternoon = computeScreenTimeStats(history, "server|user", day, day, zone, selectedHours = 12..17)
        assertEquals(30_000L, afternoon.totalWatchTimeMs)
        assertEquals(0, afternoon.openCount)
    }

    @Test fun emptyDaysRemainInTheAverageIncludingWhenSelectingAnEntireBucket() {
        val history = VideoWatchHistory(listOf(session("short")),
            listOf(VideoWatchSpan("short", at("2026-10-19T10:00:00"), at("2026-10-19T10:00:00") + 900)))
        val result = computeScreenTimeStats(history, "server|user", day, day.plusDays(2), zone,
            selectedDays = setOf(day.toEpochDay(), day.plusDays(1).toEpochDay(), day.plusDays(2).toEpochDay()))
        assertEquals(900L, result.totalWatchTimeMs)
        assertEquals(300L, result.dailyAverageMs)
        assertEquals(listOf(900L, 0L, 0L), result.dailyBreakdown.map { it.watchTimeMs })
    }

    @Test fun theTwoTopFivesUseIndependentPeriodScopedMetrics() {
        val sessions = (1..7).flatMap { n -> (1..n).map { visit -> session("$n-$visit", "$n") } } +
            session("old", "7", "2026-10-18T10:00:00")
        val spans = (1..7).map { n -> VideoWatchSpan("$n-1", at("2026-10-19T10:00:00"), at("2026-10-19T10:00:00") + (8 - n) * 1000) } +
            span("old", "2026-10-18T10:00:00", "2026-10-18T20:00:00")
        val result = stats(VideoWatchHistory(sessions, spans))
        assertEquals(listOf("1", "2", "3", "4", "5"), result.topByWatchTime.map { it.itemId })
        assertEquals(listOf("7", "6", "5", "4", "3"), result.topByOpenCount.map { it.itemId })
        assertEquals(7, result.topByOpenCount.first().openCount)
        assertEquals(7_000L, result.topByWatchTime.first().watchTimeMs)
    }

    @Test fun openingWithoutPlaybackCountsAndDoesNotInventWatchTime() {
        val result = stats(VideoWatchHistory(listOf(session("opened"))))
        assertEquals(0L, result.totalWatchTimeMs)
        assertEquals(1, result.openCount)
        assertEquals(1, result.videoCount)
        assertTrue(result.topByWatchTime.isEmpty())
        assertEquals("opened", result.topByOpenCount.single().itemId)
    }

    @Test fun otherAccountsAndOrphanIntervalsCannotLeakIntoTheResult() {
        val history = VideoWatchHistory(listOf(session("mine"), session("other", scope = "server|other")),
            listOf(span("other", "2026-10-19T10:00:00", "2026-10-19T20:00:00"),
                span("missing", "2026-10-19T10:00:00", "2026-10-19T20:00:00")))
        assertEquals(0L, stats(history).totalWatchTimeMs)
        assertEquals(listOf("mine"), stats(history).topByOpenCount.map { it.itemId })
    }

    @Test fun daylightSavingFallbackDoesNotDuplicateOrDropElapsedTime() {
        val dstZone = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2026, 11, 1)
        val start = ZonedDateTime.parse("2026-11-01T01:59:50-04:00").toInstant().toEpochMilli()
        val end = ZonedDateTime.parse("2026-11-01T01:00:10-05:00").toInstant().toEpochMilli()
        val history = VideoWatchHistory(listOf(VideoWatchSession("dst", "server|user", "dst", "DST", start)),
            listOf(VideoWatchSpan("dst", start, end)))
        val result = computeScreenTimeStats(history, "server|user", dstDay, dstDay, dstZone)
        assertEquals(20_000L, result.totalWatchTimeMs)
        assertEquals(20_000L, result.peakHours.last().watchTimeMs)
    }
}
