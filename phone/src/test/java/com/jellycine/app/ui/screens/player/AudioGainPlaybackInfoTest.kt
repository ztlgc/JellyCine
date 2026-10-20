package com.jellycine.app.ui.screens.player

import org.junit.Assert.*
import org.junit.Test

class AudioGainPlaybackInfoTest {
    private fun info(paired: Boolean = true) = AudioGainPlaybackInfo(
        engine = "test", configuredGainDb = 13.9794, normalizationEnabled = false,
        inputRmsDbfs = -40.0, outputRmsDbfs = -26.0206, outputPeakDbfs = -23.0,
        systemVolumeIndex = 8, systemVolumeMax = 15, measurementPaired = paired
    )

    @Test fun onlyMeasurementsOfTheSameAudioIntervalEstablishGain() {
        assertEquals(13.9794, info().measuredGainDb!!, 0.0001)
        assertNull(info(paired = false).measuredGainDb)
    }

    @Test fun silenceMissingMetersAndNoiseFloorDoNotPretendToConfirmGain() {
        assertNull(info().copy(inputRmsDbfs = null).measuredGainDb)
        assertNull(info().copy(inputRmsDbfs = Double.NEGATIVE_INFINITY).measuredGainDb)
        assertNull(info().copy(inputRmsDbfs = -100.0).measuredGainDb)
        assertNull(info().copy(outputRmsDbfs = Double.NaN).measuredGainDb)
    }

    @Test fun measuredGainCanBeLessThanThePresetWhenPeaksAreLimited() {
        val limited = info().copy(outputRmsDbfs = -34.0)
        assertEquals(6.0, limited.measuredGainDb!!, 0.0001)
        assertTrue(limited.measuredGainDb!! < limited.configuredGainDb)
    }
}
