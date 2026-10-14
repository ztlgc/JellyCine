package com.jellycine.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeSnapshotCompatibilityTest {
    @Test fun olderSnapshotsLoadWithoutRecentWatchingFields() {
        val oldSnapshot = """{
            "snapshotKey":"server-user", "updatedAt":1,
            "featuredHomeItems":[], "continueWatchingItems":[], "homeLibrarySections":[]
        }"""
        val snapshot = Json.decodeFromString<PersistedHomeSnapshot>(oldSnapshot)
        assertEquals("server-user", snapshot.snapshotKey)
        assertNull(snapshot.recentWatchingItems)
        assertNull(snapshot.recentOtherVideoItems)
    }
}
