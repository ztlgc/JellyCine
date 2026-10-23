package com.jellycine.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class SupabaseWatchConfig(val url: String, val publishableKey: String) {
    val configured: Boolean get() = url.startsWith("https://") && publishableKey.startsWith("sb_publishable_")
}

@Serializable
data class WatchSyncUser(val id: String, val email: String = "")

@Serializable
data class WatchSyncAuthSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long = 0,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val user: WatchSyncUser
)

class WatchSyncHttpException(val status: Int, val authentication: Boolean = false) : Exception("Watch sync HTTP $status")

/** Dedicated client: never inherits Emby authorization, proxy bypass, or request logging. */
class SupabaseWatchClient(private val config: SupabaseWatchConfig, httpClient: HttpClient? = null) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = httpClient ?: HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 10_000 }
        followRedirects = false
    }

    private fun decodeSession(body:String): WatchSyncAuthSession = json.decodeFromString<WatchSyncAuthSession>(body).let {
        if (it.expiresAt>0) it else it.copy(expiresAt=System.currentTimeMillis()/1000+it.expiresIn)
    }

    suspend fun signIn(email: String, password: String): WatchSyncAuthSession = decodeSession(
        request("auth/v1/token?grant_type=password", buildJsonObject {
            put("email", email.trim()); put("password", password)
        }.toString())
    )

    suspend fun refresh(refreshToken: String): WatchSyncAuthSession = decodeSession(
        request("auth/v1/token?grant_type=refresh_token", buildJsonObject {
            put("refresh_token", refreshToken)
        }.toString())
    )

    /** With email confirmation enabled, signup returns a user but no usable session. */
    suspend fun signUp(email: String, password: String): WatchSyncAuthSession? {
        val response=request("auth/v1/signup",buildJsonObject {
            put("email",email.trim());put("password",password)
        }.toString())
        if (json.parseToJsonElement(response).jsonObject["access_token"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) return null
        return decodeSession(response)
    }

    fun remote(accessToken: String): WatchSyncRemote = object : WatchSyncRemote {
        override suspend fun push(batch: WatchSyncBatch) {
            val payload=json.encodeToJsonElement(WatchSyncBatch.serializer(),batch) as kotlinx.serialization.json.JsonObject
            request("rest/v1/rpc/jellycine_watch_push", buildJsonObject {
                put("p_sessions",payload.getValue("sessions"))
                put("p_spans",payload.getValue("spans"))
            }.toString(), accessToken)
        }

        override suspend fun pull(after: Long, limit: Int): WatchSyncPage = json.decodeFromString(
            request("rest/v1/rpc/jellycine_watch_pull", buildJsonObject {
                put("p_after", after); put("p_limit", limit)
            }.toString(), accessToken)
        )
    }

    private suspend fun request(path: String, body: String, token: String? = null): String {
        check(config.configured) { "Supabase watch sync is not configured" }
        val response = client.post("${config.url.trimEnd('/')}/$path") {
            contentType(ContentType.Application.Json)
            header("apikey", config.publishableKey)
            if (token != null) header("Authorization", "Bearer $token")
            setBody(body)
        }
        if (response.status.value !in 200..299) throw WatchSyncHttpException(response.status.value,path.startsWith("auth/"))
        return response.bodyAsText()
    }
}
