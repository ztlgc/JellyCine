package com.jellycine.app.ui.screens.player

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.VideoWatchSession
import com.jellycine.data.model.VideoWatchSpan
import java.util.UUID

/** Counts wall time with a monotonic clock, never with media position or playback speed. */
internal class VideoWatchTracker(
    private val monotonicMs: () -> Long,
    private val wallTimeMs: () -> Long,
    private val onOpen: (VideoWatchSession) -> Unit,
    private val onSpan: (VideoWatchSpan) -> Unit
) {
    private var session: VideoWatchSession? = null
    private var activeSinceMs: Long? = null
    private var intervalStartMs = 0L

    @Synchronized
    fun open(scopeKey: String, item: BaseItemDto, openedAtMs: Long, continueSession: Boolean = false) {
        if (!item.type.equals("Video", ignoreCase = true) || item.id.isNullOrBlank()) {
            close()
            return
        }
        if (continueSession && session?.scopeKey == scopeKey && session?.itemId == item.id) return
        close()
        session = VideoWatchSession(UUID.randomUUID().toString(), scopeKey, item.id!!, item.name.orEmpty(), openedAtMs)
            .also(onOpen)
    }

    @Synchronized
    fun setPlaying(playing: Boolean) {
        if (session == null || playing == (activeSinceMs != null)) return
        if (playing) {
            activeSinceMs = monotonicMs()
            intervalStartMs = wallTimeMs()
        } else {
            checkpoint()
            activeSinceMs = null
        }
    }

    @Synchronized
    fun checkpoint() {
        val current = session ?: return
        val started = activeSinceMs ?: return
        val now = monotonicMs()
        val elapsed = (now - started).coerceAtLeast(0L)
        if (elapsed > 0) {
            onSpan(VideoWatchSpan(current.id, intervalStartMs, intervalStartMs + elapsed))
            intervalStartMs += elapsed
        }
        activeSinceMs = now
    }

    @Synchronized
    fun close() {
        setPlaying(false)
        session = null
    }
}
