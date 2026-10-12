package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.UserItemDataDto
import org.junit.Assert.*
import org.junit.Test

class LibraryCatalogTest {
    private fun ids(items: List<BaseItemDto>) = items.map { it.id }

    @Test fun missingMetadataRemainsLastInBothDirections() {
        val items = listOf(BaseItemDto(id = "none"), BaseItemDto(id = "low", communityRating = 3f), BaseItemDto(id = "high", communityRating = 9f))
        assertEquals(listOf("high", "low", "none"), ids(sortLibrary(items, LibrarySort.COMMUNITY, true, 0)))
        assertEquals(listOf("low", "high", "none"), ids(sortLibrary(items, LibrarySort.COMMUNITY, false, 0)))
    }

    @Test fun fileNamesUseTheLeafOfWindowsAndUnixPaths() {
        val items = listOf(BaseItemDto(id = "b", path = "C:\\media\\b.mkv"), BaseItemDto(id = "a", path = "/zzz/a.mkv"))
        assertEquals(listOf("a", "b"), ids(sortLibrary(items, LibrarySort.FILENAME, false, 0)))
    }

    @Test fun resolutionUsesVideoPixelsAndIgnoresAudioStreams() {
        val items = listOf(
            BaseItemDto(id = "hd", mediaStreams = listOf(MediaStream(type = "Video", width = 1920, height = 1080))),
            BaseItemDto(id = "uhd", mediaStreams = listOf(MediaStream(type = "Video", width = 3840, height = 2160))),
            BaseItemDto(id = "audio", mediaStreams = listOf(MediaStream(type = "Audio", width = 8000, height = 8000)))
        )
        assertEquals(listOf("uhd", "hd", "audio"), ids(sortLibrary(items, LibrarySort.RESOLUTION, true, 0)))
    }

    @Test fun datesCompareChronologicallyAcrossTimeZones() {
        val items = listOf(BaseItemDto(id = "earlier", dateCreated = "2025-01-01T09:00:00+08:00"),
            BaseItemDto(id = "later", dateCreated = "2025-01-01T02:00:00Z"))
        assertEquals(listOf("later", "earlier"), ids(sortLibrary(items, LibrarySort.CREATED, true, 0)))
    }

    @Test fun randomOrderIsStableAcrossInputOrderAndCanBeReshuffled() {
        val items = (1..200).map { BaseItemDto(id = it.toString(), name = "Item $it") }
        val first = sortLibrary(items, LibrarySort.RANDOM, true, 10)
        assertEquals(ids(first), ids(sortLibrary(items.reversed(), LibrarySort.RANDOM, false, 10)))
        assertNotEquals(ids(first), ids(sortLibrary(items, LibrarySort.RANDOM, true, 11)))
        assertEquals(200, first.map { it.id }.distinct().size)
    }

    @Test fun seriesQueueExpandsOnlySelectedSeriesAndDeduplicatesEpisodes() {
        val series = BaseItemDto(id = "series", type = "Series")
        val episode = BaseItemDto(id = "episode", type = "Episode", seriesId = "series")
        val other = BaseItemDto(id = "other", type = "Episode", seriesId = "other-series")
        val photo = BaseItemDto(id = "photo", type = "Photo")
        assertEquals(listOf("episode"), ids(libraryPlayableQueue(listOf(series, episode, photo), listOf(episode, other))))
    }

    @Test fun allMoviesExcludeCollectionsFoldersAndEpisodes() {
        val items = listOf(BaseItemDto(id = "movie", type = "Movie"), BaseItemDto(id = "box", type = "BoxSet"),
            BaseItemDto(id = "folder", type = "Folder"), BaseItemDto(id = "episode", type = "Episode"))
        assertEquals(listOf("movie"), ids(libraryMedia(items, ContentType.MOVIES)))
    }

    @Test fun tiedValuesHaveStableOrderRatherThanServerPageOrder() {
        val items = listOf(BaseItemDto(id = "b", name = "same", productionYear = 2024), BaseItemDto(id = "a", name = "same", productionYear = 2024))
        assertEquals(listOf("a", "b"), ids(sortLibrary(items, LibrarySort.YEAR, true, 0)))
        assertEquals(listOf("a", "b"), ids(sortLibrary(items.reversed(), LibrarySort.YEAR, false, 0)))
    }

    @Test fun filtersKeepNestedDirectoriesContainingMatchingMedia() {
        val root = BaseItemDto(id = "folder", type = "Folder", isFolder = true)
        val nested = BaseItemDto(id = "nested", parentId = "folder", type = "Folder", isFolder = true)
        val film = BaseItemDto(id = "film", parentId = "nested", type = "Movie", genres = listOf("Drama"), userData = UserItemDataDto(played = true))
        val empty = BaseItemDto(id = "empty", type = "Folder", isFolder = true)
        assertEquals(listOf("folder"), ids(filterLibrary(listOf(root, empty), listOf(root, nested, film, empty), setOf("Drama"), true, emptyMap(), true)))
        assertTrue(filterLibrary(listOf(root), listOf(root, nested, film), setOf("Comedy"), null, emptyMap(), true).isEmpty())
    }

    @Test fun collectionFiltersMatchMembersRatherThanMissingCollectionMetadata() {
        val box = BaseItemDto(id = "box", type = "BoxSet")
        val film = BaseItemDto(id = "film", type = "Movie", genres = listOf("Drama"))
        assertEquals(listOf("box"), ids(filterLibrary(listOf(box), listOf(film), setOf("Drama"), null, mapOf("box" to listOf(film)), false)))
        assertTrue(filterLibrary(listOf(box), listOf(film), setOf("Comedy"), null, mapOf("box" to listOf(film)), false).isEmpty())
    }

    @Test fun seriesDirectoryMatchesItsOwnGenresWhenEpisodesDoNotCarryGenres() {
        val series = BaseItemDto(id = "series", type = "Series", isFolder = true, genres = listOf("Drama"))
        val episode = BaseItemDto(id = "episode", type = "Episode", parentId = "series")
        assertEquals(listOf("series"), ids(filterLibrary(listOf(series), listOf(series, episode), setOf("Drama"), null, emptyMap(), true)))
    }
}
