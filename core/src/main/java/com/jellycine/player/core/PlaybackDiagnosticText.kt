package com.jellycine.player.core

import java.net.URI

internal object PlaybackDiagnosticText {
    /** No credentials, query, fragment, media path or local filename in diagnostic endpoints. */
    fun endpoint(value: String): String = runCatching {
        val uri = URI(value)
        if (uri.scheme != "http" && uri.scheme != "https") return "local_or_unknown"
        val extension = uri.path.orEmpty().substringAfterLast('.', "")
            .takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) }.orEmpty()
        "${uri.scheme}://${uri.host ?: "unknown"}" +
            (if (uri.port >= 0) ":${uri.port}" else "") +
            (if (extension.isNotEmpty()) "/….$extension" else "/…")
    }.getOrDefault("unknown")

    fun errorTypes(error: Throwable): String = generateSequence(error) { it.cause }
        .take(5).joinToString(" -> ") { it.javaClass.simpleName }
}
