package com.jellycine.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HoldPlaybackSpeedTest {
    @Test fun releaseRestoresEverySupportedOriginalSpeed() {
        PlaybackOptions.speeds.forEach { original ->
            val speed = HoldPlaybackSpeed()
            speed.setPreferredSpeed(original)
            speed.start()
            assertEquals(2f, speed.effectiveSpeed)
            assertEquals(original, speed.preferredSpeed)
            speed.stop()
            assertEquals(original, speed.effectiveSpeed)
            assertFalse(speed.isActive)
        }
    }

    @Test fun repeatedStartAndCancellationDoNotLoseTheOriginalSpeed() {
        val speed = HoldPlaybackSpeed()
        speed.setPreferredSpeed(1.5f)
        speed.start()
        speed.start()
        speed.stop()
        speed.stop()
        assertEquals(1.5f, speed.effectiveSpeed)
    }

    @Test fun restoringPreferencesDuringPlaybackRestartKeepsTheHoldActive() {
        val speed = HoldPlaybackSpeed()
        speed.setPreferredSpeed(1.25f)
        speed.start()
        speed.setPreferredSpeed(1.25f)
        assertEquals(2f, speed.effectiveSpeed)
        speed.stop()
        assertEquals(1.25f, speed.effectiveSpeed)
    }

    @Test fun aNewSpeedSelectionDuringTheHoldIsUsedAfterRelease() {
        val speed = HoldPlaybackSpeed()
        speed.start()
        speed.setPreferredSpeed(0.75f)
        assertEquals(2f, speed.effectiveSpeed)
        speed.stop()
        assertEquals(0.75f, speed.effectiveSpeed)
    }

    @Test fun cancelledHoldDoesNotAffectTheNextPressOrInvalidPreferenceFallback() {
        val speed = HoldPlaybackSpeed()
        speed.setPreferredSpeed(Float.NaN)
        speed.start()
        speed.stop()
        assertEquals(1f, speed.effectiveSpeed)
        speed.setPreferredSpeed(1.75f)
        speed.start()
        speed.stop()
        assertEquals(1.75f, speed.effectiveSpeed)
    }
}
