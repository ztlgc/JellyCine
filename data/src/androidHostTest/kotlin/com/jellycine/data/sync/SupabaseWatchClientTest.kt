package com.jellycine.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class SupabaseWatchClientTest {
    private val config=SupabaseWatchConfig("https://example.supabase.co","sb_publishable_test")
    @Test fun passwordLoginSendsOnlyPublishableKeyAndUserEnteredCredentials()=runBlocking {
        val engine=MockEngine { request ->
            assertEquals("/auth/v1/token",request.url.encodedPath)
            assertEquals("password",request.url.parameters["grant_type"])
            assertEquals(config.publishableKey,request.headers["apikey"])
            assertNull(request.headers[HttpHeaders.Authorization])
            val body=Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("email","password"),body.keys)
            assertEquals("person@example.com",body.getValue("email").jsonPrimitive.content)
            respond("""{"access_token":"access","refresh_token":"refresh","expires_at":2000000000,"user":{"id":"owner","email":"person@example.com"}}""",
                headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }
        HttpClient(engine).use { http ->
            val session=SupabaseWatchClient(config,http).signIn(" person@example.com ","entered-password")
            assertEquals("owner",session.user.id)
        }
    }
    @Test fun pushUsesAuthenticatedRpcAndStableRecordVersions()=runBlocking {
        val span=WatchSyncSpan("span","session","scope",100,200,3)
        val engine=MockEngine {request ->
            assertEquals("/rest/v1/rpc/jellycine_watch_push",request.url.encodedPath)
            assertEquals("Bearer user-session",request.headers[HttpHeaders.Authorization])
            val body=Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(3L,body.getValue("p_spans").jsonArray.single().jsonObject.getValue("version").jsonPrimitive.long)
            assertEquals(setOf("p_sessions","p_spans"),body.keys)
            respond("",HttpStatusCode.NoContent)
        }
        HttpClient(engine).use { SupabaseWatchClient(config,it).remote("user-session").push(WatchSyncBatch(spans=listOf(span))) }
    }
    @Test fun failuresNeverExposeResponseBodiesContainingCredentials()=runBlocking {
        val engine=MockEngine {respond("secret server diagnostics",HttpStatusCode.Unauthorized)}
        HttpClient(engine).use {
            val error=assertFailsWith<WatchSyncHttpException> {SupabaseWatchClient(config,it).remote("access").pull(0,400)}
            assertEquals(401,error.status)
            assertFalse(error.message.orEmpty().contains("secret"))
        }
    }
    @Test fun legacyAndSecretKeysCannotEnableTheClient() {
        assertFalse(SupabaseWatchConfig("https://example.supabase.co","sb_secret_invalid").configured)
        assertFalse(SupabaseWatchConfig("http://example.supabase.co","sb_publishable_test").configured)
        assertFalse(SupabaseWatchConfig("https://example.supabase.co","eyJlegacy").configured)
    }
    @Test fun signupWaitsForEmailVerificationInsteadOfTreatingAUserAsASession()=runBlocking {
        val engine=MockEngine { request ->
            assertEquals("/auth/v1/signup",request.url.encodedPath)
            assertNull(request.headers[HttpHeaders.Authorization])
            respond("""{"user":{"id":"owner"},"access_token":null}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }
        HttpClient(engine).use {assertNull(SupabaseWatchClient(config,it).signUp("person@example.com","entered-password"))}
    }
}
