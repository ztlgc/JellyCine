package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.media3.common.MediaLibraryInfo
import com.jellycine.app.BuildConfig
import com.jellycine.logging.AppLogger
import com.jellycine.logging.CrashLogStore
import com.jellycine.logging.LogEntry
import com.jellycine.logging.LogcatReader
import com.jellycine.logging.PlaybackLogStore
import com.jellycine.shared.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects logcat, the in-memory/mpv buffer, persistent playback diagnostics and crash reports into a single text file and
 * hands it to the system share sheet. Backs the "Logs" item in Settings.
 */
object LogsExporter {

    private const val MAX_CACHE_FILES = 5

    suspend fun shareAll(context: Context) {
        val appContext = context.applicationContext
        val uri = withContext(Dispatchers.IO) {
            val text = buildText(appContext)
            val dir = File(appContext.cacheDir, "logs").apply { mkdirs() }
            pruneOldFiles(dir)
            val stamp = fileStampFormat.format(Date())
            val file = File(dir, "jellycine_log_$stamp.txt")
            file.writeText(text)
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.logs_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    internal suspend fun buildText(context: Context): String {
        val logcat = LogcatReader.read()
        val inMemory = AppLogger.entries.value
        val merged = (logcat + inMemory).sortedBy { it.timeMillis }
        val crashes = runCatching { CrashLogStore.readCrashFiles(context) }.getOrDefault(emptyList())
        val playback = PlaybackLogStore.read(context)

        return buildString {
            append("=== JellyCine Logs — ").append(timeFormat.format(Date())).append(" ===\n")
            append("App: ").append(context.packageName).append('\n')
            append("Version: ").append(BuildConfig.VERSION_NAME).append(" (")
                .append(BuildConfig.VERSION_CODE).append(") ").append(BuildConfig.BUILD_TYPE).append('\n')
            append("Media3: ").append(MediaLibraryInfo.VERSION).append('\n')
            append("Diagnostics: buffered-stall-v2\n")
            append("Device: ")
                .append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL)
                .append(" (Android ").append(android.os.Build.VERSION.RELEASE)
                .append(", API ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n")

            append("----- EXOPLAYER PLAYBACK DIAGNOSTICS (persistent, latest up to 1.5 MiB) -----\n")
            append("Times are UTC; session identifies each player instance. null/unknown/-1 means unavailable.\n")
            append("Buffering wall time may include startup, seeks or pauses; use rebufferCount/rebufferMs for stalls.\n")
            append("Bandwidth is an estimate; completed load bytes may include disk cache. Thermal status: 0=none, 1=light, 2=moderate, 3=severe, 4+=critical.\n")
            append(playback.ifBlank { "(no ExoPlayer diagnostics captured yet)\n" })
            append('\n')

            append("----- LOGCAT (").append(merged.size).append(" lines) -----\n")
            if (merged.isEmpty()) {
                append("(no log entries captured)\n")
            } else {
                merged.forEach { append(formatLine(it)).append('\n') }
            }

            append("\n----- CRASHES (").append(crashes.size).append(") -----\n")
            if (crashes.isEmpty()) {
                append("(no crash reports)\n")
            } else {
                crashes.forEach { crash ->
                    append("----- ").append(crash.name).append(" -----\n")
                    append(crash.content).append("\n\n")
                }
            }
        }
    }

    private fun formatLine(entry: LogEntry): String =
        "${timeFormat.format(Date(entry.timeMillis))} ${entry.level.label}/${entry.tag}: ${entry.message}"

    private fun pruneOldFiles(dir: File) {
        val files = dir.listFiles { f -> f.name.startsWith("jellycine_log_") } ?: return
        files.sortedByDescending { it.lastModified() }
            .drop(MAX_CACHE_FILES - 1)
            .forEach { runCatching { it.delete() } }
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
}
