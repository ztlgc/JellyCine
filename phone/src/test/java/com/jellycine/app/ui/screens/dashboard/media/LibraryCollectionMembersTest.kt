package com.jellycine.app.ui.screens.dashboard.media

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.shared.playback.UserDataRefreshEvent
import org.junit.Assert.*
import org.junit.Test

class LibraryCollectionMembersTest {
    @Test fun membershipsAreSharedAndExpireWithTheCatalogPolicy() {
        var now = 1_000_000L
        val members = LibraryCollectionMembers(nowMs = { now })
        val key = libraryCatalogKey("server", "url", "user", "collection:box")
        members.put(key, listOf(BaseItemDto(id = "film")))
        assertEquals(listOf("film"), members.get(key)?.map { it.id })
        now += LibraryCatalogCache.FULL_REFRESH_INTERVAL_MS
        assertNull(members.get(key))
    }

    @Test fun userDataUpdatesAndInvalidationStayInTheirOwnSession() {
        val members = LibraryCollectionMembers(nowMs = { 1_000_000L })
        val a = libraryCatalogKey("a", "url", "user", "collection:box")
        val b = libraryCatalogKey("b", "url", "user", "collection:box")
        val unplayed = listOf(BaseItemDto(id = "film", userData = UserItemDataDto(played = false)))
        members.put(a, unplayed)
        members.put(b, unplayed)

        members.updateSession("a") {
            it.withLibraryUserData(UserDataRefreshEvent("film", played = true))
        }

        assertEquals(true, members.get(a)?.single()?.userData?.played)
        assertEquals(false, members.get(b)?.single()?.userData?.played)
        members.invalidateSession("a")
        assertNull(members.get(a))
        assertNotNull(members.get(b))
    }

    @Test fun memoryIsBoundedSoSwitchingLibrariesCannotGrowItWithoutLimit() {
        val members = LibraryCollectionMembers(nowMs = { 1_000_000L })
        repeat(60) { index ->
            members.put(libraryCatalogKey("server", "url", "user", "collection:$index"),
                listOf(BaseItemDto(id = "film$index")))
        }
        assertNull(members.get(libraryCatalogKey("server", "url", "user", "collection:0")))
        assertNotNull(members.get(libraryCatalogKey("server", "url", "user", "collection:59")))
    }

    @Test fun largeMembershipListsAreEvictedWhenTheItemBudgetIsExceeded() {
        val members = LibraryCollectionMembers(nowMs = { 1_000_000L }, maxItems = 10)
        val first = libraryCatalogKey("server", "url", "user", "collection:a")
        val second = libraryCatalogKey("server", "url", "user", "collection:b")
        members.put(first, (1..8).map { BaseItemDto(id = "a$it") })
        members.put(second, (1..8).map { BaseItemDto(id = "b$it") })
        assertNull(members.get(first))
        assertNotNull(members.get(second))
    }
}
