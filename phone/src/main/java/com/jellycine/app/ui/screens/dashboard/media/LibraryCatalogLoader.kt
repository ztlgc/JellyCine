package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.QueryResult

/** A fresh complete snapshot (including an empty library) needs no network request. */
internal suspend fun loadCachedLibraryCatalog(
    cache: LibraryCatalogCache,
    key: String,
    forceRefresh: Boolean = false,
    onCached: (List<BaseItemDto>) -> Unit = {},
    fetch: suspend () -> List<BaseItemDto>
): List<BaseItemDto> {
    val cached = cache.load(key)
    if (cached != null) {
        onCached(cached)
        if (!forceRefresh && cache.isFresh(key)) return cached
    }
    return fetch().also { cache.save(key, it) }
}

internal suspend fun fetchLibraryCatalog(
    fetchPage: suspend (offset: Int, limit: Int) -> QueryResult<BaseItemDto>,
    onPage: suspend (items: List<BaseItemDto>, total: Int) -> Unit = { _, _ -> }
): List<BaseItemDto> {
    val result = linkedMapOf<String, BaseItemDto>()
    var offset = 0
    while (true) {
        val page = fetchPage(offset, 200)
        val batch = page.items.orEmpty()
        val previousSize = result.size
        batch.forEach { item -> item.id?.let { result[it] = item } }
        offset += batch.size
        check(batch.isEmpty() || result.size > previousSize) { "Server returned duplicate pages" }
        onPage(withoutLibraryPhotos(result.values.toList()), page.totalRecordCount ?: result.size)
        if (batch.isEmpty() || (page.totalRecordCount != null && offset >= page.totalRecordCount!!)) break
    }
    return withoutLibraryPhotos(result.values.toList())
}
