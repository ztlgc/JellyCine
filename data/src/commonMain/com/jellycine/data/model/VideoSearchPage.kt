package com.jellycine.data.model

data class SearchSourceCursor(
    val startIndex: Int = 0,
    val nameStartsWith: Boolean = false,
    val seenItemIds: Set<String> = emptySet()
)

/** A null source cursor means that source has finished. */
data class VideoSearchCursor(
    val videos: SearchSourceCursor? = SearchSourceCursor(),
    val series: SearchSourceCursor? = SearchSourceCursor()
) {
    val hasMore: Boolean get() = videos != null || series != null
}

data class VideoSearchPage(
    val items: List<BaseItemDto>,
    val nextCursor: VideoSearchCursor
)
