package com.jellycine.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.ktor.http.Url
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.Proxy

internal fun OkHttpClient.Builder.directLocalServer(serverUrl: String): OkHttpClient.Builder = apply {
    if (isLocalServerAddress(serverUrl)) proxy(Proxy.NO_PROXY)
}

/** Choose a connected LAN interface with a route to this server, without binding the process. */
internal fun localServerNetwork(context: Context?, serverUrl: String): Network? {
    if (context == null || !isLocalServerAddress(serverUrl)) return null
    val host = runCatching { Url(serverUrl.trim()).host.removeSurrounding("[", "]") }.getOrNull()
        ?: return null
    if (host == "localhost" || host == "::1" || host.startsWith("127.")) return null
    val literalAddress = if (host.contains(':') || host.all { it.isDigit() || it == '.' }) {
        runCatching { InetAddress.getByName(host) }.getOrNull()
    } else null
    val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val active = manager.activeNetwork
    return runCatching {
        manager.allNetworks.mapNotNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return@mapNotNull null
            val properties = manager.getLinkProperties(network) ?: return@mapNotNull null
            val matchingPrefix = if (literalAddress != null) {
                properties.routes.filter { it.destination.prefixLength > 0 && it.matches(literalAddress) }
                    .maxOfOrNull { it.destination.prefixLength } ?: return@mapNotNull null
            } else 0
            Triple(network, matchingPrefix, network == active)
        }.sortedWith(compareByDescending<Triple<Network, Int, Boolean>> { it.second }.thenByDescending { it.third })
            .firstOrNull()?.first
    }.getOrNull()
}
