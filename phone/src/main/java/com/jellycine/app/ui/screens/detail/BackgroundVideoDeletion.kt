package com.jellycine.app.ui.screens.detail

import android.content.Context
import android.widget.Toast
import com.jellycine.app.ui.screens.dashboard.media.LibraryCatalogCache
import com.jellycine.shared.R
import com.jellycine.shared.playback.UserDataRefreshSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** The owner is the application, so navigating back does not cancel a deletion. */
internal class BackgroundDeletionQueue(private val scope: CoroutineScope) {
    private val pending = ConcurrentHashMap.newKeySet<String>()

    fun submit(key: String, operation: suspend () -> Result<Unit>, completed: suspend (Result<Unit>) -> Unit): Boolean {
        if (!pending.add(key)) return false
        scope.launch {
            try {
                completed(operation())
            } finally {
                pending.remove(key)
            }
        }
        return true
    }
}

internal object BackgroundVideoDeletion {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = BackgroundDeletionQueue(scope)

    fun submit(context: Context, serverId: String, itemId: String, name: String, operation: suspend () -> Result<Unit>) {
        val appContext = context.applicationContext
        queue.submit("$serverId:$itemId", operation) { result ->
            if (result.isSuccess) {
                UserDataRefreshSignals.notifyItemDeleted(serverId, itemId)
                runCatching { LibraryCatalogCache.getInstance(appContext.cacheDir).invalidateSession(serverId) }
                Toast.makeText(appContext, appContext.getString(R.string.video_detail_deleted, name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(appContext, appContext.getString(R.string.video_detail_delete_failed, name,
                    result.exceptionOrNull()?.message.orEmpty()), Toast.LENGTH_LONG).show()
            }
        }
    }
}
