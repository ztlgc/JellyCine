package com.jellycine.data.repository

import com.jellycine.data.network.JellyCineJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDeletionTargetTest {
    private val target = VideoDeletionTarget(
        serverId = "https://original.example/emby|original-user",
        serverUrl = "https://original.example/emby/",
        userId = "original-user",
        serverTypeRaw = "EMBY",
        itemId = "video",
        name = "家庭旅行"
    )

    @Test fun persistedTargetContainsIdentityAndDisplayNameWithoutCredentials() {
        val encoded = JellyCineJson.encodeToString(target)
        assertEquals(
            setOf("serverId", "serverUrl", "userId", "serverTypeRaw", "itemId", "name"),
            JellyCineJson.parseToJsonElement(encoded).jsonObject.keys
        )
        assertEquals(target, JellyCineJson.decodeFromString<VideoDeletionTarget>(encoded))
        val unknownType = target.copy(serverTypeRaw = null)
        assertEquals(unknownType, JellyCineJson.decodeFromString<VideoDeletionTarget>(JellyCineJson.encodeToString(unknownType)))
    }

    @Test fun sessionBindingAllowsTrailingSlashNormalization() {
        assertTrue(target.matchesSession(target.serverId, "https://original.example/emby", target.userId))
    }

    @Test fun switchedAccountOrChangedServerCannotReuseTheTarget() {
        assertFalse(target.matchesSession("https://original.example/emby|other-user", target.serverUrl, "other-user"))
        assertFalse(target.matchesSession(target.serverId, "https://other.example/emby/", target.userId))
        assertFalse(target.matchesSession(target.serverId, "https://original.example/Emby/", target.userId))
        assertFalse(target.matchesSession("other-session", target.serverUrl, target.userId))
        assertFalse(target.matchesSession(target.serverId, target.serverUrl, "other-user"))
    }

    @Test fun incompletePersistedIdentityCannotIssueDeletionRequests() {
        listOf(
            target.copy(serverId = ""),
            target.copy(serverUrl = " "),
            target.copy(userId = ""),
            target.copy(itemId = " ")
        ).forEach { incomplete ->
            assertFalse(incomplete.matchesSession(incomplete.serverId, incomplete.serverUrl, incomplete.userId))
        }
    }
}
