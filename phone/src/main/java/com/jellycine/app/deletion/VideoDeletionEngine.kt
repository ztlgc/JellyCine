package com.jellycine.app.deletion

import com.jellycine.data.repository.VideoDeletionOutcome
import com.jellycine.data.repository.VideoDeletionTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal enum class VideoDeletionRunResult { COMPLETE, RETRY, EXHAUSTED }

/** Shared by every worker: duplicate submissions cannot send concurrent requests for one item. */
internal class VideoDeletionEngine(
    private val store: VideoDeletionStore,
    private val send: suspend (VideoDeletionTarget) -> VideoDeletionOutcome
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun attempt(key: String): VideoDeletionRunResult = locks.getOrPut(key) { Mutex() }.withLock {
        val reserved = store.reserveAttempt(key) ?: return@withLock if (store.get(key)?.exhausted == true) {
            VideoDeletionRunResult.EXHAUSTED
        } else VideoDeletionRunResult.COMPLETE
        val outcome = try {
            send(reserved.target)
        } catch (cancelled: CancellationException) {
            // Retain the reserved count: a cancelled request may already have reached the server.
            throw cancelled
        } catch (_: Exception) {
            VideoDeletionOutcome.RETRY
        }
        val updated = store.finishAttempt(key, outcome)
        when {
            updated.completed -> VideoDeletionRunResult.COMPLETE
            updated.exhausted -> VideoDeletionRunResult.EXHAUSTED
            else -> VideoDeletionRunResult.RETRY
        }
    }
}
