package com.jellycine.data.network

import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalServerRoutingTest {
    @Test fun localPublicInfoRequestBypassesAnUnreachableHttpProxy() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/System/Info/Public") { exchange ->
            val body = "{\"Version\":\"4.10.0.22\"}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val baseUrl = "http://127.0.0.1:${server.address.port}/"
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 1)))
            .directLocalServer(baseUrl)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
        try {
            client.newCall(Request.Builder().url(baseUrl + "System/Info/Public").build()).execute().use {
                assertEquals(200, it.code)
                assertEquals("{\"Version\":\"4.10.0.22\"}", it.body.string())
            }
        } finally {
            server.stop(0)
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun remoteServersKeepTheConfiguredProxy() {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 8888))
        val client = OkHttpClient.Builder().proxy(proxy).directLocalServer("https://media.example.org/").build()
        assertEquals(proxy, client.proxy)
    }
}
