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
internal class LibraryCatalogCache(private val directory: File) {
    @Serializable
    private data class Snapshot(val key: String, val items: List<BaseItemDto>, val schema: Int = 1)

    private val memory = LinkedHashMap<String, List<BaseItemDto>>(4, .75f, true)
    private val mutex = Mutex()

    @Synchronized
    fun peek(key: String): List<BaseItemDto>? = memory[key]

    @Synchronized
    private fun remember(key: String, items: List<BaseItemDto>) {
        memory[key] = items
        while (memory.size > 3) memory.remove(memory.keys.first())
    }

    suspend fun load(key: String): List<BaseItemDto>? = peek(key) ?: withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = file(key)
            val snapshot = runCatching {
                JellyCineJson.decodeFromString<Snapshot>(file.readText())
            }.getOrNull()
            snapshot?.takeIf { it.schema == 1 && it.key == key }?.items?.also { remember(key, it) }
        }
    }

    suspend fun save(key: String, items: List<BaseItemDto>) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val deletedIds = com.jellycine.shared.playback.UserDataRefreshSignals.deletedItems.value.entries
                    .firstOrNull { (serverId, _) -> key.startsWith("${serverId.length}:$serverId") }?.value.orEmpty()
                val retainedItems = items.filterNot { it.id in deletedIds }
                remember(key, retainedItems)
                // A disk cache failure must not turn a successful network request into a UI error.
                runCatching {
                    directory.mkdirs()
                    val target = file(key)
                    val temporary = File(directory, "${target.name}.tmp")
                    temporary.writeText(JellyCineJson.encodeToString(Snapshot(key, retainedItems)))
                    try {
                        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    directory.listFiles { candidate -> candidate.extension == "json" }
                        ?.sortedByDescending { it.lastModified() }?.drop(6)?.forEach { it.delete() }
                }
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
        @Volatile private var instance: LibraryCatalogCache? = null

        fun getInstance(cacheDir: File): LibraryCatalogCache = instance ?: synchronized(this) {
            instance ?: LibraryCatalogCache(File(cacheDir, "library_catalogs")).also { instance = it }
        }
    }
}

/** Length prefixes avoid collisions; include the user because watched/favorite data is personal. */
internal fun libraryCatalogKey(server: String?, url: String?, user: String?, library: String): String =
    listOf(server.orEmpty(), url.orEmpty(), user.orEmpty(), library).joinToString("") { "${it.length}:$it" }
