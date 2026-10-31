package com.jellycine.app.ui.screens.auth

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.shared.R

/** The launch screen deliberately does not compose any server content or restore its selection. */
@Composable
fun ServerLandingScreen(
    onAddServer: () -> Unit,
    onAddUser: (serverUrl: String, serverName: String?) -> Unit,
    onServerEntered: () -> Unit
) {
    val appContext = LocalContext.current.applicationContext
    val authRepository = remember(appContext) { AuthRepositoryProvider.getInstance(appContext) }
    val sessionSnapshot by authRepository.observeActiveSession().collectAsStateWithLifecycle(
        initialValue = authRepository.getActiveSessionSnapshot()
    )
    val switchViewModel: ServerSwitchViewModel = viewModel {
        ServerSwitchViewModel(appContext as Application)
    }
    val switchState by switchViewModel.uiState.collectAsStateWithLifecycle()
    val dialogs = rememberServerSwitchDialogsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TextButton(onClick = dialogs::openServers, enabled = !switchState.isBusy) {
            Icon(Icons.Default.Dns, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.auth_select_server),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = Color.White)
        }
    }

    ServerSwitchDialogsHost(
        state = dialogs,
        savedServers = sessionSnapshot.savedServers,
        // A persisted session is available for reuse, but nothing is selected on this screen.
        activeServerId = null,
        currentServerName = null,
        currentServerUrl = null,
        isSwitching = switchState.isBusy,
        onAddServer = onAddServer,
        onAddUser = onAddUser,
        onServerSelected = { server, dismiss ->
            switchViewModel.switchServer(
                serverId = server.id,
                activeServerId = null,
                onSwitchComplete = {
                    dismiss()
                    onServerEntered()
                },
                onSwitchFailed = {
                    dismiss()
                    onAddUser(server.serverUrl, server.serverName)
                }
            )
        },
        showRemoveAction = false
    )
}
