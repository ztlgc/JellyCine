package com.jellycine.data.model

/** Keep series navigation alongside playable videos from any library. */
fun BaseItemDto.isVideoSearchResult(): Boolean = when {
    type.equals("Series", ignoreCase = true) -> true
    isFolder == true -> false
    mediaType.equals("Video", ignoreCase = true) -> true
    else -> type?.lowercase() in setOf("movie", "episode", "video", "musicvideo", "trailer", "adultvideo")
}
