package com.jellycine.logging

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Bounded, local playback diagnostics, retained across process restarts. No playback-thread IO. */
object PlaybackLogStore {
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "PlaybackDiagnosticsIO").apply { isDaemon = true }
    }

    fun log(context: Context, tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        AppLogger.log(level, tag, message)
        val directory = File(context.applicationContext.filesDir, "playback_diagnostics")
        val line = "${Instant.now()} ${level.label}/$tag: $message"
        worker.execute {
            runCatching { RotatingPlaybackLog(directory).append(line) }
                .onFailure {
                    AppLogger.log(LogLevel.WARN, "PlaybackLogStore", "Playback log write failed: ${it.javaClass.simpleName}")
                }
        }
    }

    suspend fun read(context: Context): String {
        val directory = File(context.applicationContext.filesDir, "playback_diagnostics")
        // Enqueue the read after pending writes so an immediate export includes the last event.
        return suspendCancellableCoroutine { continuation ->
            worker.execute {
                val result = runCatching { RotatingPlaybackLog(directory).read() }
                    .getOrElse { "Playback diagnostics unavailable: ${it.javaClass.simpleName}" }
                continuation.resume(result)
            }
        }
    }
}
