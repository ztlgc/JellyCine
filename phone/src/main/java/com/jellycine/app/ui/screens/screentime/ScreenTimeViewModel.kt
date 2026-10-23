package com.jellycine.app.ui.screens.screentime

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jellycine.data.datastore.VideoWatchHistoryStore
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.repository.computeScreenTimeStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

class ScreenTimeViewModel(context: Context) : ViewModel() {
    private val mediaRepository = MediaRepositoryProvider.getInstance(context)
    private val historyStore = VideoWatchHistoryStore.getInstance(context)
    private val _screenTimeState = MutableStateFlow(ScreenTimeUiState())
    val screenTimeState: StateFlow<ScreenTimeUiState> = _screenTimeState.asStateFlow()
    private var loadJob: Job? = null

    suspend fun getItemImageUrl(itemId: String): String? = mediaRepository.getImageUrlString(
        itemId = itemId, imageType = "Primary", width = 200, quality = 85, enableImageEnhancers = false
    )

    init {
        loadScreenTime()
        viewModelScope.launch { historyStore.contentRevision.collect { refresh() } }
        com.jellycine.app.sync.WatchSyncController.get(context).requestSync()
    }

    fun refresh() {
        val state = _screenTimeState.value
        loadScreenTime(state.period, state.year, state.monthOffset, state.weekOffset)
    }

    fun loadScreenTime(
        period: ScreenTimePeriod = ScreenTimePeriod.WEEK,
        year: Int = LocalDate.now().year,
        monthOffset: Int = 0,
        weekOffset: Int = 0
    ) {
        loadJob?.cancel()
        val today = LocalDate.now()
        val (start, end) = when (period) {
            ScreenTimePeriod.WEEK -> {
                val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
                val start = today.with(TemporalAdjusters.previousOrSame(firstDay)).plusWeeks(weekOffset.toLong())
                start to if (weekOffset == 0) today else start.plusDays(6)
            }
            ScreenTimePeriod.MONTH -> {
                val month = today.plusMonths(monthOffset.toLong())
                month.withDayOfMonth(1) to if (monthOffset == 0) today else month.withDayOfMonth(month.lengthOfMonth())
            }
            ScreenTimePeriod.YEAR -> LocalDate.of(year, 1, 1) to
                if (year == today.year) today else LocalDate.of(year, 12, 31)
        }
        val requested = ScreenTimeUiState(period, year, monthOffset, weekOffset, startDate = start, endDate = end)
        _screenTimeState.value = requested
        loadJob = viewModelScope.launch {
            try {
                val scope = mediaRepository.getWatchHistoryScopeKey().orEmpty()
                val previousEnd = start.minusDays(1)
                val previousStart = previousEnd.minusDays(ChronoUnit.DAYS.between(start, end))
                val zone = ZoneId.systemDefault()
                val history = historyStore.load(scope,
                    previousStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                    end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                val (stats, previous) = withContext(Dispatchers.Default) {
                    computeScreenTimeStats(history, scope, start, end, zone) to
                        computeScreenTimeStats(history, scope, previousStart, previousEnd, zone)
                }
                _screenTimeState.value = requested.copy(
                    history = history, scopeKey = scope, stats = stats, previousStats = previous, isLoading = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _screenTimeState.value = requested.copy(isLoading = false, error = "watch_history_load_failed")
            }
        }
    }
}

class ScreenTimeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ScreenTimeViewModel(context) as T
}
