package com.jellycine.logging

import java.io.File

/** Accessed only by PlaybackLogStore's single IO worker. */
internal class RotatingPlaybackLog(
    private val directory: File,
    private val maxFileBytes: Int = 512 * 1024,
    private val fileCount: Int = 3
) {
    init {
        require(maxFileBytes >= 128 && fileCount >= 1)
    }

    fun append(line: String) {
        directory.mkdirs()
        // Keep a single event bounded even if a codec produces an unusually large error.
        val bytes = (line.replace('\n', ' ').replace('\r', ' ').take(maxFileBytes / 4 - 1) + "\n")
            .toByteArray(Charsets.UTF_8)
        val current = file(0)
        if (current.length() + bytes.size > maxFileBytes) {
            file(fileCount - 1).delete()
            for (index in fileCount - 2 downTo 0) {
                check(!file(index).exists() || file(index).renameTo(file(index + 1)))
            }
        }
        current.appendBytes(bytes)
    }

    fun read(): String = buildString {
        for (index in fileCount - 1 downTo 0) {
            val file = file(index)
            if (file.isFile) append(file.readText())
        }
    }

    private fun file(index: Int) = File(directory, "exoplayer.$index.txt")
}
