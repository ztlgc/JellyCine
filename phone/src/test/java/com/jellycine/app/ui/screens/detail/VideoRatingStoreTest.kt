package com.jellycine.app.ui.screens.detail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path
import com.jellycine.data.preferences.VideoRatingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VideoRatingStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun scoreSurvivesReopeningTheStoreAndCanBeChanged() = runBlocking {
        val file = temporary.root.resolve("ratings.preferences_pb")
        val firstJob = SupervisorJob()
        val firstStore = VideoRatingStore(ratingDataStore(
            scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file.absolutePath.toPath() }
        ))
        try {
            assertNull(firstStore.observeRating("session", "video").first())
            firstStore.saveRating("session", "video", 1)
            firstStore.saveRating("session", "video", 10)
        } finally {
            firstJob.cancelAndJoin()
        }
        val secondJob = SupervisorJob()
        try {
            val reopened = VideoRatingStore(ratingDataStore(
                scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file.absolutePath.toPath() }
            ))
            assertEquals(10, reopened.observeRating("session", "video").first())
        } finally {
            secondJob.cancelAndJoin()
        }
    }

    @Test fun ratingsAreIsolatedByAccountServerAndVideo() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = VideoRatingStore(ratingDataStore(
                scope = CoroutineScope(job + Dispatchers.IO),
                produceFile = { temporary.root.resolve("scopes.preferences_pb").absolutePath.toPath() }
            ))
            store.saveRating("serverA-userA", "video", 8)
            store.saveRating("serverA-userB", "video", 3)
            store.saveRating("serverB-userA", "video", 6)
            store.saveRating("a:b", "c", 2)
            store.saveRating("a", "b:c", 9)
            assertEquals(8, store.observeRating("serverA-userA", "video").first())
            assertEquals(3, store.observeRating("serverA-userB", "video").first())
            assertEquals(6, store.observeRating("serverB-userA", "video").first())
            assertNull(store.observeRating("serverA-userA", "other-video").first())
            assertEquals(2, store.observeRating("a:b", "c").first())
            assertEquals(9, store.observeRating("a", "b:c").first())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun invalidScoresDoNotOverwriteTheSavedRating() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = VideoRatingStore(ratingDataStore(
                scope = CoroutineScope(job + Dispatchers.IO),
                produceFile = { temporary.root.resolve("validation.preferences_pb").absolutePath.toPath() }
            ))
            store.saveRating("session", "video", 5)
            for (invalid in listOf(0, 11)) {
                try {
                    store.saveRating("session", "video", invalid)
                    fail("Scores outside 1..10 should be rejected")
                } catch (_: IllegalArgumentException) {
                    assertEquals(5, store.observeRating("session", "video").first())
                }
            }
        } finally {
            job.cancelAndJoin()
        }
    }
}

// Okio atomically replaces existing files on Windows, matching Android file semantics.
private fun ratingDataStore(scope: CoroutineScope, produceFile: () -> Path) =
    PreferenceDataStoreFactory.create(
        storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = produceFile),
        scope = scope
    )
