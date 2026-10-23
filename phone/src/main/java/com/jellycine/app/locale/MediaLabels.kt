package com.jellycine.app.locale

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.jellycine.shared.R
import java.util.Locale

fun Context.localizedQualityLabel(value: String): String =
    if (value.equals("Original", ignoreCase = true)) getString(R.string.player_quality_original_short) else value

@Composable
fun localizedQualityLabel(value: String): String = LocalContext.current.localizedQualityLabel(value)

/** Only app-owned playback labels belong here; media titles and server metadata stay intact. */
fun Context.localizedPlaybackLabel(value: String): String {
    val resource = when (value) {
        "Off" -> R.string.ui_off
        "Original" -> R.string.player_quality_original_short
        "Unknown", "UNKNOWN" -> R.string.ui_unknown
        "Subtitle" -> R.string.ui_subtitles
        "Mono" -> R.string.video_detail_mono
        "Stereo" -> R.string.video_detail_stereo
        "Auto" -> R.string.settings_auto
        "5.1 Surround" -> R.string.ui_5_1_surround
        "Passthrough" -> R.string.ui_passthrough
        "Direct Play", "DirectPlay" -> R.string.ui_direct_play
        "Direct Stream", "DirectStream" -> R.string.ui_direct_stream
        "Transcode" -> R.string.ui_transcode
        "Offline" -> R.string.ui_offline
        "Software", "Software Decoder" -> R.string.ui_software
        "Hardware", "Hardware Decoder" -> R.string.player_settings_decoder_priority_hardware_first
        else -> null
    }
    if (resource != null) return getString(resource)
    Regex("^(Audio|Subtitle|Video Track) ([0-9]+)$").matchEntire(value)?.let {
        val label = when (it.groupValues[1]) {
            "Audio" -> R.string.ui_audio_number
            "Subtitle" -> R.string.ui_subtitle_number
            else -> R.string.ui_video_number
        }
        return getString(label, it.groupValues[2])
    }
    Regex("^([0-9]+)ch$").matchEntire(value)?.let {
        return getString(R.string.ui_channels, it.groupValues[1].toInt())
    }
    return value
}

fun Context.localizedLanguageName(value: String): String {
    val locale = Locale.forLanguageTag(value.replace('_', '-'))
    if (value.isBlank() || value.equals("und", true)) return ""
    return locale.getDisplayLanguage(resources.configuration.locales[0]).takeIf { it.isNotBlank() } ?: value
}

fun Context.localizedRuntime(ticks: Long?, includeSeconds: Boolean = false): String {
    if (ticks == null) return ""
    if (includeSeconds) return com.jellycine.detail.CodecUtils.formatRuntime(ticks, true)
    val minutes = ticks.coerceAtLeast(0L) / 10_000_000L / 60L
    return if (minutes >= 60) getString(R.string.ui_hours_minutes, minutes / 60, minutes % 60)
        else getString(R.string.ui_minutes, minutes)
}

fun Context.userFacingError(error: Throwable?, fallback: Int): String {
    val language = resources.configuration.locales[0].language
    val message = error?.message?.trim().orEmpty()
    if (message.isNotBlank() && (language != "zh" || message.any { it in '\u4e00'..'\u9fff' })) return message
    return when (error) {
        is com.jellycine.data.network.HttpStatusException -> getString(R.string.ui_server_http_error, error.statusCode)
        is java.net.SocketTimeoutException -> getString(R.string.ui_connection_timed_out_server_may_be_unreachable)
        is java.net.UnknownHostException, is java.net.ConnectException -> getString(R.string.ui_cannot_reach_server_check_if_it_s_running)
        is javax.net.ssl.SSLException -> getString(R.string.ui_secure_connection_failed_check_server_certificate)
        is java.io.IOException -> getString(R.string.ui_network_error_check_your_connection)
        else -> getString(fallback)
    }
}
