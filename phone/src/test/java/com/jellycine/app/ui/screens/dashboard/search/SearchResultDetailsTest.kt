package com.jellycine.app.ui.screens.dashboard.search

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaSourceInfo
import com.jellycine.data.model.MediaStream
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SearchResultDetailsTest {
    @Test fun serverRatingIsDisplayedWithVideoDetails() {
        val item = BaseItemDto(communityRating = 7.5f, runTimeTicks = 37_230_000_000L,
            mediaStreams = listOf(MediaStream(type = "Video", width = 1920, height = 1080)))
        assertEquals(listOf("★ ${String.format(Locale.getDefault(), "%.1f", 7.5f)}", "1920×1080", "1:02:03"), searchResultDetails(item))
    }

    @Test fun sourcesSupplyDimensionsAndRuntimeWhenTopLevelFieldsAreMissing() {
        val item = BaseItemDto(mediaStreams = listOf(MediaStream(type = "Audio", width = 1920, height = 1080)),
            mediaSources = listOf(MediaSourceInfo(runTimeTicks = 450_000_000L,
                mediaStreams = listOf(MediaStream(type = "Video", width = 1080, height = 1920)))))
        assertEquals(listOf("1080×1920", "0:45"), searchResultDetails(item))
    }

    @Test fun absentOrInvalidMetadataDoesNotProduceMisleadingValues() {
        assertEquals(emptyList<String>(), searchResultDetails(BaseItemDto(communityRating = Float.NaN,
            runTimeTicks = -1, width = 0, height = 1080)))
    }

    @Test fun communityRatingAndItemDimensionsRemainAvailableWithoutStreams() {
        val item = BaseItemDto(communityRating = 8.5f, width = 1280, height = 720, runTimeTicks = 900_000_000L)
        assertEquals(listOf("★ ${String.format(Locale.getDefault(), "%.1f", 8.5f)}", "1280×720", "1:30"), searchResultDetails(item))
    }
}
