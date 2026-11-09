package com.jellycine.app.update

/**
 * 版本号，按数字段比较。支持 `1.3.6`、`1.3.6.1`，以及以版本号开头的 Release tag，
 * 如 `v1.3.6.1-android-20261108`（只取开头的 `x.y[.z[.w]]`）。缺失的段按 0 处理，
 * 因此 `1.3.6` 与 `1.3.6.0` 视为相等。
 */
data class AppVersion(val segments: List<Int>) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(segments.size, other.segments.size)
        for (index in 0 until size) {
            val diff = segments.getOrElse(index) { 0 }.compareTo(other.segments.getOrElse(index) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    override fun toString(): String = segments.joinToString(".")

    companion object {
        // 锚定到开头，避免 `android-20261031` 之类的 tag 被误当作版本号。
        private val VersionPattern = Regex("""^v?(\d+(?:\.\d+){0,3})""")

        fun parse(raw: String): AppVersion? {
            val text = VersionPattern.find(raw.trim())?.groupValues?.get(1) ?: return null
            val parts = text.split('.').map { it.toIntOrNull() ?: return null }
            return AppVersion(parts)
        }
    }
}
