package com.jellycine.data.api

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cache
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryRefreshRequestTest {
    @Test fun manualRefreshBypassesFreshHttpCacheAndRefreshesEveryRequestedPage() = runBlocking {
        val currentName = AtomicReference("old")
        val requests = CopyOnWriteArrayList<Pair<String?, Map<String, String>>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val directory = Files.createTempDirectory("library-http-cache").toFile()
        server.createContext("/Users/user/Items") { exchange ->
            val query = exchange.requestURI.rawQuery.split('&').associate { pair ->
                val parts = pair.split('=', limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            }
            requests += exchange.requestHeaders.getFirst("Cache-Control") to query
            val body = """{"Items":[{"Id":"video","Name":"${currentName.get()}"}],"TotalRecordCount":401}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Cache-Control", "public, max-age=3600")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val diskCache = Cache(directory, 1024L * 1024)
        val client = HttpClient(OkHttp) {
            engine { config { proxy(Proxy.NO_PROXY); cache(diskCache) } }
            defaultRequest { url("http://127.0.0.1:${server.address.port}/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val api: MediaServerApi = MediaServerApiClient(client, "http://127.0.0.1:${server.address.port}/")
            suspend fun page(forceRefresh: Boolean = false, offset: Int = 0) = api.getUserItems(
                userId = "user", parentId = "library", recursive = true,
                startIndex = offset, limit = 200, enableUserData = true, forceRefresh = forceRefresh
            ).body()!!.items!!.single().name

            assertEquals("old", page())
            currentName.set("updated")
            assertEquals("old", page()) // Reproduce the otherwise fresh, stale HTTP response.
            assertEquals(1, requests.size)
            assertEquals("updated", page(forceRefresh = true))
            assertEquals(2, requests.size)
            assertEquals("no-cache", requests.last().first)
            assertEquals("updated", page()) // The refreshed result also replaces the HTTP snapshot.
            assertEquals(2, requests.size)

            page(forceRefresh = true, offset = 200)
            page(forceRefresh = true, offset = 400)
            assertEquals(listOf("0", "0", "200", "400"), requests.map { it.second["startIndex"] })
            requests.drop(1).forEach { (cacheControl, query) ->
                assertEquals("no-cache", cacheControl)
                assertEquals("library", query["parentId"])
                assertEquals("200", query["limit"])
                assertEquals("true", query["EnableUserData"])
            }

            val filtered = api.getUserItems(
                userId = "user", parentId = "library", recursive = true,
                startIndex = 0, limit = 200, enableUserData = true,
                excludeItemTypes = "Photo", forceRefresh = true
            )
            assertTrue(filtered.isSuccessful)
            assertEquals("Photo", requests.last().second["ExcludeItemTypes"])
        } finally {
            client.close()
            diskCache.close()
            server.stop(0)
            directory.deleteRecursively()
        }
    }
}
