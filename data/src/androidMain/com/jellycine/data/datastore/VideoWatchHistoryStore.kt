package com.jellycine.data.datastore

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.jellycine.data.model.VideoWatchHistory
import com.jellycine.data.model.VideoWatchSession
import com.jellycine.data.model.VideoWatchSpan
import com.jellycine.data.sync.*
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** App-owned FIFO writer: leaving the player cannot cancel its final checkpoint. */
class VideoWatchHistoryStore private constructor(context: Context) : WatchSyncLocalStore {
    companion object {
        @Volatile private var instance: VideoWatchHistoryStore? = null

        fun getInstance(context: Context): VideoWatchHistoryStore = instance ?: synchronized(this) {
            instance ?: VideoWatchHistoryStore(context.applicationContext).also { instance = it }
        }
    }

    private val helper = WatchHistoryDatabase(context)
    private val operations = Channel<() -> Unit>(Channel.UNLIMITED)
    private val deviceId = context.getSharedPreferences("watch_sync_device", Context.MODE_PRIVATE).let {
        it.getString("id", null) ?: UUID.randomUUID().toString().also { id -> it.edit().putString("id", id).commit() }
    }
    @Volatile private var ownerId: String? = null
    private val _localChanges = MutableStateFlow(0L)
    val localChanges = _localChanges.asStateFlow()
    private val _contentRevision = MutableStateFlow(0L)
    val contentRevision = _contentRevision.asStateFlow()

    fun setSyncOwner(id: String?) { ownerId = id; _contentRevision.update { it+1 } }

    private suspend fun <T> query(block: (SQLiteDatabase) -> T): T {
        val result = CompletableDeferred<T>()
        operations.send {
            try { result.complete(block(helper.writableDatabase)) }
            catch (error: Exception) { result.completeExceptionally(error) }
        }
        return result.await()
    }

