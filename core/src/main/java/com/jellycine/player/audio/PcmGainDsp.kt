package com.jellycine.player.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Paired decoded-PCM measurements, before AudioTrack/device volume; these are not speaker loudness.
 * RMS/peaks describe the latest complete 0.5 s window (the current partial window until one exists).
 * A settings switch starts a fresh window when its first frame reaches the output.
 */
data class AudioGainDiagnostics(
    val sampleRate: Int,
    val channelCount: Int,
    val lookAheadFrames: Int,
    val inputFrames: Long = 0,
    val outputFrames: Long = 0,
    val inputRms: Double = 0.0,
    val inputPeak: Double = 0.0,
    val outputRms: Double = 0.0,
    val outputPeak: Double = 0.0,
    val limitedFrames: Long = 0,
    val configuredGainMultiplier: Double = 1.0,
    val normalizationEnabled: Boolean = false,
    val normalizationMultiplier: Double = 1.0,
    val limiterMultiplier: Double = 1.0,
    val measurementFrames: Long = 0,
    val measurementEndOutputFrame: Long = 0,
)

/**
 * Streaming, channel-linked gain/limiter for interleaved PCM in [-1, 1].
 *
 * It always holds 5 ms of audio, including when bypassed, so live switches do not change latency.
 * Call [drain] at EOS and [reset] on seeks. Settings and diagnostic reads may run off the audio
 * thread; process/drain/reset themselves belong to the single audio thread.
 */
class PcmGainDsp(val sampleRate: Int, val channelCount: Int) {
    init {
        require(sampleRate > 0)
        require(channelCount > 0)
    }

    private data class Settings(val multiplier: Double, val normalize: Boolean)

    val lookAheadFrames = max(1, (sampleRate * 0.005).roundToInt())
    private val ringSize = lookAheadFrames + 1
    private val audio = FloatArray(ringSize * channelCount)
    private val framePeaks = DoubleArray(ringSize)
    private val rawFramePeaks = DoubleArray(ringSize)
    private val rawFrameEnergies = DoubleArray(ringSize)
    private val frameSettingsVersions = LongArray(ringSize)
    private val frameConfiguredGains = DoubleArray(ringSize)
    private val frameNormalizations = BooleanArray(ringSize)
    private val frameNormalizingGains = DoubleArray(ringSize)
    private val bypass = BooleanArray(ringSize)
    private val frameSampleCounts = IntArray(ringSize)
    private val partialFrame = FloatArray(channelCount)
    private var partialSamples = 0
    private var readFrame = 0
    private var writeFrame = 0
    private var queuedFrames = 0
    private var inputFrames = 0L
    private var outputFrames = 0L
    private var limitedFrames = 0L
    private data class Measurement(
        val samples: Long = 0, val frames: Long = 0, val endOutputFrame: Long = 0,
        val inputEnergy: Double = 0.0, val outputEnergy: Double = 0.0,
        val inputPeak: Double = 0.0, val outputPeak: Double = 0.0,
    )
    private var measurementSamples = 0L
    private var measurementFrames = 0L
    private var measurementEndOutputFrame = 0L
    private var measurementInputEnergy = 0.0
    private var measurementOutputEnergy = 0.0
    private var measurementInputPeak = 0.0
    private var measurementOutputPeak = 0.0
    private var completedMeasurement: Measurement? = null
    private val measurementWindowFrames = max(1, sampleRate / 2)
    private var settingsVersion = 0L
    private var outputSettingsVersion = 0L
    private var outputConfiguredGain = 1.0
    private var outputNormalizationEnabled = false
    private var outputNormalizingGain = 1.0
    private var outputLimiterGain = 1.0

    // A monotonic deque provides the future peak without scanning the lookahead for each sample.
    private val peakIndices = LongArray(ringSize + 1)
    private val peakValues = DoubleArray(ringSize + 1)
    private var peakHead = 0
    private var peakTail = 0
    private val attack = coefficient(0.0005)
    private val release = coefficient(0.150)
    private val rmsSmoothing = coefficient(0.050)
    private val normalizeRise = coefficient(2.0)
    private val normalizeFall = coefficient(0.300)
    private val gainRampFrames = max(1, (sampleRate * 0.040).roundToInt())
    private var gainRampRemaining = 0
    private var gainStep = 0.0
    private var normalizationRampRemaining = 0
    private var normalizationStep = 0.0
    private var fixedGain = 1.0
    private var normalizingGain = 1.0
    private var smoothedEnergy = 0.0
    private var limiterGain = 1.0
    @Volatile private var requested = Settings(1.0, false)
    private var applied = requested
    @Volatile private var snapshot = AudioGainDiagnostics(sampleRate, channelCount, lookAheadFrames)

    fun setSettings(gainMultiplier: Double, normalize: Boolean = false) {
        val safeMultiplier = gainMultiplier.takeIf { it.isFinite() }?.coerceIn(1.0, MAX_TOTAL_GAIN) ?: 1.0
        requested = Settings(safeMultiplier, normalize)
    }

    fun diagnostics(): AudioGainDiagnostics = snapshot

