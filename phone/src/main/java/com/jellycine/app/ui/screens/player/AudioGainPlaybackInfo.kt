package com.jellycine.app.ui.screens.player

/** Measurements are taken from decoded PCM, before Android device volume and speaker processing. */
data class AudioGainPlaybackInfo(
    val engine: String,
    val configuredGainDb: Double,
    val normalizationEnabled: Boolean,
    val inputRmsDbfs: Double? = null,
    val outputRmsDbfs: Double? = null,
    val outputPeakDbfs: Double? = null,
    val processedFrames: Long? = null,
    val inputProcessedFrames: Long? = null,
    val measurementPaired: Boolean = false,
    val limitedFrames: Long? = null,
    val systemVolumeIndex: Int,
    val systemVolumeMax: Int,
    val outputFormat: String? = null
) {
    // Silence and uninitialized meters cannot establish an actual gain ratio.
    val measuredGainDb: Double?
        get() = if (measurementPaired && inputRmsDbfs != null && outputRmsDbfs != null &&
            inputRmsDbfs.isFinite() && outputRmsDbfs.isFinite() && inputRmsDbfs > -90.0
        ) outputRmsDbfs - inputRmsDbfs else null
}
