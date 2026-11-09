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
class VideoTagsNotConfirmedException : Exception("The server did not confirm the requested tags")

/** CommunityRating is shared media metadata, visible in other clients, rather than a local vote. */
class VideoRatingUpdater(
    private val api: MediaServerApi,
    private val userId: String,
    private val isEmby: Boolean = false
) {
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

    /**
     * Replaces the server-side Tags list. Tags are shared metadata, so the write follows the same
     * full-document, LockedFields and readback rules as the rating.
     */
    suspend fun saveTags(itemId: String, tags: List<String>): BaseItemDto {
        val normalized = normalizeVideoTags(tags)
        val original = loadDocument(itemId)
        if ((original["CanEditItems"] as? JsonPrimitive)?.booleanOrNull == false) {
            throw VideoRatingPermissionException()
        }
        val fields = original["LockedFields"] as? JsonArray ?: JsonArray(emptyList())
        val lockedFields = if (fields.any { (it as? JsonPrimitive)?.contentOrNull == "Tags" })
            fields else JsonArray(fields + JsonPrimitive("Tags"))
        // Emby 使用 TagItems（NameLongIdPair[]）；只改 Tags 会留下旧 TagItems，
        // 服务端可能返回成功但继续保留旧标签。已有标签保留其 ID，新标签只提交名称。
        val changes = mutableMapOf<String, JsonElement>(
            "Tags" to JsonArray(normalized.map { JsonPrimitive(it) }), "LockedFields" to lockedFields
        )
        if (isEmby || original["TagItems"] is JsonArray) {
            val existing = (original["TagItems"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
            changes["TagItems"] = JsonArray(normalized.map { name ->
                existing.firstOrNull { (it["Name"] as? JsonPrimitive)?.contentOrNull == name }
                    ?: buildJsonObject { put("Name", name) }
            })
        }
        val updated = JsonObject(original + changes)
        requireSuccess(api.updateItem(itemId, updated))
        val confirmed = loadDocument(itemId)
        val returned = readTags(confirmed)
        if (returned.size != normalized.size || returned.toSet() != normalized.toSet()) {
            throw VideoTagsNotConfirmedException()
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

    private fun readTags(item: JsonObject): List<String> {
        // TagItems 是 Emby 的权威字段；空数组也必须优先，才能确认删除全部标签。
        val tagItems = item["TagItems"] as? JsonArray
        val values = if (tagItems != null) tagItems.map { element ->
            when (element) {
                is JsonObject -> (element["Name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                is JsonPrimitive -> element.contentOrNull.orEmpty()
                else -> error("Invalid TagItems entry")
            }
        } else (item["Tags"] as? JsonArray).orEmpty().map {
            (it as? JsonPrimitive)?.contentOrNull ?: error("Invalid Tags entry")
        }
        return normalizeVideoTags(values)
    }

    private fun decode(item: JsonObject): BaseItemDto =
        JellyCineJson.decodeFromJsonElement<BaseItemDto>(item).copy(tags = readTags(item))

    private fun requireSuccess(response: ApiResponse<*>) {
        if (response.code() == 403) throw VideoRatingPermissionException()
        if (!response.isSuccessful) throw HttpStatusException(response.code(), "HTTP ${response.code()}")
    }
}

/** Trims whitespace, drops blank entries and removes duplicates while keeping the user's order. */
fun normalizeVideoTags(tags: Collection<String>): List<String> =
    tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
