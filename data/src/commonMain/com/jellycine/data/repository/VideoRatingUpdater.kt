package com.jellycine.data.repository

import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.network.ApiResponse
import com.jellycine.data.network.HttpStatusException
import com.jellycine.data.network.JellyCineJson
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.math.roundToInt

class VideoRatingPermissionException : Exception("Media metadata editing permission is required")
class VideoRatingNotConfirmedException : Exception("The server did not confirm the requested rating")
class VideoRatingSessionException : Exception("The active server or account changed")

/** CommunityRating is shared media metadata, visible in other clients, rather than a local vote. */
class VideoRatingUpdater(private val api: MediaServerApi, private val userId: String) {
    suspend fun load(itemId: String): BaseItemDto = decode(loadDocument(itemId))

    suspend fun save(itemId: String, score: Float): BaseItemDto {
        require(score.isFinite() && score in 1f..10f)
        val tenths = (score * 10).roundToInt()
        require(abs(score * 10 - tenths) < 0.0001f) { "Expected a rating in 0.1-point increments" }
        val normalizedScore = tenths / 10f
        val original = loadDocument(itemId)
        if ((original["CanEditItems"] as? JsonPrimitive)?.booleanOrNull == false) {
            throw VideoRatingPermissionException()
        }
        // A partial DTO POST can clear unrelated Emby metadata. Preserve the entire fresh document.
        val fields = original["LockedFields"] as? JsonArray ?: JsonArray(emptyList())
        val lockedFields = if (fields.any { (it as? JsonPrimitive)?.contentOrNull == "CommunityRating" })
            fields else JsonArray(fields + JsonPrimitive("CommunityRating"))
        val updated = JsonObject(original + mapOf(
            "CommunityRating" to JsonPrimitive(normalizedScore), "LockedFields" to lockedFields
        ))
        requireSuccess(api.updateItem(itemId, updated))
        val confirmed = loadDocument(itemId)
        val returnedScore = (confirmed["CommunityRating"] as? JsonPrimitive)?.floatOrNull
        if (returnedScore == null || !returnedScore.isFinite() || abs(returnedScore - normalizedScore) > 0.00001f) {
            throw VideoRatingNotConfirmedException()
        }
        return decode(confirmed)
    }

    private suspend fun loadDocument(itemId: String): JsonObject {
        require(itemId.isNotBlank())
        val response = api.getItemForUpdate(userId, itemId)
        requireSuccess(response)
        val item = checkNotNull(response.body()) { "Missing media document" }
        check((item["Id"] as? JsonPrimitive)?.contentOrNull == itemId) { "Unexpected media identity" }
        check((item["Type"] as? JsonPrimitive)?.contentOrNull.equals("Video", true) &&
            (item["IsFolder"] as? JsonPrimitive)?.booleanOrNull != true) { "Expected an ordinary video" }
        return item
    }

    private fun decode(item: JsonObject): BaseItemDto = JellyCineJson.decodeFromJsonElement(item)

    private fun requireSuccess(response: ApiResponse<*>) {
        if (response.code() == 403) throw VideoRatingPermissionException()
        if (!response.isSuccessful) throw HttpStatusException(response.code(), "HTTP ${response.code()}")
    }
}
