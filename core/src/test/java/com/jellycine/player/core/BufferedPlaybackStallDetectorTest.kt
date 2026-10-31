package com.jellycine.player.core

import org.junit.Assert.*
import org.junit.Test

class BufferedPlaybackStallDetectorTest {
    @Test fun readyBufferingOscillationDoesNotHideFrozenBufferedPlayback() {
        val detector = BufferedPlaybackStallDetector()
        // Replay the stable position/buffer/frame counters in both exported handset stalls.
        assertNull(detector.observe(0, true, 3_607_742, 121_323, 3622))
        for (now in 1_000L..5_000L step 1_000L) {
            assertNull(detector.observe(now, true, 3_607_742, 121_323, 3622))
        }
        assertEquals(6_000L, detector.observe(6_000, true, 3_607_742, 121_323, 3622))
        assertEquals(10_000L, detector.observe(10_000, true, 3_607_742, 121_323, 3622))
    }

    @Test fun insufficientBufferPauseAndSuppressionCannotTriggerRecovery() {
        val detector = BufferedPlaybackStallDetector()
        for (now in 0L..20_000L step 1_000L) {
            assertNull(detector.observe(now, true, 1000, 1500, 10))
            assertNull(detector.observe(now, false, 1000, 120000, 10))
        }
    }

    @Test fun slowContinuousProgressAndVideoOutputPreventFalseRecovery() {
        val detector = BufferedPlaybackStallDetector()
        for (now in 0L..30_000L step 1_000L) {
            assertNull(detector.observe(now, true, now / 4, 120000, null))
        }
        detector.reset()
        for (now in 0L..20_000L step 1_000L) {
            assertNull(detector.observe(now, true, 1000, 120000, now.toInt()))
        }
    }

    @Test fun seekResetAndNewProgressStartFreshObservationWindows() {
        val detector = BufferedPlaybackStallDetector()
        detector.observe(0, true, 1000, 120000, 10)
        detector.reset() // Explicit seek, even when it lands at the same position.
        assertNull(detector.observe(6000, true, 1000, 120000, 10))
        assertNull(detector.observe(11000, true, 1000, 120000, 10))
        assertNull(detector.observe(12000, true, 1300, 120000, 10))
        assertNull(detector.observe(17000, true, 1300, 120000, 10))
        assertEquals(6000L, detector.observe(18000, true, 1300, 120000, 10))
    }
}
