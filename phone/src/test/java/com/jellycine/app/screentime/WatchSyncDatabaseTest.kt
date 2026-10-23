package com.jellycine.app.screentime

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jellycine.data.datastore.VideoWatchHistoryStore
import com.jellycine.data.model.VideoWatchSession
import com.jellycine.data.model.VideoWatchSpan
import com.jellycine.data.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE,application=Application::class)
class WatchSyncDatabaseTest {
    private lateinit var context: Context
    private var store: VideoWatchHistoryStore?=null
    private val owner="owner-a"
    private val remoteScope="a".repeat(64)
    private val legacy="http://media.local:8096|media-user"
    private val id=UUID.randomUUID().toString()

    @Before fun setup() {
        context=RuntimeEnvironment.getApplication()
        context.deleteDatabase("video_watch_history.db")
    }
    private fun open():VideoWatchHistoryStore = store ?: VideoWatchHistoryStore::class.java
        .getDeclaredConstructor(Context::class.java).apply { isAccessible=true }.newInstance(context).also { store=it }
    @After fun close() {
        store?.let { instance ->
            VideoWatchHistoryStore::class.java.getDeclaredField("helper").apply {isAccessible=true}
                .get(instance).let { (it as SQLiteOpenHelper).close() }
        }
    }
    private fun record(s:VideoWatchHistoryStore=open()) {
        s.recordOpen(VideoWatchSession(id,legacy,"video","旅行",1000))
        s.recordSpan(VideoWatchSpan(id,1000,1500))
    }

    @Test fun versionOneDatabaseUpgradesWithoutLosingHistoryOrChangingSyncIds()=runBlocking {
        context.getDatabasePath("video_watch_history.db").parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("video_watch_history.db"),null).use { db ->
            db.execSQL("CREATE TABLE watch_sessions(id TEXT PRIMARY KEY,scope_key TEXT,item_id TEXT,name TEXT,opened_at INTEGER)")
            db.execSQL("CREATE TABLE watch_spans(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id TEXT,start_at INTEGER,end_at INTEGER)")
            db.execSQL("INSERT INTO watch_sessions VALUES(?,?,?,?,?)",arrayOf<Any>(id,legacy,"video","旅行",1000))
            db.execSQL("INSERT INTO watch_spans(session_id,start_at,end_at) VALUES(?,?,?)",arrayOf<Any>(id,1000,1500))
            db.version=1
        }
        val s=open()
        assertEquals(500L,s.load(legacy,0,2000).spans.sumOf {it.endMs-it.startMs})
        s.setSyncOwner(owner)
        s.bindScope(owner,legacy,remoteScope)
        val first=s.pending(owner,200)
        assertEquals(1,first.sessions.size)
        assertEquals(500L,first.spans.single().endMs-first.spans.single().startMs)
        close();store=null
        val reopened=open()
        reopened.setSyncOwner(owner)
        assertEquals(first.spans.single().id,reopened.pending(owner,200).spans.single().id)
        assertEquals(2,SQLiteDatabase.openDatabase(context.getDatabasePath("video_watch_history.db").path,null,
            SQLiteDatabase.OPEN_READONLY).use {it.version})
    }

    @Test fun oldAcknowledgementCannotMarkAnExtendedPlaybackSpanAsUploaded()=runBlocking {
        val s=open();s.setSyncOwner(owner);record(s);s.bindScope(owner,legacy,remoteScope)
        val sent=s.pending(owner,200)
        s.recordSpan(VideoWatchSpan(id,1500,1800))
        s.acknowledge(owner,sent)
        val newer=s.pending(owner,200)
        assertTrue(newer.sessions.isEmpty())
        assertEquals(sent.spans.single().id,newer.spans.single().id)
        assertEquals(2L,newer.spans.single().version)
        assertEquals(1800L,newer.spans.single().endMs)
        s.acknowledge(owner,newer)
        assertTrue(s.pending(owner,200).isEmpty)
    }

    @Test fun olderServerPageCannotOverwriteNewLocalDuration()=runBlocking {
        val s=open();s.setSyncOwner(owner);record(s);s.bindScope(owner,legacy,remoteScope)
        val old=s.pending(owner,200)
        s.recordSpan(VideoWatchSpan(id,1500,2000))
        s.merge(owner,WatchSyncPage(old.sessions,old.spans,10,false))
        assertEquals(1000L,s.load(legacy,0,3000).spans.sumOf {it.endMs-it.startMs})
        assertEquals(2L,s.pending(owner,200).spans.single().version)
    }

    @Test fun restoredRecordsAreNotReuploadedAndAliasesShareOneHistory()=runBlocking {
        val s=open();s.setSyncOwner(owner)
        val session=WatchSyncSession(id,remoteScope,"video","旅行",1000,UUID.randomUUID().toString())
        val span=WatchSyncSpan(UUID.randomUUID().toString(),id,remoteScope,1000,2000,1)
        val page=WatchSyncPage(listOf(session),listOf(span),20,false)
        s.merge(owner,page);s.merge(owner,page)
        s.bindScope(owner,legacy,remoteScope)
        val otherUrl="https://example.org/emby|media-user"
        s.bindScope(owner,otherUrl,remoteScope)
        assertEquals(1,s.load(legacy,0,3000).sessions.size)
        assertEquals(1000L,s.load(otherUrl,0,3000).spans.sumOf {it.endMs-it.startMs})
        assertTrue(s.pending(owner,200).isEmpty)
        assertEquals(20L,s.cursor(owner))
    }

    @Test fun accountSwitchDoesNotExposeOrClaimAnotherAccountsHistory()=runBlocking {
        val s=open();s.setSyncOwner(owner);record(s);s.bindScope(owner,legacy,remoteScope)
        s.setSyncOwner("owner-b");s.bindScope("owner-b",legacy,remoteScope)
        assertTrue(s.load(legacy,0,3000).sessions.isEmpty())
        assertTrue(s.pending("owner-b",200).isEmpty)
        s.setSyncOwner(owner)
        assertEquals(1,s.load(legacy,0,3000).sessions.size)
    }

    @Test fun invalidPageRollsBackRecordsAndCursorTogether()=runBlocking {
        val s=open();s.setSyncOwner(owner)
        val session=WatchSyncSession(id,remoteScope,"video","旅行",1000,UUID.randomUUID().toString())
        val bad=WatchSyncSpan(UUID.randomUUID().toString(),"missing",remoteScope,1000,1500,1)
        try {s.merge(owner,WatchSyncPage(listOf(session),listOf(bad),100,false));fail("Invalid page accepted")}
        catch (_:IllegalStateException) {}
        assertEquals(0L,s.cursor(owner))
        s.bindScope(owner,legacy,remoteScope)
        assertTrue(s.load(legacy,0,3000).sessions.isEmpty())
    }
}
