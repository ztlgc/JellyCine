package com.jellycine.player.core

import org.junit.Assert.*
import org.junit.Test

class PlaybackOptionsTest {
    @Test fun gainIsAdditionalAmplitudeRatherThanAnAbsoluteVolume() {
        assertEquals(1.0, PlaybackOptions.gainMultiplier(0), 0.00001)
        assertEquals(1.1, PlaybackOptions.gainMultiplier(10), 0.00001)
        assertEquals(1.7, PlaybackOptions.gainMultiplier(70), 0.00001)
        assertEquals(0, PlaybackOptions.gainMillibels(0))
        assertEquals(83, PlaybackOptions.gainMillibels(10))
        assertEquals(461, PlaybackOptions.gainMillibels(70))
    }

    @Test fun unexpectedPersistedSettingsFallBackToDefaults() {
        assertEquals(0, PlaybackOptions.validGain(-10))
        assertEquals(0, PlaybackOptions.validGain(100))
        assertEquals(1f, PlaybackOptions.validSpeed(Float.NaN))
        assertEquals(1f, PlaybackOptions.validSpeed(3f))
    }

    @Test fun speedButtonsStopAtBothBounds() {
        assertEquals(0.5f, PlaybackOptions.stepSpeed(0.5f, -1))
        assertEquals(2f, PlaybackOptions.stepSpeed(2f, 1))
        assertEquals(1.25f, PlaybackOptions.stepSpeed(1f, 1))
        assertEquals(0.75f, PlaybackOptions.stepSpeed(1f, -1))
    }
}
