package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.QueryResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryPhotoFilteringTest {
    @get:Rule val temporary = TemporaryFolder()

    private val video = BaseItemDto(id = "video", type = "Video")
    private val photo = BaseItemDto(id = "photo", type = "Photo")
    private val folder = BaseItemDto(id = "folder", type = "Folder", isFolder = true)

    @Test fun photoTypesAndMediaMetadataAreHiddenWhileDirectoriesRemainNavigable() {
        val album = BaseItemDto(id = "album", type = "PhotoAlbum", mediaType = "Photo")
        val metadataPhoto = BaseItemDto(id = "metadata", mediaType = "pHoTo")
        val upperCasePhoto = BaseItemDto(id = "upper", type = "PHOTO")
        val mixed = listOf(photo, metadataPhoto, upperCasePhoto, folder, album, video)
        assertEquals(listOf(folder, album, video), withoutLibraryPhotos(mixed))
        assertEquals(listOf(video), libraryMedia(mixed, ContentType.HOME_MEDIA))
        assertEquals(listOf(video), libraryMedia(mixed, ContentType.ALL))
        assertEquals(listOf(folder, album, video), filterLibrary(mixed, mixed, emptySet(), null, emptyMap(), true))
    }

    @Test fun photoOnlyMatchesDoNotKeepGenreGroupsCollectionsOrFilteredAncestors() {
        val taggedPhoto = photo.copy(parentId = "folder", genres = listOf("Family"))
        val collection = BaseItemDto(id = "collection", type = "BoxSet")
        val catalog = listOf(folder, taggedPhoto, video)
        assertTrue(filterLibrary(listOf(folder, taggedPhoto), catalog, setOf("Family"), null, emptyMap(), true).isEmpty())
        assertTrue(filterLibrary(listOf(collection), catalog, setOf("Family"), null,
            mapOf("collection" to listOf(taggedPhoto)), false).isEmpty())
    }

    @Test fun photoOnlyServerPagesStillAdvanceToVideosWithoutPublishingPhotos() = runBlocking {
        val offsets = mutableListOf<Int>()
        val result = fetchLibraryCatalog(fetchPage = { offset, _ ->
            offsets += offset
            QueryResult(items = if (offset == 0) listOf(photo) else listOf(folder, video), totalRecordCount = 3)
        }, onPage = { items, _ -> assertTrue(items.none { it.id == "photo" }) })
        assertEquals(listOf(0, 1), offsets)
        assertEquals(listOf(folder, video), result)
    }

    @Test fun oldFreshDiskSnapshotsHidePhotosWithoutRequiringARefresh() = runBlocking {
        val directory = temporary.newFolder()
        val now = 1_000_000L
        LibraryCatalogCache(directory) { now }.save("library", emptyList())
        directory.listFiles()!!.single().writeText("""
            {"key":"library","items":[{"Id":"photo","Type":"Photo"},{"Id":"folder","Type":"Folder","IsFolder":true},{"Id":"video","Type":"Video"}],"schema":1,"refreshedAtMs":$now}
        """.trimIndent())
        val restarted = LibraryCatalogCache(directory) { now + 1_000L }
        val result = loadCachedLibraryCatalog(restarted, "library", onCached = {
            assertEquals(listOf(folder, video), it)
        }) { error("A fresh catalog must not require a server request") }
        assertEquals(listOf(folder, video), result)
        assertEquals(result, restarted.peek("library"))
    }

    @Test fun savingAndLocalUpdatesCannotRestoreCachedPhotos() = runBlocking {
        val directory = temporary.newFolder()
        val cache = LibraryCatalogCache(directory)
        cache.save("library", listOf(photo, folder, video))
        cache.updateItems("library") { it + photo }
        assertEquals(listOf(folder, video), cache.peek("library"))
        assertEquals(listOf(folder, video), LibraryCatalogCache(directory).load("library"))
    }
}
