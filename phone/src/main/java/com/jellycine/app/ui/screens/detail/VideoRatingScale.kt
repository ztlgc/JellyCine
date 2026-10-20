package com.jellycine.app.ui.screens.detail

import kotlin.math.roundToInt

/** Keep slider selection in integer tenths, including the 5.9 / 6.0 boundary. */
internal object VideoRatingScale {
    val highScores = 60..100
    val lowScores = 10..59

    fun fromServer(score: Float?): Int? = score
        ?.takeIf { it.isFinite() && it in 1f..10f }
        ?.let { (it * 10).roundToInt() }

    fun toServer(tenths: Int): Float = tenths / 10f
}
