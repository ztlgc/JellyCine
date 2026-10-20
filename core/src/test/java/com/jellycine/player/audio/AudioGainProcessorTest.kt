package com.jellycine.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioGainProcessorTest {
    private fun processor() = AudioGainProcessor().apply {
        configure(AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun collect(processor: AudioGainProcessor): List<Int> {
        val buffer = processor.output.order(ByteOrder.nativeOrder())
        return buildList { while (buffer.hasRemaining()) add(buffer.short.toInt()) }
    }

    private fun output(processor: AudioGainProcessor, samples: IntArray, chunkBytes: Int = samples.size * 2): List<Int> {
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { pcm.putShort(it.toShort()) }
        val bytes = pcm.array()
        val result = ArrayList<Int>()
        var offset = 0
        while (offset < bytes.size) {
            val count = chunkBytes.coerceAtMost(bytes.size - offset)
            val input = ByteBuffer.wrap(bytes, offset, count)
            processor.queueInput(input)
            assertFalse(input.hasRemaining())
            result.addAll(collect(processor))
            offset += count
        }
        processor.queueEndOfStream()
        result.addAll(collect(processor))
        assertTrue(processor.isEnded)
        return result
    }

    @Test fun eachGainActuallyAmplifiesLowLevelPcmSamples() {
        listOf(30 to 1300, 80 to 1800, 150 to 2500, 200 to 3000, 400 to 5000, 800 to 9000, 1500 to 16000).forEach { (percent, expected) ->
            val processor = processor().apply { setGainPercent(percent) }
            assertEquals(listOf(expected, -expected, 0, expected), output(processor, intArrayOf(1000, -1000, 0, 1000)))
        }
    }

    @Test fun higherPresetsAmplifyDecodedPcmAndKeepTheirLimitForPeaks() {
        listOf(3000 to 6200, 6000 to 12200, 12000 to 24200).forEach { (percent, expected) ->
            val quiet = processor().apply { setGainPercent(percent) }
            assertEquals(listOf(expected, -expected), output(quiet, intArrayOf(200, -200)))
            val peaks = processor().apply { setGainPercent(percent) }
            val limited = output(peaks, intArrayOf(30000, -15000))
            assertTrue(limited.all { kotlin.math.abs(it) <= 31458 })
            assertEquals(-0.5, limited[1].toDouble() / limited[0], 0.0001)
        }
    }

    @Test fun disabledPcm16RoundTripsExactlyAcrossOddByteAndStereoFrameBoundaries() {
        val processor = processor()
        val source = IntArray(65536) { it - 32768 }
        assertEquals(source.toList(), output(processor, source, chunkBytes = 13))
        assertEquals(0, processor.diagnosticsSnapshot()!!.limitedFrames)
    }

    @Test fun boostedPeaksAreLinkedAndBoundedRatherThanClippedIndependently() {
        val processor = processor().apply { setGainPercent(1500) }
        val result = output(processor, intArrayOf(30000, -15000, 16000, -8000), chunkBytes = 3)
        assertEquals(4, result.size)
        assertTrue(result.all { kotlin.math.abs(it) <= 31458 })
        assertEquals(-0.5, result[1].toDouble() / result[0], 0.0001)
        assertEquals(-0.5, result[3].toDouble() / result[2], 0.0001)
        assertTrue(processor.diagnosticsSnapshot()!!.limitedFrames > 0)
    }

    @Test fun flushDropsBufferedPreSeekAudioAndKeepsTheGainOption() {
        val processor = processor().apply { setGainPercent(400) }
        val buffered = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putShort(30_000).putShort(30_000)
        buffered.flip()
        processor.queueInput(buffered)
        assertEquals(emptyList<Int>(), collect(processor))
        processor.flush()
        assertEquals(0, processor.diagnosticsSnapshot()!!.inputFrames)
        assertEquals(listOf(500, -1000), output(processor, intArrayOf(100, -200)))
        assertTrue(processor.isActive)
    }
}
