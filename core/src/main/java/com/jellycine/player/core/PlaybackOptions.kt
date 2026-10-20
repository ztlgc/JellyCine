package com.jellycine.player.core

import kotlin.math.log10
import kotlin.math.roundToInt

object PlaybackOptions {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val gainPercentages = listOf(0, 30, 80, 150, 200, 400, 800, 1500, 3000, 6000, 12000)
    val maxGainMultiplier: Double = 1.0 + gainPercentages.max() / 100.0

    fun validSpeed(speed: Float): Float = speed.takeIf { it in speeds } ?: 1f
    fun validGain(percent: Int): Int = percent.takeIf { it in gainPercentages } ?: 0

    // Keep existing enabled preferences on the closest new enabled preset.
    fun restoreGain(percent: Int): Int = when (percent) {
        10, 20, 50 -> 30
        70 -> 80
        else -> validGain(percent)
    }

    fun gainMultiplier(percent: Int): Double = 1.0 + validGain(percent) / 100.0
    fun gainDecibels(percent: Int): Double = 20 * log10(gainMultiplier(percent))

    // Android LoudnessEnhancer uses millibels; UI percentages describe amplitude gain.
    fun gainMillibels(percent: Int): Int = (100 * gainDecibels(percent)).roundToInt()

    fun stepSpeed(speed: Float, step: Int): Float =
        speeds[(speeds.indexOf(validSpeed(speed)) + step).coerceIn(speeds.indices)]
}
