package com.jellycine.app.screentime

import com.jellycine.app.ui.screens.screentime.ScreenTimePeriod
import com.jellycine.app.ui.screens.screentime.ScreenTimeUiState
import com.jellycine.app.ui.screens.screentime.loadingStateFor
import com.jellycine.data.model.DailyWatchTime
import com.jellycine.data.model.ScreenTimeStats
import com.jellycine.data.model.VideoWatchHistory
import com.jellycine.data.model.VideoWatchSession
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeRefreshStateTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val request = ScreenTimeUiState(
        period = ScreenTimePeriod.MONTH, year = 2026, monthOffset = -1,
        startDate = start, endDate = start.plusDays(30)
    )
    private val stats = ScreenTimeStats(
        totalWatchTimeMs = 60_000, dailyAverageMs = 60_000 / 31,
        dailyBreakdown = List(31) { DailyWatchTime(start.plusDays(it.toLong()).toEpochDay(), if (it == 0) 60_000 else 0) },
        peakHours = emptyList(), videoCount = 1, openCount = 1,
        topByWatchTime = emptyList(), topByOpenCount = emptyList()
    )
    private val loaded = request.copy(
        stats = stats,
        previousStats = stats.copy(totalWatchTimeMs = 30_000),
        history = VideoWatchHistory(sessions = listOf(VideoWatchSession("session", "server|user", "video", "示例视频", 0))),
        scopeKey = "server|user", isLoading = false
    )

    @Test
    fun refreshingTheSameRangeKeepsTheEntireDisplayedSnapshot() {
        val refreshing = loaded.loadingStateFor(request)
        assertTrue(refreshing.isLoading)
        assertSame(loaded.stats, refreshing.stats)
        assertSame(loaded.previousStats, refreshing.previousStats)
        assertSame(loaded.history, refreshing.history)
        assertEquals(loaded.scopeKey, refreshing.scopeKey)
        assertEquals(loaded.startDate, refreshing.startDate)
        assertEquals(loaded.endDate, refreshing.endDate)
    }

    @Test
    fun firstLoadStillHasNoStatisticsUntilReadingCompletes() {
        val initial = ScreenTimeUiState().loadingStateFor(request)
        assertTrue(initial.isLoading)
        assertNull(initial.stats)
        assertNull(initial.previousStats)
        assertTrue(initial.history.sessions.isEmpty())
    }

    @Test
    fun changingThePeriodCannotShowStatisticsFromTheOldPeriod() {
        val yearly = request.copy(period = ScreenTimePeriod.YEAR, startDate = LocalDate.of(2026, 1, 1))
        val loading = loaded.loadingStateFor(yearly)
        assertEquals(ScreenTimePeriod.YEAR, loading.period)
        assertNull(loading.stats)
        assertNull(loading.previousStats)
        assertTrue(loading.history.sessions.isEmpty())
    }

    @Test
    fun changingDatesWithinTheSamePeriodCannotKeepTheOldSnapshot() {
        val september = request.copy(monthOffset = -2, startDate = start.minusMonths(1), endDate = start.minusDays(1))
        val loading = loaded.loadingStateFor(september)
        assertEquals(september.startDate, loading.startDate)
        assertEquals(september.endDate, loading.endDate)
        assertNull(loading.stats)
        assertTrue(loading.history.sessions.isEmpty())
    }

    @Test
    fun resolvingAnotherAccountClearsThePreviousAccountBeforeReading() {
        val pending = loaded.loadingStateFor(request)
        val switched = pending.loadingStateFor(request, "server|another-user")
        assertEquals("server|another-user", switched.scopeKey)
        assertNull(switched.stats)
        assertNull(switched.previousStats)
        assertTrue(switched.history.sessions.isEmpty())
        assertTrue(switched.isLoading)
    }

    @Test
    fun retryClearsTheErrorWithoutDiscardingStatistics() {
        val failed = loaded.copy(error = "watch_history_load_failed")
        val retry = failed.loadingStateFor(request, loaded.scopeKey)
        assertNull(retry.error)
        assertSame(loaded.stats, retry.stats)
        assertSame(loaded.history, retry.history)
        assertTrue(retry.isLoading)
    }

    @Test
    fun repeatedRefreshesAndEmptyResultsDoNotReturnToFirstLoad() {
        val empty = stats.copy(totalWatchTimeMs = 0, dailyAverageMs = 0, videoCount = 0, openCount = 0)
        val existing = loaded.copy(stats = empty)
        val first = existing.loadingStateFor(request)
        val repeated = first.loadingStateFor(request, existing.scopeKey)
        assertSame(empty, repeated.stats)
        assertEquals(0, repeated.stats!!.videoCount)
        assertFalse(repeated.scopeKey.isEmpty())
        assertEquals(first, repeated)
    }
}
