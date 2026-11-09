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

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String
)

data class ReleaseInfo(
    val version: AppVersion,
    val tagName: String,
    val htmlUrl: String,
    val assets: List<ReleaseAsset> = emptyList()
) {

    /**
     * 选择与设备 ABI 匹配的安装包：优先按设备 ABI 顺序取第一个匹配项，
     * 其次接受文件名未标注 ABI 的通用包；都没有则返回 null（只能打开 release 页面）。
     */
    fun apkFor(supportedAbis: List<String>): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null
        supportedAbis.forEach { abi ->
            apks.firstOrNull { it.detectAbi() == abi }?.let { return it }
        }
        return apks.firstOrNull { it.detectAbi() == null }
    }
}

/** 从文件名的 ABI 片段推断目标 ABI；无法判断时返回 null（视为通用包）。 */
internal fun ReleaseAsset.detectAbi(): String? {
    val name = name.lowercase()
    return when {
        "arm64-v8a" in name || "arm64" in name || "aarch64" in name -> "arm64-v8a"
        "armeabi-v7a" in name || "armv7" in name || "armeabi" in name -> "armeabi-v7a"
        "x86_64" in name || "x86-64" in name -> "x86_64"
        "x86" in name -> "x86"
        else -> null
    }
}

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
        val assets = (this["assets"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.toReleaseAsset() }
        return ReleaseInfo(version = version, tagName = tag, htmlUrl = url, assets = assets)
    }

    private fun JsonObject.toReleaseAsset(): ReleaseAsset? {
        val name = this["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val url = this["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null
        return ReleaseAsset(name = name, downloadUrl = url)
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
