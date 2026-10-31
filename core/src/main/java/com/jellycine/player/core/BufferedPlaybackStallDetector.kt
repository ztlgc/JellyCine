package com.jellycine.player.core

/** Monotonic playback observation that survives READY/BUFFERING oscillation. */
class BufferedPlaybackStallDetector(
    private val timeoutMs: Long = 6_000L,
    private val minimumBufferMs: Long = 3_000L
) {
    private var lastProgressMs: Long? = null
    private var anchorPositionMs = 0L
    private var anchorRenderedFrames: Int? = null

    fun reset() {
        lastProgressMs = null
        anchorRenderedFrames = null
    }

    /** Returns elapsed stalled time, or null when progress/rebuffer conditions are normal. */
    fun observe(
        nowMs: Long,
        eligible: Boolean,
        positionMs: Long,
        bufferedMs: Long,
        renderedFrames: Int?
    ): Long? {
        if (!eligible || bufferedMs < minimumBufferMs) {
            reset()
            return null
        }
        val previousProgressMs = lastProgressMs
        val framesChanged = renderedFrames != null && anchorRenderedFrames != null &&
            renderedFrames != anchorRenderedFrames
        if (previousProgressMs == null || nowMs < previousProgressMs ||
            positionMs < anchorPositionMs || positionMs - anchorPositionMs >= 250L || framesChanged) {
            lastProgressMs = nowMs
            anchorPositionMs = positionMs
            anchorRenderedFrames = renderedFrames
            return null
        }
        return (nowMs - previousProgressMs).takeIf { it >= timeoutMs }
    }
}
