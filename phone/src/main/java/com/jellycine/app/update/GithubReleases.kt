package com.jellycine.app.update

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class ReleaseInfo(
    val version: AppVersion,
    val tagName: String,
    val htmlUrl: String
)

/** 本仓库（origin: ztlgc/JellyCine）的 GitHub Release 源。 */
internal const val GithubReleasesApiUrl = "https://api.github.com/repos/ztlgc/JellyCine/releases?per_page=30"

internal object GithubReleaseParser {

    /**
     * 从 `/releases` 列表 JSON 中挑选版本号最大的一条（跳过草稿与无法解析版本号的条目）。
     * 解析失败或列表为空时返回 null。
     */
    fun latestRelease(json: String): ReleaseInfo? {
        val releases = Json.parseToJsonElement(json) as? JsonArray ?: return null
        return releases.mapNotNull { element -> (element as? JsonObject)?.toReleaseInfo() }
            .maxByOrNull { it.version }
    }

    private fun JsonObject.toReleaseInfo(): ReleaseInfo? {
        if (this["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
        val tag = this["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val url = this["html_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val version = AppVersion.parse(tag) ?: return null
        return ReleaseInfo(version = version, tagName = tag, htmlUrl = url)
    }
}

internal suspend fun fetchLatestRelease(client: OkHttpClient): ReleaseInfo? = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(GithubReleasesApiUrl)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "JellyCine-Android")
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("GitHub Releases 请求失败：HTTP ${response.code}")
        GithubReleaseParser.latestRelease(response.body.string())
    }
}
