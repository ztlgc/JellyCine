package com.jellycine.player.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class PlaybackOptionsTest {
    @Test fun gainIsAdditionalAmplitudeRatherThanAnAbsoluteVolume() {
        assertEquals(1.0, PlaybackOptions.gainMultiplier(0), 0.00001)
        assertEquals(1.3, PlaybackOptions.gainMultiplier(30), 0.00001)
        assertEquals(1.8, PlaybackOptions.gainMultiplier(80), 0.00001)
        assertEquals(2.5, PlaybackOptions.gainMultiplier(150), 0.00001)
        assertEquals(3.0, PlaybackOptions.gainMultiplier(200), 0.00001)
        assertEquals(5.0, PlaybackOptions.gainMultiplier(400), 0.00001)
        assertEquals(9.0, PlaybackOptions.gainMultiplier(800), 0.00001)
        assertEquals(16.0, PlaybackOptions.gainMultiplier(1500), 0.00001)
        assertEquals(0, PlaybackOptions.gainMillibels(0))
        assertEquals(228, PlaybackOptions.gainMillibels(30))
        assertEquals(1398, PlaybackOptions.gainMillibels(400))
    }

    @Test fun mpvDecibelsMatchPcmAmplitudeAtEveryPresetIncludingAboveTwelveDb() {
        assertEquals(listOf(0, 30, 80, 150, 200, 400, 800, 1500, 3000, 6000, 12000), PlaybackOptions.gainPercentages)
        listOf(0 to 1.0, 30 to 1.3, 80 to 1.8, 150 to 2.5, 200 to 3.0, 400 to 5.0,
            800 to 9.0, 1500 to 16.0, 3000 to 31.0, 6000 to 61.0, 12000 to 121.0).forEach { (percent, amplitude) ->
            assertEquals(amplitude, 10.0.pow(PlaybackOptions.gainDecibels(percent) / 20), 0.00001)
        }
        assertTrue(PlaybackOptions.gainDecibels(400) > 12.0)
        assertEquals(24.08, PlaybackOptions.gainDecibels(1500), 0.01)
    }

    @Test fun legacySavedGainKeepsAnEnabledPresetWhileInvalidValuesStayOff() {
        listOf(10 to 30, 20 to 30, 50 to 30, 70 to 80, 30 to 30, 400 to 400, 0 to 0).forEach { (saved, restored) ->
            assertEquals(restored, PlaybackOptions.restoreGain(saved))
        }
        assertEquals(0, PlaybackOptions.restoreGain(-10))
        assertEquals(0, PlaybackOptions.restoreGain(100))
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
