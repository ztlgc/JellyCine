package com.jellycine.app.screentime

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jellycine.data.datastore.VideoWatchHistoryStore
import com.jellycine.data.model.VideoWatchSession
import com.jellycine.data.model.VideoWatchSpan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class VideoWatchHistoryStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = VideoWatchHistoryStore.getInstance(context)

    @Test fun queuedExitCheckpointIsVisibleAndDurableBeforeTheStatisticsQueryReturns() = runBlocking {
        val id = UUID.randomUUID().toString()
        val scope = "test-server|$id"
        val start = System.currentTimeMillis()
        store.recordOpen(VideoWatchSession(id, scope, "video", "短视频", start))
        store.recordSpan(VideoWatchSpan(id, start, start + 450))
        store.recordSpan(VideoWatchSpan(id, start + 450, start + 900))
        val history = store.load(scope, start, start + 1000)
        assertEquals(1, history.sessions.size)
        assertEquals(900L, history.spans.sumOf { it.endMs - it.startMs })
        assertTrue(store.load("other-server|$id", start, start + 1000).sessions.isEmpty())
        // Read through a separate connection to prove the history is on disk, not an in-memory cache.
        SQLiteDatabase.openDatabase(context.getDatabasePath("video_watch_history.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT name FROM watch_sessions WHERE id = ?", arrayOf(id)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("短视频", cursor.getString(0))
            }
            db.rawQuery("SELECT SUM(end_at - start_at) FROM watch_spans WHERE session_id = ?", arrayOf(id)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(900L, cursor.getLong(0))
            }
        }
    }

    @Test fun queryRetainsAnOlderSessionWithPlaybackInsideTheRequestedPeriod() = runBlocking {
        val id = UUID.randomUUID().toString()
        val scope = "test-server|$id"
        val start = System.currentTimeMillis()
        store.recordOpen(VideoWatchSession(id, scope, "video", "跨时段", start - 60_000))
        store.recordSpan(VideoWatchSpan(id, start - 1000, start + 500))
        val history = store.load(scope, start, start + 1000)
        assertEquals(id, history.sessions.single().id)
        assertEquals(start + 500, history.spans.single().endMs)
        assertTrue(store.load(scope, start + 1000, start + 2000).sessions.isEmpty())
    }
}
