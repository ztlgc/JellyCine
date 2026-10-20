package com.jellycine.app.player.mpv

import org.junit.Assert.*
import org.junit.Test

class MpvAudioGainDiagnosticsTest {
    @Test
    fun bufferingKeepsSeparateMeasurementsWithoutClaimingAnRmsRatio() {
        val result = MpvAudioGainDiagnostics.fromPcmStats(
            doubleArrayOf(-40.0, -20.0, -26.0, -6.0, 48000.0, 47760.0), "float"
        )!!
        assertFalse(result.measurementPaired)
        assertEquals(48000L, result.inputProcessedFrames)
        assertEquals(47760L, result.processedFrames)
        assertEquals(-26.0, result.outputRmsDbfs!!, 0.0)
    }

    @Test
    fun equalPositiveFrameCountsAllowPairing() {
        val result = MpvAudioGainDiagnostics.fromPcmStats(
            doubleArrayOf(-40.0, -20.0, -26.0, -6.0, 48000.0, 48000.0), "s16"
        )!!
        assertTrue(result.measurementPaired)
    }

    @Test
    fun absentSamplesStayUnknownAndSilenceRetainsNegativeInfinity() {
        val result = MpvAudioGainDiagnostics.fromPcmStats(
            doubleArrayOf(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.NaN, Double.NaN, Double.NaN, 0.0), ""
        )!!
        assertNull(result.processedFrames)
        assertNull(result.inputProcessedFrames)
        assertNull(result.outputRmsDbfs)
        assertEquals(Double.NEGATIVE_INFINITY, result.inputRmsDbfs!!, 0.0)
        assertFalse(result.measurementPaired)
        assertNull(MpvAudioGainDiagnostics.fromPcmStats(doubleArrayOf(1.0), ""))
    }
}
