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

internal fun ScreenTimeUiState.loadingStateFor(
    requested: ScreenTimeUiState,
    resolvedScopeKey: String = scopeKey
): ScreenTimeUiState {
    val sameSelection = period == requested.period && year == requested.year &&
        monthOffset == requested.monthOffset && weekOffset == requested.weekOffset &&
        startDate == requested.startDate && endDate == requested.endDate
    // 同一账号和日期范围内的刷新保留完整快照，避免图表及排行在读取期间被移除。
    return if (stats != null && sameSelection && scopeKey == resolvedScopeKey) {
        copy(isLoading = true, error = null)
    } else {
        requested.copy(scopeKey = resolvedScopeKey)
    }
}
