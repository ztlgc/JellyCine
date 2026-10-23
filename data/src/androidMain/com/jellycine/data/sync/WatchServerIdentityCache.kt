package com.jellycine.data.sync

import android.content.Context
import com.jellycine.data.network.canonicalServerUrlKey

/** Stores only an ID obtained from a successful media-server response, never a guessed host identity. */
internal object WatchServerIdentityCache {
    fun read(context:Context,url:String):String? = context.getSharedPreferences("watch_server_identities",Context.MODE_PRIVATE)
        .getString(canonicalServerUrlKey(url),null)?.takeIf {it.isNotBlank()}

    fun remember(context:Context,url:String,id:String?) {
        if (id.isNullOrBlank()) return
        context.getSharedPreferences("watch_server_identities",Context.MODE_PRIVATE).edit()
            .putString(canonicalServerUrlKey(url),id).apply()
    }
}
