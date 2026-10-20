package com.jellycine.data.api

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SearchPagingRequestTest {
    @Test fun bothSearchModesSendTheirOffsetAndKeepStableServerOrdering() = runBlocking {
        val requests = CopyOnWriteArrayList<Map<String, String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/Users/user/Items") { exchange ->
            requests += exchange.requestURI.rawQuery.split('&').associate { pair ->
                val parts = pair.split('=', limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            }
            val body = """{"Items":[],"TotalRecordCount":121}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val client = HttpClient(OkHttp) {
            engine { config { proxy(Proxy.NO_PROXY) } }
            defaultRequest { url("http://127.0.0.1:${server.address.port}/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val api = MediaServerApiClient(client, "http://127.0.0.1:${server.address.port}/")
            assertEquals(121, api.searchItems(
                userId = "user", searchTerm = "旅行", includeItemTypes = null,
                mediaTypes = "Video", startIndex = 60, limit = 60
            ).body()?.totalRecordCount)
            api.searchItemsByName(userId = "user", nameStartsWith = "旅行", includeItemTypes = "Series",
                startIndex = 120, limit = 60)
            assertEquals(listOf("60", "120"), requests.map { it["startIndex"] })
            requests.forEach {
                assertEquals("60", it["limit"])
                assertEquals("true", it["recursive"])
                assertEquals("SortName", it["sortBy"])
                assertEquals("Ascending", it["sortOrder"])
            }
            assertEquals("Video", requests[0]["mediaTypes"])
            assertEquals("旅行", requests[0]["searchTerm"])
            assertEquals("旅行", requests[1]["nameStartsWith"])
            assertEquals("Series", requests[1]["includeItemTypes"])
            assertFalse(requests[1].containsKey("searchTerm"))
        } finally {
            client.close()
            server.stop(0)
        }
    }
}
