package com.jellycine.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoSearchTest {
    @Test fun homeAndMusicVideosDoNotNeedMovieMetadata() {
        assertTrue(BaseItemDto(type = "Video").isVideoSearchResult())
        assertTrue(BaseItemDto(type = "MusicVideo").isVideoSearchResult())
    }

    @Test fun moviesEpisodesAndSeriesRemainSearchable() {
        for (type in listOf("Movie", "Episode", "Series")) {
            assertTrue(BaseItemDto(type = type).isVideoSearchResult())
        }
        assertTrue(BaseItemDto(type = "Series", isFolder = true).isVideoSearchResult())
    }

    @Test fun mediaTypeSupportsAdditionalVideoSubtypes() {
        assertTrue(BaseItemDto(type = "CustomVideo", mediaType = "video").isVideoSearchResult())
        assertTrue(BaseItemDto(type = "trailer").isVideoSearchResult())
    }

    @Test fun photosAudioAndLibraryFoldersAreExcluded() {
        for (type in listOf("Photo", "Audio", "Folder", "PhotoAlbum", "Season")) {
            assertFalse(BaseItemDto(type = type).isVideoSearchResult())
        }
        assertFalse(BaseItemDto(isFolder = true, mediaType = "Video").isVideoSearchResult())
        assertFalse(BaseItemDto().isVideoSearchResult())
    }
}
