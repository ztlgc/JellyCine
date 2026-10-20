package com.jellycine.player.core

/** A held speed override never replaces the user's saved playback speed. */
class HoldPlaybackSpeed {
    @Volatile
    private var selectedSpeed: Float = 1f

    val preferredSpeed: Float
        get() = selectedSpeed

    @Volatile
    var isActive: Boolean = false
        private set

    val effectiveSpeed: Float
        get() = if (isActive) 2f else preferredSpeed

    fun setPreferredSpeed(speed: Float) {
        selectedSpeed = PlaybackOptions.validSpeed(speed)
    }

    fun start() {
        isActive = true
    }

    fun stop() {
        isActive = false
    }
}
