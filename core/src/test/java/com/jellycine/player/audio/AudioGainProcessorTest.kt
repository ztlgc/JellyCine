package com.jellycine.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import com.google.common.collect.ImmutableList
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

    @Test fun repeatedEndOfStreamKeepsTheUnconsumedLookAheadTail() {
        val processor = processor()
        val input = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())
        repeat(2048) { input.putShort(1000) }
        input.flip()
        processor.queueInput(input)
        collect(processor)

        processor.queueEndOfStream()
        processor.queueEndOfStream()

        assertEquals(List(480) { 1000 }, collect(processor))
        assertTrue(processor.isEnded)
    }

    @Test fun emptyInputDoesNotDiscardPendingAudio() {
        val processor = processor()
        val input = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())
        repeat(2048) { input.putShort(1000) }
        input.flip()
        processor.queueInput(input)
        processor.queueInput(AudioProcessor.EMPTY_BUFFER)

        assertEquals(List(1568) { 1000 }, collect(processor))
        processor.queueEndOfStream()
        assertEquals(List(480) { 1000 }, collect(processor))
        assertTrue(processor.isEnded)
    }

    @Test fun endOfStreamPreservesPendingAudioBeforeEmittingTheTailOnce() {
        val processor = processor()
        val source = IntArray(2048) { it - 1024 }
        val input = ByteBuffer.allocateDirect(source.size * 2).order(ByteOrder.nativeOrder())
        source.forEach { input.putShort(it.toShort()) }
        input.flip()
        processor.queueInput(input)
        processor.queueEndOfStream()
        processor.queueEndOfStream()
        assertFalse(processor.isEnded)

        val output = collect(processor)
        assertFalse(processor.isEnded)
        val tail = collect(processor)
        assertEquals(source.toList(), output + tail)
        assertTrue(processor.isEnded)
        processor.queueEndOfStream()
        assertTrue(processor.isEnded)
        assertTrue(collect(processor).isEmpty())
    }

    @Test fun flushDiscardsAnUnconsumedEndOfStreamTailAndAcceptsNewAudio() {
        val processor = processor().apply { setGainPercent(400) }
        val input = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())
        repeat(2048) { input.putShort(1000) }
        input.flip()
        processor.queueInput(input)
        processor.queueEndOfStream()
        processor.flush()

        assertFalse(processor.isEnded)
        assertTrue(collect(processor).isEmpty())
        assertEquals(listOf(500, -1000), output(processor, intArrayOf(100, -200)))
    }

    @Test fun speedChangesDrainTheRealMedia3PipelineAndResumeInput() {
        val gain = AudioGainProcessor()
        val sonic = SonicAudioProcessor()
        val upstream = object : BaseAudioProcessor() {
            override fun onConfigure(inputAudioFormat: AudioFormat) = inputAudioFormat
            override fun queueInput(inputBuffer: ByteBuffer) {
                if (!inputBuffer.hasRemaining()) return
                val output = replaceOutputBuffer(inputBuffer.remaining())
                output.put(inputBuffer).flip()
            }
        }
        val pipeline = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(upstream, gain, sonic))
        val format = AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        for (speed in listOf(1f, 2f, 1f, 2f, 1f)) {
            sonic.setSpeed(speed)
            pipeline.configure(format)
            pipeline.flush()
            var outputBytes = 0
            repeat(16) {
                val input = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())
                repeat(2048) { input.putShort(1000) }
                input.flip()
                pipeline.queueInput(input)
                assertFalse(input.hasRemaining())
                val output = pipeline.output
                outputBytes += output.remaining()
                output.position(output.limit())
            }
            pipeline.queueEndOfStream()
            var polls = 0
            while (!pipeline.isEnded && polls++ < 100) {
                val output = pipeline.output
                // AudioSink can consume only part of the output on each write.
                val consumed = minOf(128, output.remaining())
                outputBytes += consumed
                output.position(output.position() + consumed)
            }
            assertTrue("Pipeline failed to drain at speed $speed", pipeline.isEnded)
            if (speed == 1f) assertEquals(16 * 4096, outputBytes)
            else assertTrue("Unexpected audio length at speed $speed: $outputBytes", outputBytes in 30_000..35_000)
        }
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
