package com.jellycine.player.video

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import com.google.common.primitives.ImmutableIntArray
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PcmOnlyAudioSinkTest {
    private val pcm = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_16BIT)
        .setSampleRate(48_000)
        .setChannelCount(2)
        .build()

    private class RecordingSink {
        val calls = mutableListOf<String>()
        val configurations = mutableListOf<AudioSink.AudioSinkConfig>()
        val sink = Proxy.newProxyInstance(
            AudioSink::class.java.classLoader,
            arrayOf(AudioSink::class.java)
        ) { _, method, arguments ->
            calls += method.name
            when (method.name) {
                "configure" -> {
                    check(arguments!!.size == 1) { "Expected the current AudioSinkConfig API" }
                    configurations += arguments[0] as AudioSink.AudioSinkConfig
                    null
                }
                "supportsFormat" -> true
                "getFormatSupport" -> AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
                else -> error("Unexpected sink call: ${method.name}")
            }
        } as AudioSink
    }

    @Test fun currentConfigureReachesTheDelegateOnceAndPreservesItsConfiguration() {
        val recording = RecordingSink()
        val config = AudioSink.AudioSinkConfig.Builder(pcm)
            .setPreferredBufferSizeOverride(8192)
            .setOutputChannelMapping(ImmutableIntArray.of(1, 0))
            .build()

        PcmOnlyAudioSink(recording.sink).configure(config)

        assertEquals(listOf("configure"), recording.calls)
        assertSame(config, recording.configurations.single())
    }

    @Suppress("DEPRECATION")
    @Test fun legacyConfigureReachesTheDelegateOnceWithoutRecursiveDefaults() {
        val recording = RecordingSink()

        PcmOnlyAudioSink(recording.sink).configure(pcm, 4096, intArrayOf(0, 1))

        assertEquals(listOf("configure"), recording.calls)
        val config = recording.configurations.single()
        assertSame(pcm, config.format)
        assertEquals(4096, config.preferredBufferSizeOverride)
        assertArrayEquals(intArrayOf(0, 1), config.outputChannelMapping!!.toArray())
    }

    @Test fun pcmSupportUsesTheDelegateButCompressedAudioCannotBypassTheProcessor() {
        val recording = RecordingSink()
        val wrapper = PcmOnlyAudioSink(recording.sink)
        val compressed = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).build()

        assertTrue(wrapper.supportsFormat(pcm))
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY, wrapper.getFormatSupport(pcm))
        assertFalse(wrapper.supportsFormat(compressed))
        assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, wrapper.getFormatSupport(compressed))
        assertEquals(listOf("supportsFormat", "getFormatSupport"), recording.calls)
    }

    @Test fun offloadStaysDisabledForPcmAndCompressedAudio() {
        val recording = RecordingSink()
        val wrapper = PcmOnlyAudioSink(recording.sink)

        assertSame(AudioOffloadSupport.DEFAULT_UNSUPPORTED, wrapper.getFormatOffloadSupport(pcm))
        assertSame(AudioOffloadSupport.DEFAULT_UNSUPPORTED,
            wrapper.getFormatOffloadSupport(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3).build()))
        assertTrue(recording.calls.isEmpty())
    }
}
