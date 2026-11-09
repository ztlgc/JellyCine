package com.jellycine.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    private fun version(text: String) = requireNotNull(AppVersion.parse(text)) { "无法解析 $text" }

    @Test
    fun parsesFourSegmentVersionAndTagPrefix() {
        assertEquals("1.3.6.1", version("1.3.6.1").toString())
        assertEquals("1.3.6.1", version("v1.3.6.1-android-20261108").toString())
        assertEquals("1.3.6", version("v1.3.6-android-20261031").toString())
    }

    @Test
    fun rejectsTextWithoutLeadingVersion() {
        assertNull(AppVersion.parse("android-20261031"))
        assertNull(AppVersion.parse("latest"))
    }

    @Test
    fun comparesSegmentsNumericallyAndTreatsMissingAsZero() {
        assertTrue(version("1.3.6.1") > version("1.3.6"))
        assertTrue(version("1.10.0") > version("1.9.9"))
        assertEquals(0, version("1.3.6").compareTo(version("1.3.6.0")))
        assertTrue(version("1.3.6.2") > version("1.3.6.1"))
    }

    @Test
    fun picksHighestNonDraftReleaseAndSkipsInvalidEntries() {
        val json = """
            [
              {"tag_name": "v1.3.6.2", "html_url": "https://example/d", "draft": true},
              {"tag_name": "v1.3.6.1-android-20261108", "html_url": "https://example/a", "draft": false},
              {"tag_name": "v1.3.5", "html_url": "https://example/b"},
              {"tag_name": "nightly", "html_url": "https://example/c"},
              {"html_url": "https://example/e"}
            ]
        """.trimIndent()

        val latest = GithubReleaseParser.latestRelease(json)

        assertEquals("v1.3.6.1-android-20261108", latest?.tagName)
        assertEquals("https://example/a", latest?.htmlUrl)
    }

    @Test
    fun returnsNullForEmptyOrMalformedPayload() {
        assertNull(GithubReleaseParser.latestRelease("[]"))
        assertNull(GithubReleaseParser.latestRelease("{\"message\": \"rate limited\"}"))
    }

    @Test
    fun onlyReportsReleaseNewerThanCurrent() {
        val current = version("1.3.6.1")
        val sameRelease = ReleaseInfo(version("1.3.6.1"), "v1.3.6.1", "u")
        val newerRelease = ReleaseInfo(version("1.3.6.2"), "v1.3.6.2", "u")

        assertNull(AppUpdateRules.newerReleaseOrNull(current, sameRelease))
        assertEquals(newerRelease, AppUpdateRules.newerReleaseOrNull(current, newerRelease))
        assertNull(AppUpdateRules.newerReleaseOrNull(current, null))
    }

    @Test
    fun autoCheckRespectsIntervalAndClockRollback() {
        val now = 10_000_000_000L
        val day = AppUpdateRules.CHECK_INTERVAL_MS

        assertTrue(AppUpdateRules.shouldAutoCheck(lastSuccessMs = 0L, nowMs = now))
        assertTrue(AppUpdateRules.shouldAutoCheck(lastSuccessMs = now - day, nowMs = now))
        assertTrue(AppUpdateRules.shouldAutoCheck(lastSuccessMs = now + 5_000L, nowMs = now))
        assertEquals(false, AppUpdateRules.shouldAutoCheck(lastSuccessMs = now - 1_000L, nowMs = now))
    }
}
