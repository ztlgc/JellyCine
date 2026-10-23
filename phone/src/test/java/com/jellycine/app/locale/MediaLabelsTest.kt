package com.jellycine.app.locale

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.shared.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MediaLabelsTest {
    private fun context(language: String): Context {
        val base = RuntimeEnvironment.getApplication()
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(config)
    }

    @Test fun chineseDisplayDoesNotChangeSavedQualityOrSubtitleSettings() {
        val context = context("zh-CN")
        val preferences = PlayerPreferences(context)
        preferences.setStreamingQuality(PlayerPreferences.STREAMING_QUALITY_ORIGINAL)
        preferences.setSubtitleTextSize(PlayerPreferences.SUBTITLE_TEXT_SIZE_EXTRA_LARGE)
        assertEquals("原画", context.localizedQualityLabel(preferences.getStreamingQuality()))
        assertEquals("Original", preferences.getStreamingQuality())
        assertEquals("Extra Large", preferences.getSubtitleTextSize())
        assertEquals("1080p (10 Mbps)", context.localizedQualityLabel("1080p (10 Mbps)"))
    }

    @Test fun chinesePlaybackLabelsKeepCodecAndCustomTitleIntact() {
        val context = context("zh-CN")
        assertEquals("关闭", context.localizedPlaybackLabel("Off"))
        assertEquals("直接播放", context.localizedPlaybackLabel("DirectPlay"))
        assertEquals("音轨 2", context.localizedPlaybackLabel("Audio 2"))
        assertEquals("字幕 3", context.localizedPlaybackLabel("Subtitle 3"))
        assertEquals("3 声道", context.localizedPlaybackLabel("3ch"))
        assertEquals("Dolby Vision", context.localizedPlaybackLabel("Dolby Vision"))
        assertEquals("English commentary", context.localizedPlaybackLabel("English commentary"))
    }

    @Test fun englishResourcesRemainAvailable() {
        val context = context("en-US")
        assertEquals("Off", context.localizedPlaybackLabel("Off"))
        assertEquals("Original", context.localizedQualityLabel("Original"))
        assertEquals("Direct Play", context.localizedPlaybackLabel("DirectPlay"))
    }

    @Test fun languageNamesUseTheConfiguredAppLocale() {
        assertTrue(context("zh-CN").localizedLanguageName("eng") in setOf("英语", "英文"))
        assertEquals("English", context("en-US").localizedLanguageName("eng"))
        assertEquals("", context("zh-CN").localizedLanguageName("und"))
    }

    @Test fun itemCountUsesBothArguments() {
        val context = context("zh-CN")
        assertEquals("已显示 12 / 98 项", context.getString(R.string.view_all_count, 12, 98))
    }

    @Test fun durationsUseChineseUnitsAndKeepPrecisePlaybackTime() {
        val context = context("zh-CN")
        assertEquals("1 小时 5 分钟", context.localizedRuntime(65L * 60 * 10_000_000))
        assertEquals("5 分钟", context.localizedRuntime(5L * 60 * 10_000_000))
        assertEquals("1:05:00", context.localizedRuntime(65L * 60 * 10_000_000, true))
        assertEquals("", context.localizedRuntime(null))
    }

    @Test fun transportErrorsUseChineseAndKeepExistingChineseDetails() {
        val context = context("zh-CN")
        assertEquals("连接超时，服务器可能无法访问。", context.userFacingError(SocketTimeoutException("Read timed out"), R.string.ui_playback_failed))
        assertEquals("播放失败，请重试。", context.userFacingError(Exception("Unexpected codec failure"), R.string.ui_playback_failed))
        assertEquals("服务器拒绝访问", context.userFacingError(Exception("服务器拒绝访问"), R.string.ui_playback_failed))
    }
}
