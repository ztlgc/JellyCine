package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.network.JellyCineJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Only complete catalogs are cached; partial/failed refreshes never replace a snapshot. */
internal class LibraryCatalogCache(
    private val directory: File,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    @Serializable
    private data class Snapshot(
        val key: String, val items: List<BaseItemDto>, val schema: Int = 1,
        val refreshedAtMs: Long = 0
    )

    private val memory = LinkedHashMap<String, Snapshot>(4, .75f, true)
    private val mutex = Mutex()

    @Synchronized
    fun peek(key: String): List<BaseItemDto>? = memory[key]?.items

    @Synchronized
    fun isFresh(key: String): Boolean = memory[key]?.let {
        it.refreshedAtMs > 0 && nowMs() - it.refreshedAtMs in 0 until FULL_REFRESH_INTERVAL_MS
    } == true

    @Synchronized
    private fun remember(snapshot: Snapshot) {
        memory[snapshot.key] = snapshot
        while (memory.size > 3) memory.remove(memory.keys.first())
    }

    suspend fun load(key: String): List<BaseItemDto>? = peek(key) ?: withContext(Dispatchers.IO) {
        mutex.withLock {
            readSnapshot(key)?.also { remember(it) }?.items
        }
    }

    suspend fun save(key: String, items: List<BaseItemDto>) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val deletedIds = com.jellycine.shared.playback.UserDataRefreshSignals.deletedItems.value.entries
                    .firstOrNull { (serverId, _) -> key.startsWith("${serverId.length}:$serverId") }?.value.orEmpty()
                val retainedItems = withoutLibraryPhotos(items).filterNot { it.id in deletedIds }
                val snapshot = Snapshot(key, retainedItems, refreshedAtMs = nowMs())
                remember(snapshot)
                writeSnapshot(snapshot)
                runCatching {
                    directory.listFiles { candidate -> candidate.extension == "json" }
                        ?.sortedByDescending { it.lastModified() }?.drop(6)?.forEach { it.delete() }
                }
            }
        }
    }

    /** Local user-data changes must not extend the lifetime of the complete catalog. */
    suspend fun updateItems(key: String, transform: (List<BaseItemDto>) -> List<BaseItemDto>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val snapshot = synchronized(this@LibraryCatalogCache) { memory[key] } ?: readSnapshot(key)
                    ?: return@withLock
                updateSnapshot(snapshot, transform)
            }
        }

    suspend fun removeItem(serverId: String, itemId: String) =
        updateSessionItems(serverId) { items -> items.filterNot { it.id == itemId } }

    suspend fun updateRating(serverId: String, itemId: String, rating: Float?) =
        updateSessionItems(serverId) { items ->
            items.map { if (it.id == itemId) it.copy(communityRating = rating) else it }
        }

    private suspend fun updateSessionItems(serverId: String, transform: (List<BaseItemDto>) -> List<BaseItemDto>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefix = "${serverId.length}:$serverId"
            val snapshots = linkedMapOf<String, Snapshot>()
            directory.listFiles { it.extension == "json" }?.forEach { file ->
                runCatching { JellyCineJson.decodeFromString<Snapshot>(file.readText()) }.getOrNull()
                    ?.takeIf { it.schema == 1 && it.key.startsWith(prefix) }?.let { snapshots[it.key] = it }
            }
            synchronized(this@LibraryCatalogCache) {
                memory.values.filter { it.key.startsWith(prefix) }.forEach { snapshots[it.key] = it }
            }
            snapshots.values.forEach { snapshot -> updateSnapshot(snapshot, transform) }
        }
    }

    private fun updateSnapshot(snapshot: Snapshot, transform: (List<BaseItemDto>) -> List<BaseItemDto>) {
        val items = withoutLibraryPhotos(transform(snapshot.items))
        if (items == snapshot.items) return
        val updated = snapshot.copy(items = items)
        remember(updated)
        writeSnapshot(updated)
    }

    private fun readSnapshot(key: String): Snapshot? = runCatching {
        JellyCineJson.decodeFromString<Snapshot>(file(key).readText())
    }.getOrNull()?.takeIf { it.schema == 1 && it.key == key }
        ?.let { it.copy(items = withoutLibraryPhotos(it.items)) }

    // A disk cache failure must not turn a successful request or local update into a UI error.
    private fun writeSnapshot(snapshot: Snapshot) {
        runCatching {
            directory.mkdirs()
            val target = file(snapshot.key)
            val temporary = File(directory, "${target.name}.tmp")
            temporary.writeText(JellyCineJson.encodeToString(snapshot))
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    suspend fun invalidateSession(serverId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefix = "${serverId.length}:$serverId"
            synchronized(this@LibraryCatalogCache) {
                memory.keys.filter { it.startsWith(prefix) }.forEach { memory.remove(it) }
            }
            directory.listFiles { candidate -> candidate.extension == "json" }?.forEach { file ->
                val snapshot = runCatching { JellyCineJson.decodeFromString<Snapshot>(file.readText()) }.getOrNull()
                if (snapshot?.key?.startsWith(prefix) == true) file.delete()
            }
        }
    }

    private fun file(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.json")
    }

    companion object {
        const val FULL_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        @Volatile private var instance: LibraryCatalogCache? = null

        fun getInstance(cacheDir: File): LibraryCatalogCache = instance ?: synchronized(this) {
            instance ?: LibraryCatalogCache(File(cacheDir, "library_catalogs")).also { instance = it }
        }
    }
}

/** Length prefixes avoid collisions; include the user because watched/favorite data is personal. */
internal fun libraryCatalogKey(server: String?, url: String?, user: String?, library: String): String =
    listOf(server.orEmpty(), url.orEmpty(), user.orEmpty(), library).joinToString("") { "${it.length}:$it" }
