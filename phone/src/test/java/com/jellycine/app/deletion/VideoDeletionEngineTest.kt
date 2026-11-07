package com.jellycine.app.deletion

import com.jellycine.data.repository.VideoDeletionOutcome
import com.jellycine.data.repository.VideoDeletionTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class VideoDeletionEngineTest {
    @get:Rule val temporary = TemporaryFolder()
    private val target = VideoDeletionTarget("server-a", "https://a.example", "user-a", "JELLYFIN", "video", "Video")
    private val key get() = deletionKey(target.serverId, target.itemId)
    private fun file() = File(temporary.newFolder(), "deletions.json")

    @Test fun tenRetransmissionsAfterTheInitialRequestRemainTheLimitAcrossRestarts() = runBlocking {
        val file = file()
        VideoDeletionStore(file).enqueue(target)
        var sent = 0
        repeat(11) { index ->
            val store = VideoDeletionStore(file)
            val engine = VideoDeletionEngine(store) { sent++; VideoDeletionOutcome.RETRY }
            val expected = if (index < 10) VideoDeletionRunResult.RETRY else VideoDeletionRunResult.EXHAUSTED
            assertEquals(expected, engine.attempt(key))
            assertEquals(index + 1, VideoDeletionStore(file).get(key)?.attempts)
        }
        val reopened = VideoDeletionStore(file)
        // Repeated taps and startup recovery must not reset exhausted records.
        reopened.enqueue(target)
        val engine = VideoDeletionEngine(reopened) { sent++; VideoDeletionOutcome.RETRY }
        repeat(3) { assertEquals(VideoDeletionRunResult.EXHAUSTED, engine.attempt(key)) }
        assertEquals(11, sent)
        assertEquals(target, reopened.all().single().target)
        assertTrue(reopened.all().single().exhausted)
    }

    @Test fun completedDeletionStopsSendingAndRetainsItsDurableHideRecord() = runBlocking {
        val file = file()
        val store = VideoDeletionStore(file)
        store.enqueue(target)
        var sent = 0
        val engine = VideoDeletionEngine(store) { sent++; VideoDeletionOutcome.COMPLETE }
        assertEquals(VideoDeletionRunResult.COMPLETE, engine.attempt(key))
        val reopened = VideoDeletionStore(file)
        reopened.enqueue(target)
        assertEquals(VideoDeletionRunResult.COMPLETE, VideoDeletionEngine(reopened) {
            sent++; VideoDeletionOutcome.RETRY
        }.attempt(key))
        assertEquals(1, sent)
        assertTrue(reopened.get(key)!!.completed)
        assertFalse(reopened.get(key)!!.pending)
        assertEquals(target.itemId, reopened.all().single().target.itemId)
    }

    @Test fun successOnTheLastAllowedRetransmissionIsCompleteRatherThanExhausted() = runBlocking {
        val store = VideoDeletionStore(file())
        store.enqueue(target)
        var sent = 0
        val engine = VideoDeletionEngine(store) {
            sent++
            if (sent == 11) VideoDeletionOutcome.COMPLETE else VideoDeletionOutcome.RETRY
        }
        repeat(10) { assertEquals(VideoDeletionRunResult.RETRY, engine.attempt(key)) }
        assertEquals(VideoDeletionRunResult.COMPLETE, engine.attempt(key))
        assertFalse(store.get(key)!!.exhausted)
        assertTrue(store.get(key)!!.completed)
    }

    @Test fun missingLocalCredentialsDoNotConsumeNetworkRetransmissions() = runBlocking {
        val store = VideoDeletionStore(file())
        store.enqueue(target)
        val unavailable = VideoDeletionEngine(store) { VideoDeletionOutcome.SESSION_UNAVAILABLE }
        repeat(15) { assertEquals(VideoDeletionRunResult.RETRY, unavailable.attempt(key)) }
        assertEquals(0, store.get(key)?.attempts)
        assertEquals(VideoDeletionRunResult.COMPLETE,
            VideoDeletionEngine(store) { VideoDeletionOutcome.COMPLETE }.attempt(key))
        assertEquals(1, store.get(key)?.attempts)
    }

    @Test fun thrownNetworkFailureConsumesAnAttemptAndCanBeRetried() = runBlocking {
        val store = VideoDeletionStore(file())
        store.enqueue(target)
        assertEquals(VideoDeletionRunResult.RETRY, VideoDeletionEngine(store) { throw IOException("offline") }.attempt(key))
        assertEquals(1, store.get(key)?.attempts)
        assertEquals(VideoDeletionRunResult.COMPLETE, VideoDeletionEngine(store) { VideoDeletionOutcome.COMPLETE }.attempt(key))
        assertEquals(2, store.get(key)?.attempts)
    }

    @Test fun cancellationAfterSendingPreservesTheReservedCountOnDisk() = runBlocking {
        val file = file()
        val store = VideoDeletionStore(file)
        store.enqueue(target)
        try {
            VideoDeletionEngine(store) { throw CancellationException("process stopped") }.attempt(key)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        val reopened = VideoDeletionStore(file)
        assertEquals(1, reopened.get(key)?.attempts)
        assertEquals(VideoDeletionRunResult.COMPLETE, VideoDeletionEngine(reopened) { VideoDeletionOutcome.COMPLETE }.attempt(key))
        assertEquals(2, reopened.get(key)?.attempts)
    }

    @Test fun concurrentRequestsForOneVideoCannotSendAgainAfterSuccess() = runBlocking {
        val store = VideoDeletionStore(file())
        store.enqueue(target)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var sent = 0
        val engine = VideoDeletionEngine(store) {
            sent++
            started.complete(Unit)
            finish.await()
            VideoDeletionOutcome.COMPLETE
        }
        val first = async { engine.attempt(key) }
        started.await()
        val second = async { engine.attempt(key) }
        finish.complete(Unit)
        assertEquals(VideoDeletionRunResult.COMPLETE, first.await())
        assertEquals(VideoDeletionRunResult.COMPLETE, second.await())
        assertEquals(1, sent)
    }

    @Test fun sameVideoIdOnAnotherServerHasItsOwnRecordAndLimit() = runBlocking {
        val store = VideoDeletionStore(file())
        store.enqueue(target)
        val other = target.copy(serverId = "server-b", serverUrl = "https://b.example", userId = "user-b")
        store.enqueue(other)
        val seen = mutableListOf<VideoDeletionTarget>()
        val engine = VideoDeletionEngine(store) { seen += it; VideoDeletionOutcome.COMPLETE }
        engine.attempt(key)
        assertEquals(0, store.get(deletionKey(other.serverId, other.itemId))?.attempts)
        engine.attempt(deletionKey(other.serverId, other.itemId))
        assertEquals(listOf(target, other), seen)
    }

    @Test fun anUnwritableQueueDoesNotAcceptALocalDeletion() {
        val file = file()
        val store = VideoDeletionStore(file)
        file.mkdirs() // A directory at the destination makes the atomic rename fail.
        try {
            store.enqueue(target)
            fail("Persistence failure must be reported")
        } catch (_: IOException) { }
        assertTrue(store.all().isEmpty())
    }
}
