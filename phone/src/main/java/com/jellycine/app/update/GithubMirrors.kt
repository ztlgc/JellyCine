package com.jellycine.app.update

/**
 * 社区维护的 GitHub 下载加速镜像：把原始 GitHub 链接直接拼在镜像地址后面即可。
 *
 * 这类服务由第三方公益提供，域名可能随时失效，因此界面同时保留 GitHub 官方链接。
 * 按 2026-11 实测结果排序（`/releases/download/...` 资源均可命中）。
 */
data class GithubMirror(val label: String, val prefix: String)

object GithubMirrors {

    val all: List<GithubMirror> = listOf(
        GithubMirror(label = "gh-proxy.com", prefix = "https://gh-proxy.com/"),
        GithubMirror(label = "ghfast.top", prefix = "https://ghfast.top/")
    )

    fun url(mirror: GithubMirror, target: String): String = mirror.prefix + target
}