    /** Arbitrary chunk sizes are accepted; an incomplete channel frame carries into the next call. */
    fun process(samples: FloatArray): FloatArray {
        applySettings()
        val output = FloatArray(samples.size + channelCount)
        var outputPosition = 0
        for (sample in samples) {
            partialFrame[partialSamples++] = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
            if (partialSamples == channelCount) {
                enqueueFrame(channelCount)
                partialSamples = 0
                if (queuedFrames > lookAheadFrames) outputPosition += emitFrame(output, outputPosition)
            }
        }
        publishDiagnostics()
        return output.copyOf(outputPosition)
    }

    /** Emits every remaining sample once, including streams shorter than the lookahead. */
    fun drain(): FloatArray {
        applySettings()
        val output = FloatArray(queuedFrames * channelCount + partialSamples)
        var outputPosition = 0
        if (partialSamples > 0) {
            enqueueFrame(partialSamples)
            partialSamples = 0
        }
        while (queuedFrames > 0) outputPosition += emitFrame(output, outputPosition)
        publishDiagnostics()
        return output.copyOf(outputPosition)
    }

    /** Discards pre-seek samples and envelopes, but preserves the selected settings. */
    fun reset() {
        audio.fill(0f)
        partialFrame.fill(0f)
        partialSamples = 0
        readFrame = 0
        writeFrame = 0
        queuedFrames = 0
        inputFrames = 0
        outputFrames = 0
        limitedFrames = 0
        clearMeasurementWindow()
        completedMeasurement = null
        settingsVersion = 0
        outputSettingsVersion = 0
        outputConfiguredGain = requested.multiplier
        outputNormalizationEnabled = requested.normalize
        outputNormalizingGain = 1.0
        outputLimiterGain = 1.0
        peakHead = 0
        peakTail = 0
        fixedGain = requested.multiplier
        normalizingGain = 1.0
        smoothedEnergy = 0.0
        limiterGain = 1.0
        gainRampRemaining = 0
        normalizationRampRemaining = 0
        applied = requested
        publishDiagnostics()
    }

    private fun applySettings() {
        val settings = requested
        if (settings != applied) settingsVersion++
        if (inputFrames == 0L) {
            fixedGain = settings.multiplier
            gainRampRemaining = 0
        } else if (settings.multiplier != applied.multiplier) {
            gainRampRemaining = gainRampFrames
            gainStep = (settings.multiplier - fixedGain) / gainRampFrames
        }
        if (!settings.normalize && applied.normalize) {
            normalizationRampRemaining = gainRampFrames
            normalizationStep = (1.0 - normalizingGain) / gainRampFrames
        } else if (settings.normalize) normalizationRampRemaining = 0
        applied = settings
    }

    private fun enqueueFrame(sampleCount: Int) {
        var energy = 0.0
        var rawPeak = 0.0
        for (channel in 0 until sampleCount) {
            val sample = partialFrame[channel].toDouble()
            energy += sample * sample
            rawPeak = max(rawPeak, abs(sample))
        }
        smoothedEnergy += rmsSmoothing * (energy / sampleCount - smoothedEnergy)
        if (gainRampRemaining > 0) {
            fixedGain += gainStep
            if (--gainRampRemaining == 0) fixedGain = applied.multiplier
        }
        val rms = sqrt(smoothedEnergy)
        // Below -50 dBFS the normalizer has no upward target. It never attempts to fill silence.
        val normalizingTarget = if (applied.normalize && rms >= NORMALIZE_NOISE_FLOOR) {
            (NORMALIZE_TARGET_RMS / rms).coerceIn(1.0, MAX_NORMALIZING_GAIN)
        } else 1.0
        val smoothing = when {
            normalizingTarget > normalizingGain -> normalizeRise
            else -> normalizeFall
        }
        if (normalizationRampRemaining > 0) {
            normalizingGain += normalizationStep
            if (--normalizationRampRemaining == 0) normalizingGain = 1.0
        } else if (applied.normalize) normalizingGain += smoothing * (normalizingTarget - normalizingGain)
        else normalizingGain = 1.0
        val totalGain = min(MAX_TOTAL_GAIN, fixedGain * normalizingGain)
        val isBypass = totalGain == 1.0
        var peak = 0.0
        val offset = writeFrame * channelCount
        for (channel in 0 until sampleCount) {
            val value = if (isBypass) partialFrame[channel] else (partialFrame[channel] * totalGain).toFloat()
            audio[offset + channel] = value
            peak = max(peak, abs(value.toDouble()))
        }
        frameSampleCounts[writeFrame] = sampleCount
        rawFrameEnergies[writeFrame] = energy
        rawFramePeaks[writeFrame] = rawPeak
        frameSettingsVersions[writeFrame] = settingsVersion
        frameConfiguredGains[writeFrame] = applied.multiplier
        frameNormalizations[writeFrame] = applied.normalize
        frameNormalizingGains[writeFrame] = totalGain / fixedGain
        framePeaks[writeFrame] = peak
        bypass[writeFrame] = isBypass
        val limitablePeak = if (isBypass) 0.0 else peak
        while (peakHead != peakTail && peakValues[previous(peakTail)] <= limitablePeak) peakTail = previous(peakTail)
        peakIndices[peakTail] = inputFrames
        peakValues[peakTail] = limitablePeak
        peakTail = next(peakTail)
        inputFrames++
        queuedFrames++
        writeFrame = (writeFrame + 1) % ringSize
    }

