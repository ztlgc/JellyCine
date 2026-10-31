package com.jellycine.app.ui.screens.player

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import com.jellycine.app.ui.screens.dashboard.settings.LogsExporter
import com.jellycine.logging.PlaybackLogStore
import com.jellycine.player.core.PlayerUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

/** Verifies diagnostics/export plumbing; does not simulate decoder or server performance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ExoPlaybackDiagnosticsTest {
    @Test fun playerEventsArePersistedExportedAndSamplingStopsOnRelease() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val player = PlayerUtils.createPlayer(context, diagnosticContext = "source=diagnostic_test")
        try {
            val collector = player.analyticsCollector
            collector.onVideoDecoderInitialized("c2.android.avc.decoder", SystemClock.elapsedRealtime(), 12)
            collector.onDroppedFrames(3, 1000)
            collector.onAudioUnderrun(4096, 20, 150)
            collector.onVideoCodecError(IllegalStateException("https://server/?token=private-secret"))
            shadowOf(Looper.getMainLooper()).idle()

            val persisted = PlaybackLogStore.read(context)
            assertTrue(persisted.contains("event=load_control"))
            assertTrue(persisted.contains("source=diagnostic_test"))
            assertTrue(persisted.contains("event=video_decoder name=c2.android.avc.decoder"))
            assertTrue(persisted.contains("event=dropped_frames"))
            assertTrue(persisted.contains("event=audio_underrun"))
            assertTrue(persisted.contains("bufferedMs="))
            assertTrue(persisted.contains("event=video_codec_error IllegalStateException"))
            assertFalse(persisted.contains("private-secret"))

            player.release()
            shadowOf(Looper.getMainLooper()).idle()
            val afterRelease = PlaybackLogStore.read(context)
            assertTrue(afterRelease.contains("event=session_end"))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
            assertEquals(afterRelease, PlaybackLogStore.read(context))

            val exported = LogsExporter.buildText(context)
            assertTrue(exported.contains("EXOPLAYER PLAYBACK DIAGNOSTICS"))
            assertTrue(exported.contains("Version:"))
            assertTrue(exported.contains("Media3:"))
            assertTrue(exported.contains("Diagnostics: buffered-stall-v2"))
            assertTrue(exported.contains(afterRelease))
        } finally {
            if (!player.isReleased) player.release()
        }
    }
}
