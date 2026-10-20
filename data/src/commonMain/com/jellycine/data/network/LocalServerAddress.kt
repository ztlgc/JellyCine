package com.jellycine.data.network

import io.ktor.http.Url

/** Only local destinations use a direct LAN connection; public servers keep normal routing. */
fun isLocalServerAddress(serverUrl: String): Boolean {
    val host = runCatching { Url(serverUrl.trim()).host.lowercase().removeSurrounding("[", "]") }
        .getOrNull() ?: return false
    if (host == "localhost" || host.endsWith(".local")) return true
    if (host.contains(':')) {
        val address = host.substringBefore('%')
        return address == "::1" || address.startsWith("fc") || address.startsWith("fd") ||
            address.startsWith("fe8") || address.startsWith("fe9") ||
            address.startsWith("fea") || address.startsWith("feb")
    }
    val octets = host.split('.').map { it.toIntOrNull() ?: return false }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    return octets[0] == 10 || octets[0] == 127 ||
        (octets[0] == 172 && octets[1] in 16..31) ||
        (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 169 && octets[1] == 254)
}
