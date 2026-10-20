package com.jellycine.app.player.mpv

import com.jellycine.player.core.PlaybackOptions
import org.jellycine.mpv.MPVLib
import java.util.Locale

/** Statistics measured by astats on decoded PCM before and after the DSP graph. */
data class MpvAudioGainDiagnostics(
    val inputRmsDbfs: Double?,
    val outputRmsDbfs: Double?,
    val inputPeakDbfs: Double?,
    val outputPeakDbfs: Double?,
    val outputFormat: String,
    val processedFrames: Long?,
    val inputProcessedFrames: Long?,
    val measurementPaired: Boolean
) {
    companion object {
        internal fun fromPcmStats(stats: DoubleArray, outputFormat: String): MpvAudioGainDiagnostics? {
            if (stats.size != 6) return null
            fun measured(index: Int) = stats[index].takeIf { it.isFinite() || it == Double.NEGATIVE_INFINITY }
            fun frames(index: Int) = stats[index].takeIf { it.isFinite() && it > 0.0 }?.toLong()
            val inputFrames = frames(4)
            val outputFrames = frames(5)
            return MpvAudioGainDiagnostics(
                inputRmsDbfs = measured(0),
                outputRmsDbfs = measured(2),
                inputPeakDbfs = measured(1),
                outputPeakDbfs = measured(3),
                outputFormat = outputFormat,
                processedFrames = outputFrames,
                inputProcessedFrames = inputFrames,
                measurementPaired = inputFrames != null && inputFrames == outputFrames
            )
        }
    }
}

internal object MpvAudioGain {
    const val INPUT_LABEL = "jellycine_gain_input"
    const val OUTPUT_LABEL = "jellycine_gain_output"
    private const val DSP_LABEL = "jellycine_gain_dsp"
    private const val ASTATS = "astats=metadata=1:reset=0:measure_perchannel=none:" +
        "measure_overall=RMS_level+Peak_level+Number_of_samples"

    fun filters(percent: Int, normalize: Boolean): String {
        val multiplier = PlaybackOptions.gainMultiplier(percent).toDouble()
        val processing = buildList {
            // Floating point avoids clipping before the final linked-channel limiter.
            add("aformat=sample_fmts=flt")
            if (normalize) {
                val maxExtra = minOf(4.0, PlaybackOptions.maxGainMultiplier / multiplier).coerceAtLeast(1.0)
                // Dynaudnorm uses a 250 ms smoothing window. Gate on original PCM,
                // before the selected manual gain, so quiet noise is not promoted.
                add("dynaudnorm=f=50:g=5:p=0.96:m=${decimal(maxExtra)}:" +
                    "r=0.125:n=1:c=0:b=0:s=0:t=0.00316227766")
            }
            add("volume=${decimal(multiplier)}:precision=float")
            if (percent != 0 || normalize) {
                add("alimiter=limit=0.96:attack=5:release=150:level=0:latency=1")
            }
        }.joinToString(",")
        return "@$INPUT_LABEL:lavfi=[$ASTATS]," +
            "@$DSP_LABEL:lavfi=[$processing]," +
            "@$OUTPUT_LABEL:lavfi=[$ASTATS]"
    }

    private fun decimal(value: Double) = String.format(Locale.ROOT, "%.9f", value)
}

/**
 * The AAR's original command JNI discards mpv's return code. This narrow bridge
 * returns it and reads PCM metadata, using the pinned libmpv 1.0.4 native layout.
 * The native build checks that AAR's SHA-256 before compiling this bridge.
 */
internal object MpvAudioNative {
    private val loaded = runCatching { System.loadLibrary("jellycine_mpv_audio") }.isSuccess

    fun command(mpv: MPVLib, arguments: Array<String>): Int =
        if (loaded) runCatching { nativeCommand(mpv, arguments) }.getOrDefault(-1) else -1

    fun pcmStats(mpv: MPVLib): DoubleArray? =
        if (loaded) runCatching { nativePcmStats(mpv) }.getOrNull() else null

    private external fun nativeCommand(mpv: MPVLib, arguments: Array<String>): Int
    private external fun nativePcmStats(mpv: MPVLib): DoubleArray?
}
