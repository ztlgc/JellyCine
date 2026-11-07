package com.jellycine.app.deletion

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jellycine.app.ui.screens.dashboard.media.LibraryCatalogCache
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.repository.VideoDeletionTarget
import com.jellycine.shared.R
import com.jellycine.shared.playback.UserDataRefreshSignals
import com.jellycine.shared.preferences.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal class VideoDeletionController private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = Preferences(context)
    private val repository by lazy { MediaRepositoryProvider.getInstance(context) }
    private val store = runCatching { VideoDeletionStore(File(context.filesDir, "video_deletions.json")) }
        .onFailure { Log.e(TAG, "Could not load video deletion records", it) }
    private val engine by lazy { VideoDeletionEngine(store.getOrThrow(), repository::deleteVideo) }

    init {
        // Restore before the first screen or cache is read; a network refresh must never resurrect an item.
        val records = store.getOrNull()?.all().orEmpty()
        UserDataRefreshSignals.restoreDeletedItems(records.groupBy { it.target.serverId }
            .mapValues { (_, deletions) -> deletions.map { it.target.itemId }.toSet() })
        scope.launch {
            records.forEach { hideFromCaches(it.target) }
            schedulePending()
        }
        scope.launch {
            AuthRepositoryProvider.getInstance(context).observeActiveSession().collect { schedulePending() }
        }
        scope.launch {
            preferences.ForceOfflineEnabled().collect { offline -> if (!offline) schedulePending() }
        }
    }

    /** Return after durable acceptance and local hiding, without waiting for the remote request. */
    suspend fun submit(target: VideoDeletionTarget): Boolean = withContext(Dispatchers.IO) {
        val record = try {
            store.getOrThrow().enqueue(target)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Could not persist video deletion", error)
            return@withContext false
        }
        UserDataRefreshSignals.notifyItemDeleted(target.serverId, target.itemId)
        // Schedule independently of the detail route, including when navigation cancels its coroutine.
        scope.launch {
            if (record.pending) schedule(record)
            hideFromCaches(target)
        }
        true
    }

    private suspend fun hideFromCaches(target: VideoDeletionTarget) {
        try {
            repository.removeVideoFromLocalSnapshot(target)
            LibraryCatalogCache.getInstance(context.cacheDir).removeItem(target.serverId, target.itemId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Could not remove video from local caches; tombstone remains active", error)
        }
    }

    private fun schedulePending() {
        if (preferences.isForceOfflineEnabled()) return
        store.getOrNull()?.all()?.filter { it.pending }?.forEach(::schedule)
    }

    private fun schedule(record: VideoDeletionRecord) {
        try {
            val work = OneTimeWorkRequestBuilder<VideoDeletionWorker>()
                .setInputData(workDataOf(KEY to record.key))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            val digest = MessageDigest.getInstance("SHA-256").digest(record.key.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            WorkManager.getInstance(context).enqueueUniqueWork("video-deletion-$digest", ExistingWorkPolicy.KEEP, work)
        } catch (error: Exception) {
            // The durable record will be scheduled again on startup, account change or leaving offline mode.
            Log.e(TAG, "Could not schedule video deletion", error)
        }
    }

    suspend fun attempt(key: String): VideoDeletionRunResult {
        if (preferences.isForceOfflineEnabled()) return VideoDeletionRunResult.RETRY
        val result = engine.attempt(key)
        if (result == VideoDeletionRunResult.EXHAUSTED) {
            val record = store.getOrThrow().get(key)
            Log.w(TAG, "Video deletion reached its retry limit")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.video_detail_delete_retry_limit,
                    record?.target?.name.orEmpty(), VideoDeletionRecord.MAX_RETRIES), Toast.LENGTH_LONG).show()
            }
        }
        return result
    }

    companion object {
        private const val TAG = "VideoDeletion"
        internal const val KEY = "deletion_key"
        @Volatile private var instance: VideoDeletionController? = null

        fun get(context: Context): VideoDeletionController = instance ?: synchronized(this) {
            instance ?: VideoDeletionController(context.applicationContext).also { instance = it }
        }
    }
}

class VideoDeletionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val key = inputData.getString(VideoDeletionController.KEY) ?: return Result.failure()
        return try {
            when (VideoDeletionController.get(applicationContext).attempt(key)) {
                VideoDeletionRunResult.RETRY -> Result.retry()
                VideoDeletionRunResult.COMPLETE, VideoDeletionRunResult.EXHAUSTED -> Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("VideoDeletion", "Could not process persistent video deletion", error)
            Result.retry()
        }
    }
}
