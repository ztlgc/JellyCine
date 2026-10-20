package com.jellycine.app.ui.screens.dashboard.search

import com.jellycine.data.model.BaseItemDto
import java.util.Locale

internal fun searchResultDetails(item: BaseItemDto): List<String> = buildList {
    val rating = item.communityRating?.takeIf { it.isFinite() && it in 0f..10f }
    rating?.let { add("★ ${String.format(Locale.getDefault(), "%.1f", it)}") }

    val stream = (item.mediaStreams.orEmpty().asSequence() +
        item.mediaSources.orEmpty().asSequence().flatMap { it.mediaStreams.orEmpty().asSequence() })
        .firstOrNull { it.type.equals("Video", ignoreCase = true) && (it.width ?: 0) > 0 && (it.height ?: 0) > 0 }
    val width = stream?.width ?: item.width
    val height = stream?.height ?: item.height
    if (width != null && width > 0 && height != null && height > 0) add("$width×$height")

    val ticks = item.runTimeTicks?.takeIf { it > 0 }
        ?: item.mediaSources.orEmpty().firstNotNullOfOrNull { it.runTimeTicks?.takeIf { ticks -> ticks > 0 } }
    ticks?.let {
        val seconds = it / 10_000_000L
        add(if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60))
    }
}
