package com.jellycine.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.shared.R

/** 发现新版本时显示的提示框；由 Activity 根部统一挂载。 */
@Composable
fun AppUpdateDialog() {
    val context = LocalContext.current
    val controller = remember(context) { AppUpdateController.get(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    val current = state

    if (current is AppUpdateState.Available) {
        AlertDialog(
            onDismissRequest = controller::postpone,
            title = { Text(stringResource(R.string.update_dialog_title)) },
            text = {
                Text(stringResource(R.string.update_dialog_message, current.release.version.toString()))
            },
            confirmButton = {
                TextButton(onClick = {
                    openReleasePage(context, current.release.htmlUrl)
                    controller.postpone()
                }) {
                    Text(stringResource(R.string.update_dialog_download))
                }
            },
            dismissButton = {
                TextButton(onClick = controller::postpone) {
                    Text(stringResource(R.string.update_dialog_later))
                }
            }
        )
    }
}

private fun openReleasePage(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
