package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.app.R
import com.jellycine.data.model.BaseItemDto
import java.util.Locale
import java.time.OffsetDateTime
import kotlin.random.Random

enum class LibraryTab(val label: Int) {
    ALL(R.string.library_all), RESUME(R.string.library_resume), COLLECTIONS(R.string.library_collections),
    GENRES(R.string.library_genres), TAGS(R.string.library_tags), FAVORITES(R.string.library_favorites), FOLDERS(R.string.library_folders)
}

enum class LibrarySort(val label: Int, val descendingDefault: Boolean = true) {
    CREATED(R.string.library_sort_created), COMMUNITY(R.string.library_sort_community),
    TITLE(R.string.library_sort_title, false), PREMIERE(R.string.library_sort_premiere),
    RATING(R.string.library_sort_rating, false), YEAR(R.string.library_sort_year),
    CRITIC(R.string.library_sort_critic), PLAYED(R.string.library_sort_played),
    BITRATE(R.string.library_sort_bitrate), RESOLUTION(R.string.library_sort_resolution),
    FILENAME(R.string.library_sort_filename, false), DURATION(R.string.library_sort_duration), RANDOM(R.string.library_sort_random)
}

internal fun BaseItemDto.isLibraryPlayable(): Boolean =
    type in setOf("Movie", "Episode", "Video", "MusicVideo", "Audio") && isFolder != true

internal fun libraryPlayableQueue(display: List<BaseItemDto>, catalog: List<BaseItemDto>): List<BaseItemDto> {
    val series = display.filter { it.type == "Series" }.mapNotNull { it.id }.toSet()
    return (display.filter { it.isLibraryPlayable() } + catalog.filter { it.type == "Episode" && it.seriesId in series })
        .distinctBy { it.id }
}

internal fun libraryMedia(items: List<BaseItemDto>, contentType: ContentType): List<BaseItemDto> = items.filter {
    when (contentType) {
        ContentType.MOVIES -> it.type == "Movie"
        ContentType.SERIES -> it.type == "Series"
        ContentType.EPISODES -> it.type == "Episode"
        ContentType.HOME_MEDIA -> it.type in setOf("Video", "Photo", "Movie", "Audio", "MusicVideo")
        else -> it.type in setOf("Movie", "Series", "Video", "Photo", "Audio", "MusicVideo")
    }
}

internal fun filterLibrary(
    items: List<BaseItemDto>, catalog: List<BaseItemDto>, genres: Set<String>, watched: Boolean?,
    collections: Map<String, List<BaseItemDto>>, directoryMode: Boolean
): List<BaseItemDto> {
    if (genres.isEmpty() && watched == null) return items
    fun matches(item: BaseItemDto) = genres.all { it in item.genres.orEmpty() } &&
        (watched == null || (item.userData?.played == true) == watched)
    // Keep directories leading to matching descendants accessible, even without their own genre/user data.
    val matchingAncestors = mutableSetOf<String>()
    if (directoryMode) {
        val byId = catalog.associateBy { it.id }
        catalog.filter(::matches).forEach { item ->
            var parent = item.parentId
            val visited = mutableSetOf<String>()
            while (parent != null && visited.add(parent)) {
                matchingAncestors.add(parent)
                parent = byId[parent]?.parentId
            }
        }
    }
    return items.filter { item ->
        when {
            item.type == "BoxSet" -> collections[item.id].orEmpty().any(::matches)
            directoryMode && (item.isFolder == true || item.type in setOf("Folder", "PhotoAlbum", "Season", "Series", "MusicAlbum")) ->
                item.id in matchingAncestors || (item.type in setOf("Series", "MusicAlbum") && matches(item))
            else -> matches(item)
        }
    }
}

/** Sort the complete result, with absent metadata last in either direction and deterministic ties. */
internal fun sortLibrary(items: List<BaseItemDto>, sort: LibrarySort, descending: Boolean, seed: Int): List<BaseItemDto> {
    val stable = compareBy<BaseItemDto> { it.sortName ?: it.name.orEmpty() }.thenBy { it.id.orEmpty() }
    if (sort == LibrarySort.RANDOM) return items.sortedWith(stable).shuffled(Random(seed))
    fun value(item: BaseItemDto): Comparable<*>? {
        val video = (item.mediaStreams.orEmpty() + item.mediaSources.orEmpty().flatMap { it.mediaStreams.orEmpty() })
            .filter { it.type.equals("Video", true) }
        return when (sort) {
            LibrarySort.CREATED -> item.dateCreated?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            LibrarySort.COMMUNITY -> item.communityRating
            LibrarySort.TITLE -> (item.sortName ?: item.name)?.lowercase(Locale.ROOT)
            LibrarySort.PREMIERE -> item.premiereDate?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            LibrarySort.RATING -> item.officialRating?.lowercase(Locale.ROOT)
            LibrarySort.YEAR -> item.productionYear
            LibrarySort.CRITIC -> item.criticRating
            LibrarySort.PLAYED -> item.userData?.lastPlayedDate?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            LibrarySort.BITRATE -> video.mapNotNull { it.bitRate }.maxOrNull() ?: item.mediaSources.orEmpty().mapNotNull { it.bitrate }.maxOrNull()
            LibrarySort.RESOLUTION -> video.mapNotNull { stream ->
                stream.width?.let { w -> stream.height?.let { h -> w.toLong() * h } }
            }.maxOrNull() ?: item.width?.let { w -> item.height?.let { h -> w.toLong() * h } }
            LibrarySort.FILENAME -> (item.path ?: item.mediaSources?.firstOrNull()?.path)
                ?.replace('\\', '/')?.substringAfterLast('/')?.lowercase(Locale.ROOT)
            LibrarySort.DURATION -> item.runTimeTicks
            LibrarySort.RANDOM -> null
        }
    }
    return items.map { it to value(it) }.sortedWith { a, b ->
        val av = a.second; val bv = b.second
        @Suppress("UNCHECKED_CAST")
        val result = when {
            av == null && bv == null -> 0
            av == null -> 1
            bv == null -> -1
            else -> (av as Comparable<Any>).compareTo(bv).let { if (descending) -it else it }
        }
        if (result == 0) stable.compare(a.first, b.first) else result
    }.map { it.first }
}