    private fun emitFrame(output: FloatArray, outputPosition: Int): Int {
        if (frameSettingsVersions[readFrame] != outputSettingsVersion) {
            // Begin the new measurement at its actual output position, after the lookahead delay.
            outputSettingsVersion = frameSettingsVersions[readFrame]
            clearMeasurementWindow()
            completedMeasurement = null
        }
        outputConfiguredGain = frameConfiguredGains[readFrame]
        outputNormalizationEnabled = frameNormalizations[readFrame]
        outputNormalizingGain = frameNormalizingGains[readFrame]
        val futurePeak = if (peakHead == peakTail) 0.0 else peakValues[peakHead]
        val target = if (futurePeak > LIMIT_CEILING) LIMIT_CEILING / futurePeak else 1.0
        limiterGain += (if (target < limiterGain) attack else release) * (target - limiterGain)
        val isBypass = bypass[readFrame]
        val peak = framePeaks[readFrame]
        // A final linked gain bound covers the first transient of very short/EOS streams as well.
        // All channels share this coefficient: there is no independent sample clipping/distortion.
        if (!isBypass && peak > 0.0) limiterGain = min(limiterGain, LIMIT_CEILING / peak)
        val appliedLimiter = if (isBypass) 1.0 else limiterGain
        outputLimiterGain = appliedLimiter
        if (!isBypass && appliedLimiter < 0.999999) limitedFrames++
        val offset = readFrame * channelCount
        val sampleCount = frameSampleCounts[readFrame]
        var emittedEnergy = 0.0
        var emittedPeak = 0.0
        for (channel in 0 until sampleCount) {
            val value = if (isBypass) audio[offset + channel] else (audio[offset + channel] * appliedLimiter).toFloat()
            output[outputPosition + channel] = value
            val doubleValue = value.toDouble()
            emittedEnergy += doubleValue * doubleValue
            emittedPeak = max(emittedPeak, abs(doubleValue))
        }
        measurementSamples += sampleCount
        measurementFrames++
        measurementEndOutputFrame = outputFrames + 1
        measurementInputEnergy += rawFrameEnergies[readFrame]
        measurementOutputEnergy += emittedEnergy
        measurementInputPeak = max(measurementInputPeak, rawFramePeaks[readFrame])
        measurementOutputPeak = max(measurementOutputPeak, emittedPeak)
        if (measurementFrames >= measurementWindowFrames) {
            completedMeasurement = currentMeasurement()
            clearMeasurementWindow()
        }
        if (peakHead != peakTail && peakIndices[peakHead] == outputFrames) peakHead = next(peakHead)
        outputFrames++
        queuedFrames--
        readFrame = (readFrame + 1) % ringSize
        return sampleCount
    }

    private fun publishDiagnostics() {
        val values = completedMeasurement ?: currentMeasurement()
        snapshot = AudioGainDiagnostics(
            sampleRate, channelCount, lookAheadFrames, inputFrames, outputFrames,
            if (values.samples == 0L) 0.0 else sqrt(values.inputEnergy / values.samples), values.inputPeak,
            if (values.samples == 0L) 0.0 else sqrt(values.outputEnergy / values.samples), values.outputPeak,
            limitedFrames,
            if (outputFrames > 0) outputConfiguredGain else applied.multiplier,
            if (outputFrames > 0) outputNormalizationEnabled else applied.normalize,
            outputNormalizingGain, outputLimiterGain,
            values.frames, values.endOutputFrame,
        )
    }

    private fun currentMeasurement() = Measurement(
        measurementSamples, measurementFrames, measurementEndOutputFrame,
        measurementInputEnergy, measurementOutputEnergy, measurementInputPeak, measurementOutputPeak,
    )

    private fun clearMeasurementWindow() {
        measurementSamples = 0
        measurementFrames = 0
        measurementEndOutputFrame = 0
        measurementInputEnergy = 0.0
        measurementOutputEnergy = 0.0
        measurementInputPeak = 0.0
        measurementOutputPeak = 0.0
    }

    private fun coefficient(seconds: Double) = 1.0 - exp(-1.0 / (sampleRate * seconds))
    private fun next(position: Int) = (position + 1) % peakIndices.size
    private fun previous(position: Int) = (position + peakIndices.size - 1) % peakIndices.size

    companion object {
        const val LIMIT_CEILING = 0.96
        val MAX_TOTAL_GAIN = com.jellycine.player.core.PlaybackOptions.maxGainMultiplier
        const val MAX_NORMALIZING_GAIN = 4.0
        const val NORMALIZE_TARGET_RMS = 0.125
        const val NORMALIZE_NOISE_FLOOR = 0.0031622776601683794
    }
}
