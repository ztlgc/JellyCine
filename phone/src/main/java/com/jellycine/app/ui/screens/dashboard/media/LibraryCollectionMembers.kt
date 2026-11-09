package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.data.model.BaseItemDto

/**
 * Process-wide collection membership shared by every library screen of the same session.
 * Without it each media library repeats the same recursive scan for collections belonging
 * to other libraries. Entries expire with the catalog policy and are dropped on manual refresh.
 */
internal class LibraryCollectionMembers(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxItems: Int = MAX_ITEMS
) {
    private data class Entry(val items: List<BaseItemDto>, val storedAtMs: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) =
            size > maxEntries
    }

    @Synchronized
    fun get(key: String): List<BaseItemDto>? = entries[key]
        ?.takeIf { nowMs() - it.storedAtMs in 0 until LibraryCatalogCache.FULL_REFRESH_INTERVAL_MS }
        ?.items

    @Synchronized
    fun put(key: String, items: List<BaseItemDto>) {
        entries[key] = Entry(items, nowMs())
        // Membership lists can be nearly as large as a library catalog; bound both the number of
        // collections and the total retained items so switching libraries cannot grow memory forever.
        while (entries.values.sumOf { it.items.size } > maxItems) {
            val eldest = entries.keys.firstOrNull() ?: break
            entries.remove(eldest)
        }
    }

    /** Keep cached memberships current without waiting for another server scan. */
    @Synchronized
    fun updateSession(serverId: String, transform: (List<BaseItemDto>) -> List<BaseItemDto>) {
        val prefix = "${serverId.length}:$serverId"
        entries.entries.filter { it.key.startsWith(prefix) }.forEach { entry ->
            entry.setValue(entry.value.copy(items = transform(entry.value.items)))
        }
    }

    @Synchronized
    fun invalidateSession(serverId: String) {
        val prefix = "${serverId.length}:$serverId"
        entries.keys.filter { it.startsWith(prefix) }.forEach { entries.remove(it) }
    }

    companion object {
        private const val MAX_ENTRIES = 48
        private const val MAX_ITEMS = 8_000
        @Volatile private var instance: LibraryCollectionMembers? = null

        fun getInstance(): LibraryCollectionMembers = instance ?: synchronized(this) {
            instance ?: LibraryCollectionMembers().also { instance = it }
        }
    }
}
