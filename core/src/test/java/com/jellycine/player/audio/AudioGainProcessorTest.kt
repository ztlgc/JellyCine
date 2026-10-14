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

    private fun output(processor: AudioGainProcessor, vararg samples: Int): List<Int> {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it.toShort()) }
        input.flip()
        processor.queueInput(input)
        assertFalse(input.hasRemaining())
        val output = processor.output.order(ByteOrder.nativeOrder())
        return buildList { while (output.hasRemaining()) add(output.short.toInt()) }
    }

    @Test fun eachGainActuallyAmplifiesTheOutputSamples() {
        val processor = processor()
        listOf(10 to 1100, 20 to 1200, 30 to 1300, 50 to 1500, 70 to 1700).forEach { (percent, expected) ->
            processor.setGainPercent(percent)
            assertEquals(listOf(expected, -expected, 0, expected * 2), output(processor, 1000, -1000, 0, 2000))
        }
    }

    @Test fun gainCanChangeAndTurnOffWithoutFlushingAudio() {
        val processor = processor()
        assertTrue(processor.isActive)
        processor.setGainPercent(70)
        assertEquals(listOf(1700, -1700), output(processor, 1000, -1000))
        processor.setGainPercent(0)
        assertEquals(listOf(1000, -1000), output(processor, 1000, -1000))
        assertTrue(processor.isActive)
    }

    @Test fun amplifiedPeaksClampInsteadOfWrappingIntoNoise() {
        val processor = processor()
        processor.setGainPercent(70)
        assertEquals(listOf(32767, -32768), output(processor, 30_000, -30_000))
    }
}
