package com.jellycine.player.video

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * Requires decoded PCM so passthrough/offload cannot bypass the gain processor.
 *
 * Use Media3's forwarding class: Kotlin interface delegation skips Java default methods.
 * In particular, AudioSink's two default configure overloads call each other unless one is
 * implemented, which caused StackOverflowError on the ExoPlayer playback thread.
 */
@UnstableApi
internal class PcmOnlyAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    override fun supportsFormat(format: Format): Boolean =
        format.sampleMimeType == MimeTypes.AUDIO_RAW && super.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (format.sampleMimeType == MimeTypes.AUDIO_RAW) super.getFormatSupport(format)
        else AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
        AudioOffloadSupport.DEFAULT_UNSUPPORTED
}
