package com.jellycine.app.ui.screens.screentime

import com.jellycine.data.model.ScreenTimeStats
import com.jellycine.data.model.VideoWatchHistory
import java.time.LocalDate

enum class ScreenTimePeriod {
    WEEK, MONTH, YEAR
}

data class ScreenTimeUiState(
    val period: ScreenTimePeriod = ScreenTimePeriod.WEEK,
    val year: Int = LocalDate.now().year,
    val monthOffset: Int = 0,
    val weekOffset: Int = 0,
    val stats: ScreenTimeStats? = null,
    val previousStats: ScreenTimeStats? = null,
    val history: VideoWatchHistory = VideoWatchHistory(),
    val scopeKey: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val error: String? = null
)
