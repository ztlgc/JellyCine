package com.jellycine.app.update

import android.content.Context
import android.content.SharedPreferences
import com.jellycine.app.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data object Failed : AppUpdateState
    data class Available(val release: ReleaseInfo) : AppUpdateState
}

internal object AppUpdateRules {
    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** 距上次成功检查超过间隔才自动检查；时钟回拨（当前时间早于记录时间）时也重新检查。 */
    fun shouldAutoCheck(lastSuccessMs: Long, nowMs: Long): Boolean =
        lastSuccessMs <= 0L || nowMs < lastSuccessMs || nowMs - lastSuccessMs >= CHECK_INTERVAL_MS

    fun newerReleaseOrNull(current: AppVersion, latest: ReleaseInfo?): ReleaseInfo? =
        latest?.takeIf { it.version > current }
}

/**
 * 应用内更新检查。数据源为本仓库 GitHub Release；只负责发现新版本并提示，
 * 下载与安装由用户在浏览器中完成。
 */
class AppUpdateController private constructor(
    private val currentVersion: AppVersion,
    private val prefs: SharedPreferences,
    private val nowMs: () -> Long,
    private val fetchLatest: suspend () -> ReleaseInfo?
) {
    companion object {
        private const val PREFS_NAME = "app_update"
        private const val KEY_LAST_SUCCESS_MS = "last_success_ms"

        @Volatile private var instance: AppUpdateController? = null

        fun get(context: Context): AppUpdateController = instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                AppUpdateController(
                    currentVersion = AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(listOf(0)),
                    prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                    nowMs = { System.currentTimeMillis() },
                    fetchLatest = { fetchLatestRelease(client) }
                )
            }.also { instance = it }
        }
    }

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 启动时调用：未到间隔则跳过；静默执行，失败不打扰用户。 */
    fun checkOnLaunch() {
        if (!AppUpdateRules.shouldAutoCheck(prefs.getLong(KEY_LAST_SUCCESS_MS, 0L), nowMs())) return
        scope.launch { check(manual = false) }
    }

    /** 用户主动检查：不受间隔限制，结果同步到关于页与弹窗。 */
    fun checkNow() {
        scope.launch { check(manual = true) }
    }

    /** 点击“稍后”：收起弹窗。下一次到期的自动检查仍会再次提示。 */
    fun postpone() {
        _state.update { if (it is AppUpdateState.Available) AppUpdateState.Idle else it }
    }

    private suspend fun check(manual: Boolean) {
        mutex.withLock {
            if (manual) _state.value = AppUpdateState.Checking
            runCatching { fetchLatest() }
                .onSuccess { latest ->
                    // 只有成功才记录时间，失败不会让自动检查被推迟 24 小时。
                    prefs.edit().putLong(KEY_LAST_SUCCESS_MS, nowMs()).apply()
                    val newer = AppUpdateRules.newerReleaseOrNull(currentVersion, latest)
                    _state.value = if (newer != null) AppUpdateState.Available(newer) else AppUpdateState.UpToDate
                }
                .onFailure {
                    if (manual) _state.value = AppUpdateState.Failed
                }
        }
    }
}
