package com.jellycine.app.ui.screens.player

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.VideoWatchSession
import com.jellycine.data.model.VideoWatchSpan
import org.junit.Assert.*
import org.junit.Test

class VideoWatchTrackerTest {
    private var monotonic = 0L
    private var wall = 1_000_000L
    private val opens = mutableListOf<VideoWatchSession>()
    private val spans = mutableListOf<VideoWatchSpan>()
    private val tracker = VideoWatchTracker({ monotonic }, { wall }, { opens += it }, { spans += it })
    private val video = BaseItemDto(id = "video", name = "普通视频", type = "Video")
    private fun advance(ms: Long) { monotonic += ms; wall += ms }
    private fun total() = spans.sumOf { it.endMs - it.startMs }

    @Test fun onlyOrdinaryVideoCanCreateRecords() {
        listOf("Movie", "Episode", "Audio", "Photo", "Folder", null).forEach { type ->
            tracker.open("scope", video.copy(type = type), wall)
            tracker.setPlaying(true)
            advance(1000)
            tracker.close()
        }
        assertTrue(opens.isEmpty())
        assertTrue(spans.isEmpty())
        tracker.open("scope", video, wall)
        assertEquals(1, opens.size)
    }

    @Test fun pausesBufferingAndBackgroundTimeAreExcludedWhileShortPlaybackIsKept() {
        tracker.open("scope", video, wall)
        advance(10_000) // Initial buffering.
        tracker.setPlaying(true)
        advance(450)
        tracker.setPlaying(false)
        advance(60_000) // Pause, buffering or background.
        tracker.setPlaying(true)
        advance(350)
        tracker.close()
        assertEquals(800L, total())
        assertEquals(1, opens.size)
    }

    @Test fun checkpointsAndExitNeverCountTheSameTimeTwice() {
        tracker.open("scope", video, wall)
        tracker.setPlaying(true)
        repeat(3) { advance(5_000); tracker.checkpoint() }
        advance(123)
        tracker.close()
        tracker.close()
        assertEquals(15_123L, total())
        assertEquals(spans[0].endMs, spans[1].startMs)
        assertEquals(spans[1].endMs, spans[2].startMs)
    }

    @Test fun trackAndEngineRestartsKeepOneOpenButARealReopenCountsAgain() {
        tracker.open("scope", video, wall)
        tracker.setPlaying(true)
        advance(2_000)
        tracker.setPlaying(false)
        tracker.open("scope", video, wall, continueSession = true)
        advance(20_000) // Engine startup.
        tracker.setPlaying(true)
        advance(3_000)
        tracker.close()
        assertEquals(1, opens.size)
        assertEquals(5_000L, total())
        assertEquals(1, spans.map { it.sessionId }.distinct().size)
        tracker.open("scope", video, wall)
        assertEquals(2, opens.size)
        assertNotEquals(opens[0].id, opens[1].id)
    }

    @Test fun speedAndSeekingCannotInflateElapsedTimeAndWallClockChangesAreHarmless() {
        tracker.open("scope", video, wall)
        tracker.setPlaying(true)
        // No media-position or speed input exists: 10 seconds at 2x still takes 10 seconds.
        monotonic += 10_000
        wall += 3_600_000 // Manual system-clock adjustment.
        tracker.checkpoint()
        monotonic += 2_000
        wall -= 7_200_000
        tracker.close()
        assertEquals(12_000L, total())
        assertEquals(1_000_000L, spans.first().startMs)
    }

    @Test fun changingAccountOrVideoCannotReuseThePreviousOpen() {
        tracker.open("first", video, wall)
        tracker.setPlaying(true)
        advance(1000)
        tracker.open("second", video, wall, continueSession = true)
        tracker.open("second", video.copy(id = "another"), wall, continueSession = true)
        assertEquals(listOf("first", "second", "second"), opens.map { it.scopeKey })
        assertEquals(listOf("video", "video", "another"), opens.map { it.itemId })
        assertEquals(1000L, total())
    }
}
