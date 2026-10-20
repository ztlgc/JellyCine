package com.jellycine.data.model

/** One entry into the ordinary-video player, including entries with no playback yet. */
data class VideoWatchSession(
    val id: String,
    val scopeKey: String,
    val itemId: String,
    val name: String,
    val openedAtMs: Long
)

/** A real elapsed playback interval. Seeking and playback speed never change its length. */
data class VideoWatchSpan(val sessionId: String, val startMs: Long, val endMs: Long)

data class VideoWatchHistory(
    val sessions: List<VideoWatchSession> = emptyList(),
    val spans: List<VideoWatchSpan> = emptyList()
)

data class VideoWatchSummary(val itemId: String, val name: String, val watchTimeMs: Long, val openCount: Int)

data class ScreenTimeStats(
    val totalWatchTimeMs: Long,
    val dailyAverageMs: Long,
    val dailyBreakdown: List<DailyWatchTime>,
    val peakHours: List<PeakHourBucket>,
    val videoCount: Int,
    val openCount: Int,
    val topByWatchTime: List<VideoWatchSummary>,
    val topByOpenCount: List<VideoWatchSummary>
)

data class DailyWatchTime(val dateEpochDay: Long, val watchTimeMs: Long)

data class PeakHourBucket(val label: String, val watchTimeMs: Long)
