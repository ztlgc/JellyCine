package com.jellycine.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.shared.R

/** 发现新版本时显示的提示框；由 Activity 根部统一挂载。 */
@Composable
fun AppUpdateDialog() {
    val context = LocalContext.current
    val controller = remember(context) { AppUpdateController.get(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    val current = state as? AppUpdateState.Available ?: return

    val release = current.release
    val apk = remember(release) { release.apkFor(Build.SUPPORTED_ABIS?.toList().orEmpty()) }
    // 没有匹配当前设备的安装包时，只能回退到 release 页面。
    val target = apk?.downloadUrl ?: release.htmlUrl
    val mirrors = if (apk != null) GithubMirrors.all else emptyList()

    AlertDialog(
        onDismissRequest = controller::postpone,
        title = { Text(stringResource(R.string.update_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.update_dialog_message, release.version.toString()))
                Spacer(Modifier.height(4.dp))
                DownloadOption(
                    title = stringResource(R.string.update_dialog_github),
                    subtitle = apk?.name ?: release.htmlUrl,
                    onClick = {
                        openUrl(context, target)
                        controller.postpone()
                    }
                )
                mirrors.forEach { mirror ->
                    DownloadOption(
                        title = stringResource(R.string.update_dialog_mirror, mirror.label),
                        subtitle = mirror.prefix,
                        onClick = {
                            openUrl(context, GithubMirrors.url(mirror, target))
                            controller.postpone()
                        }
                    )
                }
                if (mirrors.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.update_dialog_mirror_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = controller::postpone) {
                Text(stringResource(R.string.update_dialog_later))
            }
        }
    )
}

@Composable
private fun DownloadOption(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
