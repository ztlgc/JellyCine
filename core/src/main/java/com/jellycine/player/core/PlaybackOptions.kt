package com.jellycine.player.core

import kotlin.math.log10
import kotlin.math.roundToInt

object PlaybackOptions {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val gainPercentages = listOf(0, 10, 20, 30, 50, 70)

    fun validSpeed(speed: Float): Float = speed.takeIf { it in speeds } ?: 1f
    fun validGain(percent: Int): Int = percent.takeIf { it in gainPercentages } ?: 0
    fun gainMultiplier(percent: Int): Double = 1.0 + validGain(percent) / 100.0

    // Android LoudnessEnhancer uses millibels; UI percentages describe amplitude gain.
    fun gainMillibels(percent: Int): Int = (2000 * log10(gainMultiplier(percent))).roundToInt()

    fun stepSpeed(speed: Float, step: Int): Float =
        speeds[(speeds.indexOf(validSpeed(speed)) + step).coerceIn(speeds.indices)]
}
