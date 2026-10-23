package com.jellycine.app.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.*
import com.jellycine.app.BuildConfig
import com.jellycine.data.datastore.VideoWatchHistoryStore
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.sync.*
import com.jellycine.shared.preferences.Preferences
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class WatchSyncError { NETWORK, AUTH, SERVER, IDENTITY, CREDENTIALS }
data class WatchSyncUiState(
    val configured: Boolean = false,
    val enabled: Boolean = false,
    val email: String? = null,
    val syncing: Boolean = false,
    val signingIn: Boolean = false,
    val verificationSent: Boolean = false,
    val offline: Boolean = false,
    val lastSuccessMs: Long = 0,
    val error: WatchSyncError? = null
)

@OptIn(FlowPreview::class)
class WatchSyncController private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: WatchSyncController? = null
        fun get(context: Context): WatchSyncController = instance ?: synchronized(this) {
            instance ?: WatchSyncController(context.applicationContext).also { instance = it }
        }
        private const val WORK = "jellycine-watch-sync"
        private const val PERIODIC = "jellycine-watch-sync-periodic"
    }

    private val config = SupabaseWatchConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
    private val prefs = context.getSharedPreferences("watch_sync_settings", Context.MODE_PRIVATE)
    private val preferences = Preferences(context)
    private val history = VideoWatchHistoryStore.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicLong(0)
    private val mutex = Mutex()
    private val loginMutex = Mutex()
    private val client by lazy { SupabaseWatchClient(config) }
    private val credentials by lazy { WatchSyncSessionStore(context, config.url) }
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    @Volatile private var session: WatchSyncAuthSession? = null
    private val _state = MutableStateFlow(WatchSyncUiState(configured = config.configured))
    val state = _state.asStateFlow()

    init {
        if (config.configured) {
            try { session = credentials.read() }
            catch (_: Exception) { _state.update { it.copy(error = WatchSyncError.CREDENTIALS) } }
        }
        history.setSyncOwner(session?.user?.id)
        refreshState()
        if (config.configured) {
            connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { refreshState(); requestSync() }
                override fun onLost(network: Network) { refreshState() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    refreshState(); if (online()) requestSync()
                }
            })
            scope.launch { preferences.ForceOfflineEnabled().collect {
                refreshState()
                if (it) cancelWork() else requestSync()
            } }
            scope.launch { history.localChanges.drop(1).debounce(2_000).collect { requestSync() } }
            scope.launch { AuthRepositoryProvider.getInstance(context).getActiveServerId()
                .distinctUntilChanged().collect { requestSync() } }
            schedulePeriodic()
            requestSync()
        }
    }

    private fun online(): Boolean = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    private fun successKey(id: String) = "success_${config.url.hashCode()}_$id"
    private fun blockedKey(id:String) = "auth_blocked_${config.url.hashCode()}_$id"
    private fun authBlocked():Boolean = session?.user?.id?.let {prefs.getBoolean(blockedKey(it),false)} == true
    private fun isEnabled(): Boolean = config.configured && prefs.getBoolean("enabled",false)
    private fun refreshState() {
        _state.update { it.copy(
            enabled = isEnabled(), email = session?.user?.email,
            offline = preferences.isForceOfflineEnabled() || !online(),
            lastSuccessMs = session?.user?.id?.let { id -> prefs.getLong(successKey(id),0) } ?: 0,
            error = if (authBlocked()) WatchSyncError.AUTH else it.error
        ) }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && (!config.configured || session == null)) return
        generation.incrementAndGet()
        check(prefs.edit().putBoolean("enabled",enabled).commit())
        _state.update { it.copy(syncing = false, error = null) }
        refreshState()
        if (enabled) { schedulePeriodic(); requestSync() } else cancelWork()
    }

    private fun cancelWork() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    private fun schedulePeriodic() {
        if (!isEnabled() || session == null || authBlocked() || preferences.isForceOfflineEnabled()) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchSyncWorker>(15,TimeUnit.MINUTES).setConstraints(constraints()).build())
    }

    fun requestSync() {
        if (!isEnabled() || session == null || authBlocked() || preferences.isForceOfflineEnabled()) return
        schedulePeriodic()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WatchSyncWorker>().setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
    }

    suspend fun signIn(email: String, password: String): Boolean = loginMutex.withLock {
        if (!config.configured || preferences.isForceOfflineEnabled()) return@withLock false
        _state.update { it.copy(signingIn = true, error = null, verificationSent = false) }
        try {
            val authenticated = client.signIn(email,password)
            bindAuthenticated(authenticated)
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            _state.update { it.copy(error = if (error is WatchSyncHttpException && error.status in listOf(400,401,422))
                WatchSyncError.AUTH else WatchSyncError.NETWORK) }
            false
        } finally { _state.update { it.copy(signingIn = false) } }
    }

    suspend fun signUp(email: String,password: String): Boolean = loginMutex.withLock {
        if (!config.configured || preferences.isForceOfflineEnabled()) return@withLock false
        _state.update { it.copy(signingIn=true,error=null,verificationSent=false) }
        try {
            val authenticated=client.signUp(email,password)
            if (authenticated==null) { _state.update {it.copy(verificationSent=true)};false }
            else { bindAuthenticated(authenticated);true }
        } catch (cancelled:CancellationException) {throw cancelled}
        catch (_:Exception) { _state.update {it.copy(error=WatchSyncError.AUTH)};false }
        finally { _state.update {it.copy(signingIn=false)} }
    }

    private suspend fun bindAuthenticated(authenticated: WatchSyncAuthSession) {
        setEnabled(false)
        mutex.withLock {
            credentials.save(authenticated)
            check(prefs.edit().remove(blockedKey(authenticated.user.id)).commit())
            session=authenticated
            history.setSyncOwner(authenticated.user.id)
        }
        setEnabled(true)
    }

    suspend fun disconnect() = loginMutex.withLock {
        setEnabled(false)
        mutex.withLock {
            credentials.clear()
            session = null
            history.setSyncOwner(null)
        }
        refreshState()
    }

    /** false asks WorkManager to retry. Auth failures wait for an explicit rebind. */
    suspend fun synchronize(): Boolean = mutex.withLock {
        val current = session ?: return@withLock true
        val owner = current.user.id
        val run = generation.get()
        suspend fun checkRun() {
            currentCoroutineContext().ensureActive()
            if (run != generation.get() || !isEnabled() || session?.user?.id != owner || preferences.isForceOfflineEnabled())
                throw CancellationException("Watch sync paused")
        }
        if (!isEnabled() || authBlocked() || preferences.isForceOfflineEnabled()) return@withLock true
        _state.update { it.copy(syncing = true, error = null) }
        try {
            checkRun()
            val media = MediaRepositoryProvider.getInstance(context)
            val localScope = media.getWatchHistoryScopeKey()
            if (localScope != null) {
                val cached = history.syncScope(owner,localScope)
                val identity = if (cached!=null) localScope to cached else media.getWatchSyncIdentity() ?: throw MissingIdentity()
                checkRun()
                history.bindScope(owner,identity.first,identity.second)
            }
            suspend fun refreshSession(force: Boolean = false): WatchSyncAuthSession {
                checkRun()
                val existing = session ?: throw CancellationException()
                if (!force && existing.expiresAt > System.currentTimeMillis()/1000+90) return existing
                val refreshed = client.refresh(existing.refreshToken)
                checkRun()
                check(refreshed.user.id == owner)
                credentials.save(refreshed)
                session = refreshed
                return refreshed
            }
            val fullyUploaded = try {
                val active = refreshSession()
                WatchSyncEngine(history,client.remote(active.accessToken)).synchronize(owner,::checkRun)
            } catch (error: WatchSyncHttpException) {
                if (error.status != 401) throw error
                val active = refreshSession(force = true)
                WatchSyncEngine(history,client.remote(active.accessToken)).synchronize(owner,::checkRun)
            }
            checkRun()
            if (!fullyUploaded) return@withLock false
            check(prefs.edit().putLong(successKey(owner),System.currentTimeMillis()).commit())
            refreshState()
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val reason = when {
                error is MissingIdentity -> WatchSyncError.IDENTITY
                error is WatchSyncHttpException && (error.status in listOf(401,403) ||
                    error.authentication && error.status in listOf(400,422)) -> WatchSyncError.AUTH
                error is WatchSyncHttpException -> WatchSyncError.SERVER
                else -> WatchSyncError.NETWORK
            }
            _state.update { it.copy(error = reason) }
            if (reason==WatchSyncError.AUTH) check(prefs.edit().putBoolean(blockedKey(owner),true).commit())
            reason == WatchSyncError.AUTH
        } finally { if (run==generation.get()) _state.update { it.copy(syncing = false) } }
    }

    private class MissingIdentity : Exception()
}

class WatchSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result =
        if (WatchSyncController.get(applicationContext).synchronize()) Result.success() else Result.retry()
}
