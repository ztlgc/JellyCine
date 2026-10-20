package com.jellycine.data.datastore

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.jellycine.data.model.VideoWatchHistory
import com.jellycine.data.model.VideoWatchSession
import com.jellycine.data.model.VideoWatchSpan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** App-owned FIFO writer: leaving the player cannot cancel its final checkpoint. */
class VideoWatchHistoryStore private constructor(context: Context) {
    companion object {
        @Volatile private var instance: VideoWatchHistoryStore? = null

        fun getInstance(context: Context): VideoWatchHistoryStore = instance ?: synchronized(this) {
            instance ?: VideoWatchHistoryStore(context.applicationContext).also { instance = it }
        }
    }

    private val helper = WatchHistoryDatabase(context)
    private val operations = Channel<() -> Unit>(Channel.UNLIMITED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (operation in operations) {
                try {
                    operation()
                } catch (error: Exception) {
                    Log.e("VideoWatchHistory", "Unable to persist video watch history", error)
                }
            }
        }
    }

    fun recordOpen(session: VideoWatchSession) {
        operations.trySend {
            helper.writableDatabase.insertOrThrow("watch_sessions", null, ContentValues().apply {
                put("id", session.id)
                put("scope_key", session.scopeKey)
                put("item_id", session.itemId)
                put("name", session.name)
                put("opened_at", session.openedAtMs)
            })
        }.getOrThrow()
    }

    fun recordSpan(span: VideoWatchSpan) {
        if (span.endMs <= span.startMs) return
        operations.trySend {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                // Merge adjacent checkpoints; pauses remain separate intervals.
                val lastId = db.rawQuery(
                    "SELECT id FROM watch_spans WHERE session_id = ? AND end_at = ? ORDER BY id DESC LIMIT 1",
                    arrayOf(span.sessionId, span.startMs.toString())
                ).use { if (it.moveToFirst()) it.getLong(0) else null }
                if (lastId != null) {
                    db.update("watch_spans", ContentValues().apply { put("end_at", span.endMs) },
                        "id = ?", arrayOf(lastId.toString()))
                } else {
                    db.insertOrThrow("watch_spans", null, ContentValues().apply {
                        put("session_id", span.sessionId)
                        put("start_at", span.startMs)
                        put("end_at", span.endMs)
                    })
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }.getOrThrow()
    }

    suspend fun load(scopeKey: String, startMs: Long, endMs: Long): VideoWatchHistory {
        val result = CompletableDeferred<VideoWatchHistory>()
        // The same queue makes a just-closed player's final writes visible immediately.
        operations.send {
            try {
                val db = helper.readableDatabase
                val sessions = db.rawQuery(
                    """SELECT s.id, s.item_id, s.name, s.opened_at FROM watch_sessions s
                        WHERE s.scope_key = ? AND ((s.opened_at >= ? AND s.opened_at < ?)
                        OR EXISTS (SELECT 1 FROM watch_spans p WHERE p.session_id = s.id
                        AND p.start_at < ? AND p.end_at > ?))""",
                    arrayOf(scopeKey, startMs.toString(), endMs.toString(), endMs.toString(), startMs.toString())
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(VideoWatchSession(
                            cursor.getString(0), scopeKey, cursor.getString(1), cursor.getString(2), cursor.getLong(3)
                        ))
                    }
                }
                val spans = db.rawQuery(
                    """SELECT p.session_id, p.start_at, p.end_at FROM watch_spans p
                        JOIN watch_sessions s ON s.id = p.session_id
                        WHERE s.scope_key = ? AND p.start_at < ? AND p.end_at > ?""",
                    arrayOf(scopeKey, endMs.toString(), startMs.toString())
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(VideoWatchSpan(cursor.getString(0), cursor.getLong(1), cursor.getLong(2)))
                    }
                }
                result.complete(VideoWatchHistory(sessions, spans))
            } catch (error: Exception) {
                result.completeExceptionally(error)
            }
        }
        return result.await()
    }
}

private class WatchHistoryDatabase(context: Context) : SQLiteOpenHelper(context, "video_watch_history.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE watch_sessions (
            id TEXT PRIMARY KEY NOT NULL, scope_key TEXT NOT NULL, item_id TEXT NOT NULL,
            name TEXT NOT NULL, opened_at INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX watch_sessions_scope_date ON watch_sessions(scope_key, opened_at)")
        db.execSQL("""CREATE TABLE watch_spans (
            id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL REFERENCES watch_sessions(id),
            start_at INTEGER NOT NULL, end_at INTEGER NOT NULL CHECK (end_at > start_at))""")
        db.execSQL("CREATE INDEX watch_spans_session_time ON watch_spans(session_id, end_at, start_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
