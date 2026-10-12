package com.jellycine.app.ui.screens.dashboard.home

import com.jellycine.data.model.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCategoryTest {
    @Test fun otherVideosIncludesOnlyHomeMediaLibraries() {
        val libraries = listOf(
            BaseItemDto(id = "movie", collectionType = "movies"),
            BaseItemDto(id = "home", collectionType = "homevideos"),
            BaseItemDto(id = "tv", collectionType = "tvshows"),
            BaseItemDto(id = "mixed", collectionType = null),
            BaseItemDto(id = "home2", collectionType = "HomeVideos")
        )
        assertEquals(listOf("home", "home2"), HomeCategory.librariesForCategory(HomeCategory.OTHER_VIDEOS, libraries).map { it.id })
    }

    @Test fun missingHomeMediaLibrariesProducesAnEmptyCategory() {
        assertTrue(HomeCategory.librariesForCategory(HomeCategory.OTHER_VIDEOS,
            listOf(BaseItemDto(collectionType = "movies"))).isEmpty())
        assertTrue(HomeCategory.librariesForCategory(HomeCategory.OTHER_VIDEOS, emptyList()).isEmpty())
    }

    @Test fun switchingBackToHomeKeepsAllLibraries() {
        val libraries = listOf(BaseItemDto(id = "home", collectionType = "homevideos"), BaseItemDto(id = "movie", collectionType = "movies"))
        assertEquals(libraries, HomeCategory.librariesForCategory(HomeCategory.HOME, libraries))
    }
}