    private fun resolvedScope(db: SQLiteDatabase, owner: String?, local: String): String =
        if (owner == null) local else db.rawQuery(
            "SELECT scope_id FROM watch_scope_aliases WHERE owner_id=? AND local_scope=?", arrayOf(owner, local)
        ).use { if (it.moveToFirst()) "remote:${it.getString(0)}" else local }

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
        val owner = ownerId
        operations.trySend {
            val db = helper.writableDatabase
            val scope = resolvedScope(db, owner, session.scopeKey)
            db.insertOrThrow("watch_sessions", null, ContentValues().apply {
                put("id", session.id)
                put("scope_key", scope)
                put("item_id", session.itemId)
                put("name", session.name)
                put("opened_at", session.openedAtMs)
                put("owner_id", owner)
                put("sync_scope", scope.takeIf { it.startsWith("remote:") }?.removePrefix("remote:"))
                put("device_id", deviceId)
            })
            _localChanges.update { it+1 }
            _contentRevision.update { it+1 }
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
                    db.execSQL("UPDATE watch_spans SET end_at=?, version=version+1 WHERE id=?", arrayOf(span.endMs, lastId))
                } else {
                    db.insertOrThrow("watch_spans", null, ContentValues().apply {
                        put("session_id", span.sessionId)
                        put("start_at", span.startMs)
                        put("end_at", span.endMs)
                        put("sync_id", UUID.randomUUID().toString())
                    })
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            _localChanges.update { it+1 }
            _contentRevision.update { it+1 }
        }.getOrThrow()
    }

    suspend fun load(scopeKey: String, startMs: Long, endMs: Long): VideoWatchHistory {
        val owner = ownerId
        val result = CompletableDeferred<VideoWatchHistory>()
        // The same queue makes a just-closed player's final writes visible immediately.
        operations.send {
            try {
                val db = helper.readableDatabase
                val resolved = resolvedScope(db, owner, scopeKey)
                val sessions = db.rawQuery(
                    """SELECT s.id, s.item_id, s.name, s.opened_at FROM watch_sessions s
                        WHERE s.scope_key = ? AND (s.owner_id IS NULL OR s.owner_id = ?) AND ((s.opened_at >= ? AND s.opened_at < ?)
                        OR EXISTS (SELECT 1 FROM watch_spans p WHERE p.session_id = s.id
                        AND p.start_at < ? AND p.end_at > ?))""",
                    arrayOf(resolved, owner.orEmpty(), startMs.toString(), endMs.toString(), endMs.toString(), startMs.toString())
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
                        WHERE s.scope_key = ? AND (s.owner_id IS NULL OR s.owner_id = ?) AND p.start_at < ? AND p.end_at > ?""",
                    arrayOf(resolved, owner.orEmpty(), endMs.toString(), startMs.toString())
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

    suspend fun syncScope(owner: String, localScope: String): String? = query { db ->
        resolvedScope(db, owner, localScope).takeIf { it.startsWith("remote:") }?.removePrefix("remote:")
    }

    /** Move legacy URL-scoped history only after the media server identity has been verified. */
    suspend fun bindScope(owner: String, localScope: String, remoteScope: String) = query { db ->
        db.beginTransaction()
        try {
            db.execSQL("INSERT OR REPLACE INTO watch_scope_aliases(owner_id,local_scope,scope_id) VALUES(?,?,?)",
                arrayOf(owner, localScope, remoteScope))
            db.execSQL("""UPDATE watch_sessions SET scope_key=?,sync_scope=?,owner_id=?,
                device_id=CASE WHEN device_id='' THEN ? ELSE device_id END
                WHERE scope_key IN (?,?) AND (owner_id IS NULL OR owner_id=?)""",
                arrayOf("remote:$remoteScope", remoteScope, owner, deviceId, localScope, "remote:$remoteScope", owner))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        _contentRevision.update { it+1 }
    }

    override suspend fun pending(ownerId: String, limit: Int): WatchSyncBatch = query { db ->
        val sessions = db.rawQuery("""SELECT id,sync_scope,item_id,name,opened_at,device_id FROM watch_sessions
            WHERE owner_id=? AND sync_scope IS NOT NULL AND synced=0 ORDER BY opened_at,id LIMIT ?""",
            arrayOf(ownerId, limit.toString())).use { c -> buildList {
                while (c.moveToNext()) add(WatchSyncSession(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getString(5)))
            } }
        val selected = sessions.map { it.id }.toSet()
        val spans = db.rawQuery("""SELECT p.sync_id,p.session_id,s.sync_scope,p.start_at,p.end_at,p.version,s.synced
            FROM watch_spans p JOIN watch_sessions s ON s.id=p.session_id
            WHERE s.owner_id=? AND s.sync_scope IS NOT NULL AND p.version>p.synced_version
            AND (s.synced=1 OR s.id IN (SELECT id FROM watch_sessions WHERE owner_id=? AND sync_scope IS NOT NULL
                AND synced=0 ORDER BY opened_at,id LIMIT ?)) ORDER BY p.id LIMIT ?""",
            arrayOf(ownerId, ownerId, limit.toString(),limit.toString())).use { c -> buildList {
                while (c.moveToNext()) {
                    check(c.getInt(6)==1 || c.getString(1) in selected)
                    add(WatchSyncSpan(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4),c.getLong(5)))
                }
            } }
        WatchSyncBatch(sessions, spans)
    }

    override suspend fun acknowledge(ownerId: String, batch: WatchSyncBatch) = query { db ->
        db.beginTransaction()
        try {
            batch.sessions.forEach { db.execSQL("UPDATE watch_sessions SET synced=1 WHERE id=? AND owner_id=?", arrayOf(it.id,ownerId)) }
            batch.spans.forEach { db.execSQL("""UPDATE watch_spans SET synced_version=MAX(synced_version,?)
                WHERE sync_id=? AND session_id IN (SELECT id FROM watch_sessions WHERE owner_id=?)""",arrayOf<Any?>(it.version,it.id,ownerId)) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override suspend fun cursor(ownerId: String): Long = query { db ->
        db.rawQuery("SELECT cursor FROM watch_sync_cursors WHERE owner_id=?", arrayOf(ownerId))
            .use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    override suspend fun merge(ownerId: String, page: WatchSyncPage): Unit = query { db ->
        db.beginTransaction()
        try {
            page.sessions.forEach { s ->
                db.execSQL("""INSERT OR IGNORE INTO watch_sessions
                    (id,scope_key,item_id,name,opened_at,owner_id,sync_scope,device_id,synced) VALUES(?,?,?,?,?,?,?,?,1)""",
                    arrayOf<Any?>(s.id,"remote:${s.scopeId}",s.itemId,s.name,s.openedAtMs,ownerId,s.scopeId,s.deviceId))
                db.rawQuery("SELECT owner_id,sync_scope FROM watch_sessions WHERE id=?",arrayOf(s.id)).use {
                    check(it.moveToFirst() && it.getString(0)==ownerId && it.getString(1)==s.scopeId) { "Watch session ownership mismatch" }
                }
            }
            page.spans.forEach { p ->
                require(p.endMs>p.startMs && p.version>0)
                // Parent may have been downloaded on an earlier page. Never attach to another account.
                db.rawQuery("SELECT owner_id,sync_scope FROM watch_sessions WHERE id=?",arrayOf(p.sessionId)).use {
                    check(it.moveToFirst() && it.getString(0)==ownerId && it.getString(1)==p.scopeId) { "Missing watch session" }
                }
                db.execSQL("""INSERT OR IGNORE INTO watch_spans(sync_id,session_id,start_at,end_at,version,synced_version)
                    VALUES(?,?,?,?,?,?)""",arrayOf<Any?>(p.id,p.sessionId,p.startMs,p.endMs,p.version,p.version))
                db.rawQuery("SELECT session_id,start_at FROM watch_spans WHERE sync_id=?",arrayOf(p.id)).use {
                    check(it.moveToFirst() && it.getString(0)==p.sessionId && it.getLong(1)==p.startMs) { "Watch span identity mismatch" }
                }
                db.execSQL("""UPDATE watch_spans SET end_at=?,version=?,synced_version=?
                    WHERE sync_id=? AND session_id=? AND start_at=? AND version<=?""",
                    arrayOf<Any?>(p.endMs,p.version,p.version,p.id,p.sessionId,p.startMs,p.version))
            }
            db.execSQL("INSERT OR REPLACE INTO watch_sync_cursors(owner_id,cursor) VALUES(?,?)", arrayOf<Any?>(ownerId,page.nextCursor))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        _contentRevision.update { it+1 }
        Unit
    }
}

internal class WatchHistoryDatabase(context: Context) : SQLiteOpenHelper(context, "video_watch_history.db", null, 2) {
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
        addSyncSchema(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) addSyncSchema(db)
    }

    private fun addSyncSchema(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE watch_sessions ADD COLUMN owner_id TEXT")
        db.execSQL("ALTER TABLE watch_sessions ADD COLUMN sync_scope TEXT")
        db.execSQL("ALTER TABLE watch_sessions ADD COLUMN device_id TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE watch_sessions ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE watch_spans ADD COLUMN sync_id TEXT")
        db.execSQL("ALTER TABLE watch_spans ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE watch_spans ADD COLUMN synced_version INTEGER NOT NULL DEFAULT 0")
        db.rawQuery("SELECT id FROM watch_spans", null).use { c ->
            while (c.moveToNext()) db.execSQL("UPDATE watch_spans SET sync_id=? WHERE id=?",arrayOf<Any?>(UUID.randomUUID().toString(),c.getLong(0)))
        }
        db.execSQL("CREATE UNIQUE INDEX watch_spans_sync_id ON watch_spans(sync_id)")
        db.execSQL("CREATE INDEX watch_sessions_sync_pending ON watch_sessions(owner_id,synced,opened_at,id)")
        db.execSQL("CREATE TABLE watch_scope_aliases(owner_id TEXT NOT NULL,local_scope TEXT NOT NULL,scope_id TEXT NOT NULL,PRIMARY KEY(owner_id,local_scope))")
        db.execSQL("CREATE TABLE watch_sync_cursors(owner_id TEXT PRIMARY KEY NOT NULL,cursor INTEGER NOT NULL DEFAULT 0)")
    }
}
