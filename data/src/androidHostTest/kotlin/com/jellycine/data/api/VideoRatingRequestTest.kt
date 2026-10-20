package com.jellycine.data.api

import com.jellycine.data.network.HttpStatusException
import com.jellycine.data.repository.VideoRatingNotConfirmedException
import com.jellycine.data.repository.VideoRatingPermissionException
import com.jellycine.data.repository.VideoRatingUpdater
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class VideoRatingRequestTest {
    private val original = """{"Id":"video","Type":"Video","Name":"家庭旅行","CanEditItems":true,
        "CommunityRating":7.4,"Overview":"保留简介","ProviderIds":{"Custom":"42"},
        "UnknownFutureField":{"Nested":[null,false,1.25]},"People":[{"Name":"家人"}],
        "LockedFields":["Name"],"LockData":false}"""
    private data class Request(val method: String, val path: String, val query: String?, val body: String, val contentType: String?)

    @Test fun savesFullFreshDocumentAndReadsBackBeforeReportingSuccess() = runBlocking {
        withServer(original) { api, requests ->
            val result = VideoRatingUpdater(api, "user").save("video", 8.6f)
            assertEquals(8.6f, result.communityRating)
            assertEquals(listOf("GET", "POST", "GET"), requests.map { it.method })
            assertEquals(listOf("/Users/user/Items/video", "/Items/video", "/Users/user/Items/video"), requests.map { it.path })
            assertTrue(requests.filter { it.method == "GET" }.all { it.query?.startsWith("_=") == true && !it.query.contains("fields") })
            val post = requests[1]
            assertTrue(post.contentType.orEmpty().startsWith("application/json"))
            assertEquals(8.6f, Json.parseToJsonElement(post.body).jsonObject["CommunityRating"]!!.jsonPrimitive.float)
            val before = Json.parseToJsonElement(original).jsonObject
            val after = Json.parseToJsonElement(post.body).jsonObject
            assertEquals(before - setOf("CommunityRating", "LockedFields"), after - setOf("CommunityRating", "LockedFields"))
            assertEquals(JsonArray(listOf(JsonPrimitive("Name"), JsonPrimitive("CommunityRating"))), after["LockedFields"])
        }
    }

    @Test fun readOnlyAccountCannotPostAndInvalidScoresCannotRequest() = runBlocking {
        withServer(original.replace("\"CanEditItems\":true", "\"CanEditItems\":false")) { api, requests ->
            val updater = VideoRatingUpdater(api, "user")
            listOf(0.9f, 10.1f, 8.65f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
                assertFailsWith<IllegalArgumentException> { updater.save("video", invalid) }
            }
            assertTrue(requests.isEmpty())
            assertFailsWith<VideoRatingPermissionException> { updater.save("video", 8f) }
            assertEquals(listOf("GET"), requests.map { it.method })
        }
    }

    @Test fun serverPermissionFailureAndHttpFailureAreNotSuccess() = runBlocking {
        listOf(403, 500).forEach { status ->
            withServer(original, postStatus = status) { api, requests ->
                val updater = VideoRatingUpdater(api, "user")
                if (status == 403) assertFailsWith<VideoRatingPermissionException> { updater.save("video", 8f) }
                else assertEquals(500, assertFailsWith<HttpStatusException> { updater.save("video", 8f) }.statusCode)
                assertEquals(listOf("GET", "POST"), requests.map { it.method })
            }
        }
    }

    @Test fun acceptedPostWithoutMatchingReadbackIsNotReportedAsSaved() = runBlocking {
        withServer(original, readback = original) { api, requests ->
            assertFailsWith<VideoRatingNotConfirmedException> { VideoRatingUpdater(api, "user").save("video", 8f) }
            assertEquals(3, requests.size)
        }
    }

    @Test fun wrongItemOrFolderCannotBeModified() = runBlocking {
        listOf(original.replace("\"Id\":\"video\"", "\"Id\":\"other\""),
            original.replace("\"Type\":\"Video\"", "\"Type\":\"Folder\"" )).forEach { document ->
            withServer(document) { api, requests ->
                assertFailsWith<IllegalStateException> { VideoRatingUpdater(api, "user").save("video", 8f) }
                assertEquals(listOf("GET"), requests.map { it.method })
            }
        }
    }

    @Test fun existingRatingLockIsNotDuplicatedAndReadUsesServerScore() = runBlocking {
        withServer(original.replace("[\"Name\"]", "[\"Name\",\"CommunityRating\"]")) { api, requests ->
            val updater = VideoRatingUpdater(api, "user")
            assertEquals(7.4f, updater.load("video").communityRating)
            updater.save("video", 10f)
            assertEquals(2, Json.parseToJsonElement(requests.first { it.method == "POST" }.body).jsonObject["LockedFields"]!!.jsonArray.size)
        }
    }


    @Test fun fractionalRangeBoundariesSurviveWriteAndReadback() = runBlocking {
        withServer(original) { api, requests ->
            val updater = VideoRatingUpdater(api, "user")
            listOf(1f, 5.9f, 6f, 10f).forEach { score ->
                assertEquals(score, updater.save("video", score).communityRating)
                val posted = Json.parseToJsonElement(requests.last { it.method == "POST" }.body).jsonObject
                assertEquals(score, posted["CommunityRating"]!!.jsonPrimitive.float)
            }
        }
    }

    @Test fun serverRoundingToIntegerCannotConfirmFractionalSave() = runBlocking {
        withServer(original, readback = original.replace("7.4", "8")) { api, _ ->
            assertFailsWith<VideoRatingNotConfirmedException> {
                VideoRatingUpdater(api, "user").save("video", 8.6f)
            }
        }
    }

    @Test fun floatRepresentationNoiseDoesNotRejectConfirmedScore() = runBlocking {
        withServer(original, readback = original.replace("7.4", "8.600001")) { api, _ ->
            val confirmed = VideoRatingUpdater(api, "user").save("video", 8.6f)
            assertEquals(8.6f, confirmed.communityRating!!, 0.00001f)
        }
    }

    private suspend fun withServer(initial: String, postStatus: Int = 204, readback: String? = null,
        block: suspend (MediaServerApi, List<Request>) -> Unit) {
        val requests = CopyOnWriteArrayList<Request>()
        val document = AtomicReference(initial)
        val reads = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            requests += Request(exchange.requestMethod, exchange.requestURI.path, exchange.requestURI.rawQuery, body,
                exchange.requestHeaders.getFirst("Content-Type"))
            if (exchange.requestMethod == "POST") {
                if (postStatus == 204) document.set(body)
                exchange.sendResponseHeaders(postStatus, -1)
                exchange.close()
            } else {
                val response = (if (reads.incrementAndGet() > 1 && readback != null) readback else document.get()).toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
        }
        server.start()
        val base = "http://127.0.0.1:${server.address.port}/"
        val client = HttpClient(OkHttp) {
            engine { config { proxy(Proxy.NO_PROXY) } }
            defaultRequest { url(base) }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try { block(MediaServerApiClient(client, base), requests) }
        finally { client.close(); server.stop(0) }
    }
}
