package com.jellycine.logging

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RotatingPlaybackLogTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun survivesReopeningAndKeepsChronologicalOrder() {
        val directory = temporary.newFolder()
        val log = RotatingPlaybackLog(directory, 128, 3)
        repeat(16) { log.append("event=$it " + "x".repeat(20)) }
        val restored = RotatingPlaybackLog(directory, 128, 3).read()
        assertEquals((4..15).toList(), restored.lines().filter { it.isNotBlank() }
            .map { it.substringAfter("event=").substringBefore(' ').toInt() })
        assertTrue(directory.listFiles()!!.size <= 3)
        assertTrue(directory.listFiles()!!.all { it.length() <= 128 })
    }

    @Test fun oversizedMultilineUnicodeEventsRemainBounded() {
        val directory = temporary.newFolder()
        val log = RotatingPlaybackLog(directory, 128, 1)
        repeat(10) { log.append("卡顿\n\r".repeat(1000)) }
        assertEquals(1, directory.listFiles()!!.size)
        assertTrue(directory.listFiles()!!.single().length() <= 128)
        assertFalse(log.read().contains('\r'))
        assertTrue(log.read().lines().all { it.length <= 31 })
    }
}
