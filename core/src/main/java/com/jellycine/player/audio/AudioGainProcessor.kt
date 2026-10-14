package com.jellycine.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.jellycine.player.core.PlaybackOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Amplifies decoded PCM before it reaches AudioTrack, independently of device effects. */
@UnstableApi
class AudioGainProcessor : BaseAudioProcessor() {
    @Volatile private var multiplier = 1.0

    fun setGainPercent(percent: Int) {
        multiplier = PlaybackOptions.gainMultiplier(percent)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw UnhandledAudioFormatException(inputAudioFormat)
        // Keep the processor in the chain at 0%, so changing gain takes effect without restarting playback.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())
        val gain = multiplier
        while (inputBuffer.remaining() >= 2) {
            val sample = (inputBuffer.short * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.putShort(sample.toShort())
        }
        output.flip()
    }
}
