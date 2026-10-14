package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.QueryResult

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
        onPage(result.values.toList(), page.totalRecordCount ?: result.size)
        if (batch.isEmpty() || (page.totalRecordCount != null && offset >= page.totalRecordCount!!)) break
    }
    return result.values.toList()
}
