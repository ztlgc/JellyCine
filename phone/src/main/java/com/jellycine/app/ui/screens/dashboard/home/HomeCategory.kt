package com.jellycine.app.ui.screens.dashboard.home

import androidx.annotation.StringRes
import com.jellycine.shared.R
import com.jellycine.data.model.BaseItemDto

internal object HomeCategory {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val TV_SHOWS = "tv_shows"
    const val OTHER_VIDEOS = "other_videos"

    val all = listOf(HOME, MOVIES, TV_SHOWS, OTHER_VIDEOS)

    fun librariesForCategory(category: String, libraries: List<BaseItemDto>): List<BaseItemDto> =
        if (category == OTHER_VIDEOS) libraries.filter { it.collectionType.equals("homevideos", ignoreCase = true) }
        else libraries

    @StringRes
    fun titleRes(category: String): Int {
        return when (category) {
            MOVIES -> R.string.movies
            TV_SHOWS -> R.string.tv_shows
            OTHER_VIDEOS -> R.string.home_other_videos
            else -> R.string.home
        }
    }
}
