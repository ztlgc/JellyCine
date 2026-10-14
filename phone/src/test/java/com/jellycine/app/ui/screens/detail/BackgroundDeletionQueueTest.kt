package com.jellycine.app.ui.screens.detail

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BackgroundDeletionQueueTest {
    @Test fun navigationCancellationDoesNotCancelTheApplicationRequest() = runBlocking {
        val appJob = SupervisorJob()
        val queue = BackgroundDeletionQueue(CoroutineScope(appJob + Dispatchers.Unconfined))
        val gate = CompletableDeferred<Unit>()
        val completion = CompletableDeferred<Result<Unit>>()
        val routeJob = Job()
        var accepted = false
        CoroutineScope(routeJob + Dispatchers.Unconfined).launch {
            accepted = queue.submit("server:item", { gate.await(); Result.success(Unit) }, { completion.complete(it) })
            awaitCancellation()
        }
        assertTrue(accepted)
        assertFalse(queue.submit("server:item", { error("Duplicate request") }, {}))
        routeJob.cancel()
        gate.complete(Unit)
        assertTrue(withTimeout(1000) { completion.await() }.isSuccess)
        appJob.cancel()
    }

    @Test fun serverFailureIsReportedAndAllowsRetry() = runBlocking {
        val appJob = SupervisorJob()
        val queue = BackgroundDeletionQueue(CoroutineScope(appJob + Dispatchers.Unconfined))
        val failure = CompletableDeferred<Result<Unit>>()
        val success = CompletableDeferred<Result<Unit>>()
        assertTrue(queue.submit("server:item", { Result.failure(IllegalStateException("HTTP 403")) }, { failure.complete(it) }))
        assertEquals("HTTP 403", failure.await().exceptionOrNull()?.message)
        assertTrue(queue.submit("server:item", { Result.success(Unit) }, { success.complete(it) }))
        assertTrue(success.await().isSuccess)
        appJob.cancel()
    }
}
