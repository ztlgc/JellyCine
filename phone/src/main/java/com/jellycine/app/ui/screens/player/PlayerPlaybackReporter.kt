package com.jellycine.app.ui.screens.player

import android.util.Log
import com.jellycine.data.repository.MediaRepository
import com.jellycine.shared.playback.UserDataRefreshSignals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class PlayerPlaybackReporter(
    private val mediaRepository: MediaRepository,
    private val scope: CoroutineScope,
    private val positionProvider: () -> Long,
    private val isPausedProvider: () -> Boolean
) {

    companion object {
        private const val TAG = "PlayerPlaybackReporter"
        private const val TICKS_PER_MILLISECOND = 10_000L
        private const val PROGRESS_REPORT_INTERVAL_MS = 15_000L
    }

    private var session = PlaybackSessionContext()
    private var progressReportingJob: Job? = null
    private var hasReportedStart: Boolean = false

    fun updateSession(newSession: PlaybackSessionContext) {
        session = newSession
    }

    fun hasReportedStart(): Boolean = hasReportedStart

    fun reset() {
        progressReportingJob?.cancel()
        progressReportingJob = null
        hasReportedStart = false
        session = PlaybackSessionContext()
    }

    fun reportPlaybackStatus() {
        val sessionSnapshot = session
        val mediaId = sessionSnapshot.mediaId ?: return
        if (sessionSnapshot.isOfflinePlayback || hasReportedStart) return

        scope.launch {
            try {
                val result = mediaRepository.reportPlaybackStart(
                    itemId = mediaId,
                    playSessionId = sessionSnapshot.playSessionId,
                    mediaSourceId = sessionSnapshot.mediaSourceId,
                    positionTicks = positionProvider() * TICKS_PER_MILLISECOND,
                    playMethod = sessionSnapshot.playMethod.reportValue
                )
                if (result.isSuccess) {
                    hasReportedStart = true
                    startProgressReportingLoop()
                } else {
                    Log.e(
                        TAG,
                        "Failed to report playback start: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Error reporting playback start", error)
            }
        }
    }

    fun reportPlaybackProgress() {
        if (!canReportProgress()) return
        scope.launch {
            reportPlaybackProgressNow()
        }
    }

    fun reportPlaybackStopped(failed: Boolean = false) {
        val sessionSnapshot = session
        val mediaId = sessionSnapshot.mediaId ?: return
        if (sessionSnapshot.isOfflinePlayback || !hasReportedStart) return

        hasReportedStart = false
        progressReportingJob?.cancel()
        progressReportingJob = null

        val stoppedPositionTicks = positionProvider() * TICKS_PER_MILLISECOND
        scope.launch {
            try {
                val result = mediaRepository.reportPlaybackStopped(
                    itemId = mediaId,
                    positionTicks = stoppedPositionTicks,
                    playSessionId = sessionSnapshot.playSessionId,
                    mediaSourceId = sessionSnapshot.mediaSourceId,
                    failed = failed
                )
                if (result.isSuccess) {
                    publishConfirmedUserData(mediaId, stoppedPositionTicks)
                } else {
                    Log.e(
                        TAG,
                        "Failed to report playback stopped: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Error reporting playback stopped", error)
            }
        }
    }

    /**
     * The server decides whether the stop crossed the watched threshold. Read the confirmed user
     * data once so libraries do not keep a finished video listed under "resume" until a full scan.
     */
    private suspend fun publishConfirmedUserData(mediaId: String, stoppedPositionTicks: Long) {
        val confirmed = mediaRepository.getItemUserData(mediaId)
        UserDataRefreshSignals.notifyUserDataChanged(
            itemId = mediaId,
            played = confirmed?.played,
            playbackPositionTicks = confirmed?.playbackPositionTicks ?: stoppedPositionTicks
        )
    }

    fun onPlaybackPauseStateChanged() {
        if (hasReportedStart) {
            reportPlaybackProgress()
        }
    }

    fun onPlaybackPositionDiscontinuity() {
        if (hasReportedStart) {
            reportPlaybackProgress()
        }
    }

    private fun startProgressReportingLoop() {
        progressReportingJob?.cancel()
        progressReportingJob = scope.launch {
            while (hasReportedStart) {
                try {
                    delay(PROGRESS_REPORT_INTERVAL_MS)
                    reportPlaybackProgressNow()
                } catch (_: CancellationException) {
                    break
                } catch (error: Exception) {
                    Log.e(TAG, "Error in progress reporting loop", error)
                    break
                }
            }
        }
    }

    private fun canReportProgress(): Boolean {
        return hasReportedStart &&
            !session.isOfflinePlayback &&
            !session.mediaId.isNullOrBlank()
    }

    private suspend fun reportPlaybackProgressNow() {
        val sessionSnapshot = session
        val mediaId = sessionSnapshot.mediaId ?: return
        if (sessionSnapshot.isOfflinePlayback || !hasReportedStart) return

        try {
            val result = mediaRepository.reportPlaybackProgress(
                itemId = mediaId,
                positionTicks = positionProvider() * TICKS_PER_MILLISECOND,
                playSessionId = sessionSnapshot.playSessionId,
                mediaSourceId = sessionSnapshot.mediaSourceId,
                isPaused = isPausedProvider(),
                playMethod = sessionSnapshot.playMethod.reportValue
            )
            if (result.isFailure) {
                Log.e(
                    TAG,
                    "Failed to report playback progress: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (error: Exception) {
            Log.e(TAG, "Error reporting playback progress", error)
        }
    }
}
