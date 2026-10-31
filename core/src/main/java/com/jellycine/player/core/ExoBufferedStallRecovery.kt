package com.jellycine.player.core

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.jellycine.logging.LogLevel
import com.jellycine.logging.PlaybackLogStore

/** Bounded recovery of buffered, motionless playback; ordinary loading and user seeks are excluded. */
@UnstableApi
class ExoBufferedStallRecovery(context: Context, private val player: ExoPlayer, private val sessionId: String) : AnalyticsListener {
    private val appContext = context.applicationContext
    private val handler = Handler(player.applicationLooper)
    private val detector = BufferedPlaybackStallDetector()
    private var released = false
    private var attempts = 0
    private var lastRecoveryMs: Long? = null
    private var limitLogged = false

    private val sample = object : Runnable {
        override fun run() {
            if (released) return
            val now = SystemClock.elapsedRealtime()
            val counters = player.videoDecoderCounters
            counters?.ensureUpdated()
            val eligible = player.playWhenReady && player.playerError == null &&
                player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING)
            val stalledMs = detector.observe(now, eligible, player.currentPosition,
                player.totalBufferedDuration, counters?.renderedOutputBufferCount)
            if (stalledMs != null) {
                if (attempts >= MAX_ATTEMPTS) {
                    if (!limitLogged) {
                        log("buffered_stall_recovery_limit", "attempts=$attempts posMs=${player.currentPosition} stalledMs=$stalledMs")
                        limitLogged = true
                    }
                } else if (lastRecoveryMs == null || now - lastRecoveryMs!! >= RECOVERY_COOLDOWN_MS) {
                    recover(now, stalledMs)
                }
            }
            if (!released) handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    init {
        player.addAnalyticsListener(this)
        handler.postDelayed(sample, SAMPLE_INTERVAL_MS)
    }

    private fun recover(nowMs: Long, stalledMs: Long) {
        if (player.mediaItemCount == 0) return
        val itemIndex = player.currentMediaItemIndex
        val positionMs = player.currentPosition
        val playWhenReady = player.playWhenReady
        attempts++
        lastRecoveryMs = nowMs
        detector.reset()
        log("buffered_stall_recovery", "attempt=$attempts posMs=$positionMs bufferedMs=${player.totalBufferedDuration} " +
            "stalledMs=$stalledMs state=${player.playbackState} action=stop_seek_prepare")
        // stop() releases the stalled renderers/AudioTrack, retaining media sources, headers and
        // track/speed/volume preferences. A same-position seek alone can be optimized away.
        player.stop()
        player.seekTo(itemIndex, positionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    override fun onPositionDiscontinuity(eventTime: AnalyticsListener.EventTime, oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo, reason: Int) = detector.reset()

    override fun onPlayWhenReadyChanged(eventTime: AnalyticsListener.EventTime, playWhenReady: Boolean, reason: Int) = detector.reset()

    override fun onPlaybackParametersChanged(eventTime: AnalyticsListener.EventTime, playbackParameters: PlaybackParameters) = detector.reset()

    override fun onPlaybackSuppressionReasonChanged(eventTime: AnalyticsListener.EventTime, playbackSuppressionReason: Int) = detector.reset()

    override fun onPlayerReleased(eventTime: AnalyticsListener.EventTime) {
        released = true
        detector.reset()
        handler.removeCallbacks(sample)
    }

    private fun log(event: String, detail: String) =
        PlaybackLogStore.log(appContext, "ExoPlayback", "session=$sessionId event=$event $detail", LogLevel.WARN)

    companion object {
        private const val SAMPLE_INTERVAL_MS = 1_000L
        private const val RECOVERY_COOLDOWN_MS = 30_000L
        private const val MAX_ATTEMPTS = 2
    }
}
