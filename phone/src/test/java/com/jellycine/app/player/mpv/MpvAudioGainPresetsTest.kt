package com.jellycine.app.player.mpv

import org.junit.Assert.*
import org.junit.Test

class MpvAudioGainPresetsTest {
    @Test fun higherPresetsUseTheirFullMultiplierBeforeTheFinalLimiter() {
        listOf(3000 to "31.000000000", 6000 to "61.000000000", 12000 to "121.000000000").forEach { (percent, multiplier) ->
            val graph = MpvAudioGain.filters(percent, false)
            assertTrue(graph.contains("volume=$multiplier:precision=float,alimiter="))
            assertTrue(graph.contains("limit=0.96"))
            assertTrue(graph.contains("level=0:latency=1"))
        }
    }

    @Test fun normalizationSharesTheNewTotalCeiling() {
        assertTrue(MpvAudioGain.filters(1500, true).contains(":m=4.000000000:"))
        assertTrue(MpvAudioGain.filters(6000, true).contains(":m=1.983606557:"))
        assertTrue(MpvAudioGain.filters(12000, true).contains(":m=1.000000000:"))
    }
}
