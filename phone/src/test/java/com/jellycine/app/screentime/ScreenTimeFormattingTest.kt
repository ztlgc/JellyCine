package com.jellycine.app.screentime

import com.jellycine.shared.ui.components.screentime.formatWatchTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeFormattingTest {
    @Test
    fun chineseDurationsUseReadableUnits() {
        assertEquals("12小时48分", formatWatchTime(768 * 60_000L, Locale.SIMPLIFIED_CHINESE))
        assertEquals("24分46秒", formatWatchTime(1_486_000, Locale.SIMPLIFIED_CHINESE))
        assertEquals("4小时", formatWatchTime(14_400_000, Locale.SIMPLIFIED_CHINESE))
        assertEquals("2分", formatWatchTime(120_000, Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun subSecondWatchTimeRemainsDistinctFromNoViewing() {
        assertEquals("<1秒", formatWatchTime(450, Locale.SIMPLIFIED_CHINESE))
        assertEquals("0秒", formatWatchTime(0, Locale.SIMPLIFIED_CHINESE))
        assertEquals("0秒", formatWatchTime(-1, Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun englishDurationsKeepCompactUnits() {
        assertEquals("12h 48m", formatWatchTime(768 * 60_000L, Locale.ENGLISH))
        assertEquals("24m 46s", formatWatchTime(1_486_000, Locale.ENGLISH))
        assertEquals("<1s", formatWatchTime(450, Locale.ENGLISH))
        assertEquals("0s", formatWatchTime(0, Locale.ENGLISH))
    }

    @Test
    fun resourceLocaleOverridesTheDeviceDefault() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("1小时", formatWatchTime(3_600_000, Locale.SIMPLIFIED_CHINESE))
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            assertEquals("1h", formatWatchTime(3_600_000, Locale.ENGLISH))
        } finally {
            Locale.setDefault(original)
        }
    }
}
