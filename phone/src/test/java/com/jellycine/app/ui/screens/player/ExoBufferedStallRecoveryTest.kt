package com.jellycine.app.ui.screens.player

import android.app.Application
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.jellycine.player.core.ExoBufferedStallRecovery
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.lang.reflect.Proxy
import java.time.Duration

/** Lifecycle/control contract only; decoder performance still requires the affected handset. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ExoBufferedStallRecoveryTest {
    private val eventTime = AnalyticsListener.EventTime(0, Timeline.EMPTY, 0, null, 0,
        Timeline.EMPTY, 0, null, 0, 0)
    private class FrozenPlayer {
        var state = Player.STATE_READY
        var playWhenReady = true
        var bufferMs = 121_323L
        val calls = mutableListOf<String>()
        lateinit var listener: AnalyticsListener
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, args ->
            when (method.name) {
                "getApplicationLooper" -> Looper.getMainLooper()
                "addAnalyticsListener" -> { listener = args!![0] as AnalyticsListener; null }
                "getVideoDecoderCounters", "getPlayerError" -> null
                "getPlayWhenReady" -> playWhenReady
                "setPlayWhenReady" -> { playWhenReady = args!![0] as Boolean; null }
                "getPlaybackState" -> state
                "getPlaybackSuppressionReason" -> Player.PLAYBACK_SUPPRESSION_REASON_NONE
                "getCurrentPosition" -> 3_607_742L
                "getTotalBufferedDuration" -> bufferMs
                "getMediaItemCount" -> 1
                "getCurrentMediaItemIndex" -> 0
                "stop" -> { calls += "stop"; state = Player.STATE_IDLE; null }
                "seekTo" -> { calls += "seek:${args!!.joinToString() }"; null }
                "prepare" -> { calls += "prepare"; state = Player.STATE_READY; null }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as ExoPlayer
    }

    @Test fun cachedFrozenPlaybackRestartsTheSameItemAndPositionWithBoundedRetries() {
        val frozen = FrozenPlayer()
        ExoBufferedStallRecovery(RuntimeEnvironment.getApplication(), frozen.player, "recovery-test")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(7))
        assertEquals(listOf("stop", "seek:0, 3607742", "prepare"), frozen.calls)
        assertTrue(frozen.playWhenReady)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertEquals(3, frozen.calls.size) // Cooldown prevents a restart loop.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(80))
        assertEquals(6, frozen.calls.size) // At most two attempts per player session.
        frozen.listener.onPlayerReleased(eventTime)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
        assertEquals(6, frozen.calls.size)
    }

    @Test fun pausedAndUnbufferedPlaybackIsLeftAloneAndReleaseCancelsSampling() {
        val frozen = FrozenPlayer().apply { playWhenReady = false }
        ExoBufferedStallRecovery(RuntimeEnvironment.getApplication(), frozen.player, "paused-test")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15))
        frozen.playWhenReady = true
        frozen.bufferMs = 0
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15))
        assertTrue(frozen.calls.isEmpty())
        frozen.listener.onPlayerReleased(eventTime)
        frozen.bufferMs = 120_000
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15))
        assertTrue(frozen.calls.isEmpty())
    }
}
