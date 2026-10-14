package com.jellycine.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.jellycine.data.datastore.DataStoreProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Personal integer scores, scoped to the saved server/account session and video. */
class VideoRatingStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(DataStoreProvider.getDataStore(context.applicationContext))

    fun observeRating(sessionId: String, itemId: String): Flow<Int?> {
        val key = ratingKey(sessionId, itemId)
        return dataStore.data.map { preferences -> preferences[key]?.takeIf { it in 1..10 } }
    }

    suspend fun saveRating(sessionId: String, itemId: String, score: Int) {
        require(score in 1..10) { "Video scores must be between 1 and 10" }
        val key = ratingKey(sessionId, itemId)
        dataStore.edit { it[key] = score }
    }

    private fun ratingKey(sessionId: String, itemId: String): Preferences.Key<Int> {
        require(sessionId.isNotBlank() && itemId.isNotBlank())
        // Length prefixes avoid collisions even if either identifier contains a separator.
        return intPreferencesKey("video_rating:${sessionId.length}:$sessionId:${itemId.length}:$itemId")
    }
}
