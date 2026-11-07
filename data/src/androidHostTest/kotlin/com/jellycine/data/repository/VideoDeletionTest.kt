package com.jellycine.data.repository

import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.api.MediaServerApiClient
import com.jellycine.data.network.JellyCineJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VideoDeletionTest {
    private val target = VideoDeletionTarget(
        serverId = "https://original.example/emby|original-user",
        serverUrl = "https://original.example/emby/",
        userId = "original-user",
        serverTypeRaw = "EMBY",
        itemId = "video",
        name = "家庭旅行"
    )

    private data class Request(
        val method: HttpMethod,
        val url: Url,
        val authorization: String?,
        val cacheControl: String?
    )

    private suspend fun delete(target: VideoDeletionTarget, api: MediaServerApi) = deleteVideoWithSession(
        target, api, target.serverId, target.serverUrl, target.userId
    )

    @Test fun everySuccessfulDeleteCompletesWithoutAnExtraRequest() = runBlocking {
        listOf(HttpStatusCode.OK, HttpStatusCode.Accepted, HttpStatusCode.NoContent).forEach { status ->
            withApi(deleteStatus = status) { api, requests ->
                assertEquals(VideoDeletionOutcome.COMPLETE, delete(target, api))
                val request = requests.single()
                assertEquals(HttpMethod.Delete, request.method)
                assertEquals("/emby/Items", request.url.encodedPath)
                assertEquals("video", request.url.parameters["Ids"])
                assertEquals("original.example", request.url.host)
                assertEquals("original-token", request.authorization)
            }
        }
    }

    @Test fun permissionAndOtherHttpFailuresRemainRetryable() = runBlocking {
        listOf(400, 401, 403, 405, 410, 429, 500, 503).forEach { status ->
            withApi(deleteStatus = HttpStatusCode.fromValue(status)) { api, requests ->
                assertEquals(VideoDeletionOutcome.RETRY, delete(target, api), "HTTP $status")
                assertEquals(1, requests.size)
            }
        }
    }

    @Test fun missingDeleteEndpointCompletesOnlyAfterFreshBoundLookupProvesAbsence() = runBlocking {
        withApi(deleteStatus = HttpStatusCode.NotFound) { api, requests ->
            assertEquals(VideoDeletionOutcome.COMPLETE, delete(target, api))
            assertEquals(listOf(HttpMethod.Delete, HttpMethod.Get), requests.map { it.method })
            val lookup = requests.last()
            assertEquals("/emby/Users/original-user/Items", lookup.url.encodedPath)
            assertEquals("video", lookup.url.parameters["ids"])
            assertEquals("true", lookup.url.parameters["recursive"])
            assertEquals("1", lookup.url.parameters["limit"])
            assertEquals("no-cache", lookup.cacheControl)
            assertTrue(requests.all { it.url.host == "original.example" && it.authorization == "original-token" })
        }
    }

    @Test fun genericProxy404AndFailedLookupsCannotConfirmAbsence() = runBlocking {
        listOf(401, 403, 404, 500).forEach { status ->
            withApi(
                deleteStatus = HttpStatusCode.NotFound,
                lookupStatus = HttpStatusCode.fromValue(status),
                lookupBody = "<html>Not Found</html>"
            ) { api, requests ->
                assertEquals(VideoDeletionOutcome.RETRY, delete(target, api), "Lookup HTTP $status")
                assertEquals(2, requests.size)
            }
        }
    }

    @Test fun itemStillPresentOrIncompleteLookupDocumentRemainsRetryable() = runBlocking {
        listOf(
            """{"Items":[{"Id":"video","Type":"Video"}],"TotalRecordCount":1}""",
            """{"Items":[{"Id":"unrelated"}],"TotalRecordCount":1}""",
            """{"Items":[],"TotalRecordCount":1}""",
            """{"Items":[]}""",
            """{"TotalRecordCount":0}""",
            """{}""",
            "not a media response"
        ).forEach { document ->
            withApi(deleteStatus = HttpStatusCode.NotFound, lookupBody = document) { api, _ ->
                assertEquals(VideoDeletionOutcome.RETRY, delete(target, api))
            }
        }
    }

    @Test fun naturalLanguageErrorTextNeverProvesAnItemIsAbsent() = runBlocking {
        listOf("Item not found", "视频不存在", "资源已被删除").forEach { message ->
            withApi(
                deleteStatus = HttpStatusCode.NotFound,
                deleteBody = message,
                lookupStatus = HttpStatusCode.NotFound,
                lookupBody = message
            ) { api, _ ->
                assertEquals(VideoDeletionOutcome.RETRY, delete(target, api))
            }
        }
    }

    @Test fun lostDeleteResponseAndVerificationNetworkFailureRemainRetryable() = runBlocking {
        withApi(deleteFailure = IOException("Lost response after server commit")) { api, requests ->
            assertEquals(VideoDeletionOutcome.RETRY, delete(target, api))
            assertEquals(1, requests.size)
        }
        withApi(deleteStatus = HttpStatusCode.NotFound, lookupFailure = IOException("Disconnected")) { api, requests ->
            assertEquals(VideoDeletionOutcome.RETRY, delete(target, api))
            assertEquals(2, requests.size)
        }
    }

    @Test fun switchedOrIncompleteSessionCannotMakeARequest() = runBlocking {
        withApi { api, requests ->
            listOf(
                Triple("another-account", target.serverUrl, target.userId),
                Triple(target.serverId, "https://other.example/emby/", target.userId),
                Triple(target.serverId, target.serverUrl, "other-user")
            ).forEach { (id, url, user) ->
                assertEquals(VideoDeletionOutcome.SESSION_UNAVAILABLE, deleteVideoWithSession(target, api, id, url, user))
            }
            assertEquals(VideoDeletionOutcome.SESSION_UNAVAILABLE, delete(target.copy(itemId = ""), api))
            assertTrue(requests.isEmpty())
        }
    }

    @Test fun cancellationPropagatesWithoutBecomingARetryOutcome() = runBlocking {
        withApi(deleteFailure = CancellationException("Worker stopped")) { api, _ ->
            assertFailsWith<CancellationException> { delete(target, api) }
        }
        withApi(deleteStatus = HttpStatusCode.NotFound, lookupFailure = CancellationException("Worker stopped")) { api, _ ->
            assertFailsWith<CancellationException> { delete(target, api) }
        }
    }

    private suspend fun withApi(
        deleteStatus: HttpStatusCode = HttpStatusCode.NoContent,
        deleteBody: String = "",
        lookupStatus: HttpStatusCode = HttpStatusCode.OK,
        lookupBody: String = """{"Items":[],"TotalRecordCount":0}""",
        deleteFailure: Exception? = null,
        lookupFailure: Exception? = null,
        block: suspend (MediaServerApi, List<Request>) -> Unit
    ) {
        val requests = mutableListOf<Request>()
        val engine = MockEngine { request ->
            requests += Request(request.method, request.url, request.headers[HttpHeaders.Authorization], request.headers[HttpHeaders.CacheControl])
            if (request.method == HttpMethod.Delete) {
                deleteFailure?.let { throw it }
                respond(deleteBody, deleteStatus)
            } else {
                lookupFailure?.let { throw it }
                respond(lookupBody, lookupStatus, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        HttpClient(engine) {
            defaultRequest {
                url(target.serverUrl)
                header(HttpHeaders.Authorization, "original-token")
            }
            install(ContentNegotiation) { json(JellyCineJson) }
        }.use { client ->
            block(MediaServerApiClient(client, target.serverUrl), requests)
        }
    }
}
