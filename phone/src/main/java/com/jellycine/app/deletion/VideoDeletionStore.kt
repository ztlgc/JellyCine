package com.jellycine.app.deletion

import com.jellycine.data.network.JellyCineJson
import com.jellycine.data.repository.VideoDeletionOutcome
import com.jellycine.data.repository.VideoDeletionTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
internal data class VideoDeletionRecord(
    val target: VideoDeletionTarget,
    val attempts: Int = 0,
    val completed: Boolean = false
) {
    val key: String get() = deletionKey(target.serverId, target.itemId)
    val exhausted: Boolean get() = !completed && attempts >= MAX_ATTEMPTS
    val pending: Boolean get() = !completed && !exhausted

    companion object {
        const val MAX_RETRIES = 10
        const val MAX_ATTEMPTS = 1 + MAX_RETRIES
    }
}

internal fun deletionKey(serverId: String, itemId: String): String = "${serverId.length}:$serverId$itemId"

/** Completed and exhausted records remain tombstones, including across refreshes and restarts. */
internal class VideoDeletionStore(private val file: File) {
    private var records = if (file.exists()) {
        JellyCineJson.decodeFromString<List<VideoDeletionRecord>>(file.readText())
    } else emptyList()

    @Synchronized
    fun all(): List<VideoDeletionRecord> = records.toList()

    @Synchronized
    fun get(key: String): VideoDeletionRecord? = records.firstOrNull { it.key == key }

    @Synchronized
    fun enqueue(target: VideoDeletionTarget): VideoDeletionRecord {
        get(deletionKey(target.serverId, target.itemId))?.let { return it }
        val record = VideoDeletionRecord(target)
        persist(records + record)
        return record
    }

    /** Reserve durably before sending, so a process death cannot reset or exceed the request limit. */
    @Synchronized
    fun reserveAttempt(key: String): VideoDeletionRecord? {
        val record = get(key)?.takeIf { it.pending } ?: return null
        val reserved = record.copy(attempts = record.attempts + 1)
        persist(records.map { if (it.key == key) reserved else it })
        return reserved
    }

    @Synchronized
    fun finishAttempt(key: String, outcome: VideoDeletionOutcome): VideoDeletionRecord {
        val record = checkNotNull(get(key))
        val updated = when (outcome) {
            VideoDeletionOutcome.COMPLETE -> record.copy(completed = true)
            VideoDeletionOutcome.RETRY -> record
            // No request was sent: missing local credentials must not consume a retransmission.
            VideoDeletionOutcome.SESSION_UNAVAILABLE -> record.copy(attempts = (record.attempts - 1).coerceAtLeast(0))
        }
        if (updated != record) persist(records.map { if (it.key == key) updated else it })
        return updated
    }

    private fun persist(updated: List<VideoDeletionRecord>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(JellyCineJson.encodeToString(updated).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        records = updated
    }
}
