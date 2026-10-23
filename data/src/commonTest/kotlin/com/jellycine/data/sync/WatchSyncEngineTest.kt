package com.jellycine.data.sync

import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class WatchSyncEngineTest {
    private val session=WatchSyncSession("session","scope","video","name",1000,"device")
    private fun span(version:Long)=WatchSyncSpan("span","session","scope",1000,1000+version*100,version)
    private class Local : WatchSyncLocalStore {
        var batch=WatchSyncBatch()
        var position=0L
        var acknowledged=0
        val merged=mutableListOf<WatchSyncPage>()
        override suspend fun pending(ownerId:String,limit:Int)=batch
        override suspend fun acknowledge(ownerId:String,batch:WatchSyncBatch) {
            acknowledged++
            this.batch=WatchSyncBatch(this.batch.sessions.filter { it !in batch.sessions },
                this.batch.spans.filter { current -> batch.spans.none { it.id==current.id && it.version>=current.version } })
        }
        override suspend fun cursor(ownerId:String)=position
        override suspend fun merge(ownerId:String,page:WatchSyncPage) { merged+=page;position=page.nextCursor }
    }
    private class Remote : WatchSyncRemote {
        var afterPush:()->Unit={}
        var failPush=false
        val received=mutableMapOf<String,WatchSyncSpan>()
        val cursors=mutableListOf<Long>()
        var pages=mutableListOf(WatchSyncPage(nextCursor=0,hasMore=false))
        override suspend fun push(batch:WatchSyncBatch) {
            batch.spans.forEach { if ((received[it.id]?.version?:0)<it.version) received[it.id]=it }
            afterPush()
            if(failPush) throw IllegalStateException("Lost response after server commit")
        }
        override suspend fun pull(after:Long,limit:Int):WatchSyncPage { cursors+=after;return pages.removeAt(0) }
    }

    @Test fun lostResponseReplaysTheSameIdsWithoutDuplicateTime()=runBlocking {
        val local=Local().apply { batch=WatchSyncBatch(listOf(session),listOf(span(1))) }
        val remote=Remote().apply { failPush=true }
        assertFailsWith<IllegalStateException> { WatchSyncEngine(local,remote).synchronize("owner") }
        assertEquals(0,local.acknowledged)
        remote.failPush=false
        WatchSyncEngine(local,remote).synchronize("owner")
        assertEquals(1,remote.received.size)
        assertTrue(local.batch.isEmpty)
    }
    @Test fun extendingASpanDuringUploadDoesNotAcknowledgeTheNewVersion()=runBlocking {
        val local=Local().apply { batch=WatchSyncBatch(spans=listOf(span(1))) }
        val remote=Remote()
        remote.afterPush={ if(local.acknowledged==0) local.batch=WatchSyncBatch(spans=listOf(span(2))) }
        WatchSyncEngine(local,remote).synchronize("owner")
        assertEquals(2L,remote.received.getValue("span").version)
        assertEquals(2,local.acknowledged)
    }
    @Test fun downloadResumesFromTheLastCommittedPage()=runBlocking {
        val local=Local().apply { position=12 }
        val remote=Remote().apply { pages=mutableListOf(
            WatchSyncPage(sessions=listOf(session),nextCursor=14,hasMore=true),
            WatchSyncPage(spans=listOf(span(1)),nextCursor=20,hasMore=false)) }
        WatchSyncEngine(local,remote).synchronize("owner")
        assertEquals(listOf(12L,14L),remote.cursors)
        assertEquals(20L,local.position)
    }
    @Test fun pauseAfterServerCommitKeepsLocalBatchForSafeRetry()=runBlocking {
        val local=Local().apply { batch=WatchSyncBatch(spans=listOf(span(1))) }
        val remote=Remote()
        var enabled=true
        remote.afterPush={enabled=false}
        assertFailsWith<CancellationException> { WatchSyncEngine(local,remote).synchronize("owner") {
            if(!enabled) throw CancellationException()
        } }
        assertEquals(0,local.acknowledged)
        assertTrue(remote.cursors.isEmpty())
    }
    @Test fun nonAdvancingPageIsRejectedBeforeLocalCursorChanges()=runBlocking {
        val local=Local().apply {position=5}
        val remote=Remote().apply {pages=mutableListOf(WatchSyncPage(nextCursor=5,hasMore=true))}
        assertFailsWith<IllegalArgumentException> {WatchSyncEngine(local,remote).synchronize("owner")}
        assertEquals(5L,local.position)
        assertTrue(local.merged.isEmpty())
    }
}
