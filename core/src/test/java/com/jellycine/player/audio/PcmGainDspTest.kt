package com.jellycine.player.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class PcmGainDspTest {
    private val sampleRate = 48_000

    private fun tone(frames: Int, amplitude: Double = 0.01, channels: Int = 1): FloatArray =
        FloatArray(frames * channels) { (amplitude * sin(2 * PI * (it / channels) / 48)).toFloat() }

    private fun render(dsp: PcmGainDsp, samples: FloatArray, chunkSamples: Int = samples.size): FloatArray {
        val output = ArrayList<Float>(samples.size)
        var position = 0
        while (position < samples.size) {
            val end = (position + chunkSamples).coerceAtMost(samples.size)
            dsp.process(samples.copyOfRange(position, end)).forEach { output.add(it) }
            position = end
        }
        dsp.drain().forEach { output.add(it) }
        return output.toFloatArray()
    }

    private fun rms(samples: FloatArray): Double = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)

    @Test fun lowLevelSineGetsTheActualRequestedGainAtEveryPreset() {
        val source = tone(sampleRate, 0.001)
        listOf(1.0, 1.3, 1.8, 2.5, 3.0, 5.0, 9.0, 16.0, 31.0, 61.0, 121.0).forEach { gain ->
            val dsp = PcmGainDsp(sampleRate, 1).apply { setSettings(gain) }
            val output = render(dsp, source, 997)
            assertEquals(source.size, output.size)
            assertEquals(gain, rms(output) / rms(source), 0.00001)
            assertEquals(0, dsp.diagnostics().limitedFrames)
        }
    }

    @Test fun bypassIsBitExactEvenWithFullScaleAndUnalignedChunks() {
        val source = FloatArray(6001) { ((it * 7919 % 65536) - 32768) / 32768f }
        val dsp = PcmGainDsp(sampleRate, 2)
        assertArrayEquals(source, render(dsp, source, 37), 0f)
        assertEquals(0, dsp.diagnostics().limitedFrames)
    }

    @Test fun shortStreamDrainsEverySampleAndCanDrainOnlyOnce() {
        val dsp = PcmGainDsp(sampleRate, 2).apply { setSettings(5.0) }
        val input = floatArrayOf(0.01f, -0.02f, 0.03f, 0.04f, -0.01f)
        assertEquals(0, dsp.process(input).size)
        val output = dsp.drain()
        assertArrayEquals(input.map { it * 5f }.toFloatArray(), output, 0.000001f)
        assertEquals(0, dsp.drain().size)
    }

    @Test fun limiterBoundsTransientAndKeepsTheStereoImage() {
        val source = FloatArray(2400 * 2) { if (it % 2 == 0) 0.02f else -0.005f }
        source[1200 * 2] = 0.9f
        source[1200 * 2 + 1] = -0.225f
        val dsp = PcmGainDsp(sampleRate, 2).apply { setSettings(16.0) }
        val output = render(dsp, source, 177)
        assertTrue(output.maxOf { abs(it) } <= PcmGainDsp.LIMIT_CEILING + 0.000001)
        for (frame in 0 until output.size / 2) assertEquals(-0.25, (output[frame * 2 + 1] / output[frame * 2]).toDouble(), 0.000001)
        assertTrue(dsp.diagnostics().limitedFrames > 0)
        assertTrue(abs(output[1190 * 2]) < abs(source[1190 * 2] * 16f) * 0.2f)
    }

    @Test fun limiterWorksForAnImmediatePeakAndTheLastFrameAtEos() {
        val input = floatArrayOf(1f, -0.5f, 0.9f, -0.45f)
        val output = render(PcmGainDsp(sampleRate, 2).apply { setSettings(16.0) }, input, 1)
        assertEquals(input.size, output.size)
        assertTrue(output.all { abs(it) <= PcmGainDsp.LIMIT_CEILING + 0.000001 })
        assertEquals(-0.5, (output[1] / output[0]).toDouble(), 0.000001)
        assertEquals(-0.5, (output[3] / output[2]).toDouble(), 0.000001)
    }

    @Test fun outputIsInvariantToChunksAcrossAForwardAndReleaseEnvelope() {
        val source = tone(sampleRate, 0.03, 2).also {
            for (frame in 1000 until 1300) {
                it[frame * 2] = if (frame % 2 == 0) 0.8f else -0.8f
                it[frame * 2 + 1] = it[frame * 2] * 0.5f
            }
            it[it.size - 2] = 0.99f
        }
        val single = render(PcmGainDsp(sampleRate, 2).apply { setSettings(9.0, true) }, source)
        val split = render(PcmGainDsp(sampleRate, 2).apply { setSettings(9.0, true) }, source, 101)
        assertArrayEquals(single, split, 0f)
    }

    @Test fun runningSwitchIsARampAndEventuallyReturnsToExactBypass() {
        val dsp = PcmGainDsp(sampleRate, 1)
        val block = FloatArray(sampleRate / 10) { 0.01f }
        dsp.process(block)
        dsp.setSettings(5.0)
        val switched = dsp.process(block)
        val start = dsp.lookAheadFrames
        assertTrue(switched[start] > 0.01f)
        assertTrue(switched[start] < 0.011f)
        assertEquals(0.05f, switched.last(), 0.000001f)
        for (index in start + 1 until switched.size) assertTrue(abs(switched[index] - switched[index - 1]) < 0.0001f)
        dsp.setSettings(1.0)
        dsp.process(block)
        assertArrayEquals(block, dsp.process(block), 0f)
    }

    @Test fun seekResetDropsOldAudioAndResetsDiagnosticsButRetainsSettings() {
        val dsp = PcmGainDsp(sampleRate, 2).apply { setSettings(5.0) }
        dsp.process(FloatArray(100) { 0.8f })
        dsp.reset()
        assertEquals(0, dsp.diagnostics().inputFrames)
        assertEquals(0, dsp.diagnostics().outputFrames)
        assertEquals(0, dsp.diagnostics().limitedFrames)
        val source = floatArrayOf(0.001f, -0.002f)
        assertArrayEquals(floatArrayOf(0.005f, -0.01f), render(dsp, source), 0.000001f)
    }

    @Test fun normalizationRaisesQuietSpeechSlowlyWithAFourTimesLimit() {
        val dsp = PcmGainDsp(sampleRate, 1).apply { setSettings(1.0, true) }
        val source = tone(sampleRate * 10, 0.02)
        val output = render(dsp, source, 1024)
        val initialRatio = rms(output.copyOfRange(0, 4800)) / rms(source.copyOfRange(0, 4800))
        val finalRatio = rms(output.copyOfRange(output.size - sampleRate, output.size)) / rms(source.copyOfRange(source.size - sampleRate, source.size))
        assertTrue(initialRatio < 1.1)
        assertTrue(finalRatio in 3.9..4.00001)
    }

    @Test fun normalizationDoesNotFillSilenceOrRaiseBelowThresholdNoise() {
        val dsp = PcmGainDsp(sampleRate, 1).apply { setSettings(1.0, true) }
        assertTrue(render(dsp, FloatArray(sampleRate)).all { it == 0f })
        val noise = FloatArray(sampleRate * 3) { if (it % 2 == 0) 0.001f else -0.001f }
        assertArrayEquals(noise, render(dsp, noise, 961), 0f)
        assertEquals(1.0, dsp.diagnostics().normalizationMultiplier, 0.0)
    }

    @Test fun normalizationAndManualGainTogetherCannotExceedTheHighestPreset() {
        val dsp = PcmGainDsp(sampleRate, 1).apply { setSettings(61.0, true) }
        val source = tone(sampleRate * 8, 0.005)
        val output = render(dsp, source, 2048)
        val start = output.size - sampleRate
        assertEquals(121.0, rms(output.copyOfRange(start, output.size)) / rms(source.copyOfRange(start, source.size)), 0.00001)
    }

    @Test fun normalizationCanTurnOffAndFullyReturnToBaseline() {
        val dsp = PcmGainDsp(sampleRate, 1).apply { setSettings(1.0, true) }
        dsp.process(FloatArray(sampleRate * 4) { 0.02f })
        dsp.setSettings(1.0, false)
        dsp.process(FloatArray(sampleRate / 10) { 0.02f })
        val stable = FloatArray(sampleRate / 10) { 0.02f }
        assertArrayEquals(stable, dsp.process(stable), 0f)
    }

    @Test fun diagnosticsMeasureMatchedCurrentOutputAfterOneFiveOneSwitches() {
        val dsp = PcmGainDsp(sampleRate, 1)
        listOf(1.0, 5.0, 1.0).forEach { gain ->
            dsp.setSettings(gain)
            dsp.process(tone(sampleRate * 2))
            val diagnostics = dsp.diagnostics()
            assertEquals(gain, diagnostics.outputRms / diagnostics.inputRms, 0.00001)
            assertEquals(gain, diagnostics.outputPeak / diagnostics.inputPeak, 0.00001)
            assertEquals(sampleRate / 2L, diagnostics.measurementFrames)
            assertEquals(diagnostics.inputFrames - dsp.lookAheadFrames, diagnostics.outputFrames)
        }
    }

    @Test fun diagnosticsUseOutputPairedInputEvenWhenAStreamChangesAtTheBufferedTail() {
        val dsp = PcmGainDsp(sampleRate, 1).apply { setSettings(5.0) }
        dsp.process(FloatArray(1000) { 0.01f } + FloatArray(dsp.lookAheadFrames) { 0.1f })
        val diagnostics = dsp.diagnostics()
        assertEquals(0.01, diagnostics.inputRms, 0.000001)
        assertEquals(0.05, diagnostics.outputRms, 0.000001)
        assertEquals(1000, diagnostics.measurementFrames)
    }

    @Test fun diagnosticSettingFollowsTheOutputInsteadOfRelabelingBufferedOldAudio() {
        val dsp = PcmGainDsp(sampleRate, 1)
        dsp.process(FloatArray(1000) { 0.01f })
        dsp.setSettings(5.0)
        dsp.process(floatArrayOf(0.01f))
        assertEquals(1.0, dsp.diagnostics().configuredGainMultiplier, 0.0)
        assertEquals(1.0, dsp.diagnostics().outputRms / dsp.diagnostics().inputRms, 0.00001)
        dsp.process(FloatArray(dsp.lookAheadFrames) { 0.01f })
        assertEquals(5.0, dsp.diagnostics().configuredGainMultiplier, 0.0)
        assertTrue(dsp.diagnostics().measurementFrames < dsp.lookAheadFrames)
    }

    @Test fun nonFiniteSettingCannotMakeTheOutputNonFinite() {
        val dsp = PcmGainDsp(sampleRate, 1).apply { setSettings(Double.NaN, true) }
        val output = render(dsp, floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -0.01f))
        assertTrue(output.all { it.isFinite() })
        assertEquals(1.0, dsp.diagnostics().configuredGainMultiplier, 0.0)
    }
}
