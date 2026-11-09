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
    @Volatile private var normalizationEnabled = false
    @Volatile private var dsp: PcmGainDsp? = null
    private var pendingByte: Int? = null
    private var endOfStreamQueued = false
    private var pendingDrain = false

    fun setGainPercent(percent: Int) {
        multiplier = PlaybackOptions.gainMultiplier(percent)
        dsp?.setSettings(multiplier, normalizationEnabled)
    }

    fun setNormalizationEnabled(enabled: Boolean) {
        normalizationEnabled = enabled
        dsp?.setSettings(multiplier, enabled)
    }

    /** Decoded PCM measurements; device/media volume and speakers act after this stage. */
    fun diagnosticsSnapshot(): AudioGainDiagnostics? = dsp?.diagnostics()

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw UnhandledAudioFormatException(inputAudioFormat)
        // Keep the processor in the chain at 0%, so changing gain takes effect without restarting playback.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 pumps empty input while draining; it must not replace pending output.
        if (!inputBuffer.hasRemaining() || endOfStreamQueued) return
        val samples = FloatArray((inputBuffer.remaining() + if (pendingByte == null) 0 else 1) / 2)
        var position = 0
        val littleEndian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
        while (inputBuffer.hasRemaining()) {
            val byte = inputBuffer.get().toInt() and 0xff
            val first = pendingByte
            if (first == null) pendingByte = byte else {
                val value = if (littleEndian) first or (byte shl 8) else (first shl 8) or byte
                samples[position++] = value.toShort().toFloat() / 32768f
                pendingByte = null
            }
        }
        writeOutput(requireNotNull(dsp).process(samples))
    }

    override fun onQueueEndOfStream() {
        // An ended upstream processor may signal EOS repeatedly while Sonic drains.
        if (endOfStreamQueued) return
        endOfStreamQueued = true
        pendingDrain = true
        pendingByte = null
    }

    override fun getOutput(): ByteBuffer {
        val output = super.getOutput()
        if (output.hasRemaining() || !pendingDrain) return output
        // Wait until the previous output has been handed off before reusing its buffer.
        pendingDrain = false
        val tail = dsp?.drain() ?: FloatArray(0)
        if (tail.isNotEmpty()) writeOutput(tail)
        return super.getOutput()
    }

    override fun isEnded(): Boolean = super.isEnded() && !pendingDrain

    override fun onFlush() {
        pendingByte = null
        endOfStreamQueued = false
        pendingDrain = false
        dsp = if (inputAudioFormat == AudioFormat.NOT_SET) null else {
            PcmGainDsp(inputAudioFormat.sampleRate, inputAudioFormat.channelCount).also {
                it.setSettings(multiplier, normalizationEnabled)
            }
        }
    }

    override fun onReset() {
        dsp = null
        pendingByte = null
        endOfStreamQueued = false
        pendingDrain = false
    }

    private fun writeOutput(samples: FloatArray) {
        val output = replaceOutputBuffer(samples.size * 2).order(ByteOrder.nativeOrder())
        for (sample in samples) {
            // The DSP bounds active output; this conversion also preserves every bypassed PCM16 value.
            output.putShort((sample * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
        }
        output.flip()
    }
}
