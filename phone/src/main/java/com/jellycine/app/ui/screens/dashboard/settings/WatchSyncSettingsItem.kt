package com.jellycine.app.ui.screens.dashboard.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jellycine.app.sync.*
import com.jellycine.app.ui.components.common.PhoneDialog
import com.jellycine.shared.R
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WatchSyncSettingsItem() {
    val context = LocalContext.current
    val controller = remember { WatchSyncController.get(context) }
    val state by controller.state.collectAsState()
    var showAccount by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    WatchSyncSettingsContent(state,
        onToggle = { enabled ->
            if (enabled && (state.email==null || state.error==WatchSyncError.AUTH)) showAccount=true
            else controller.setEnabled(enabled)
        }, onSync = {if (state.error==WatchSyncError.AUTH) showAccount=true else controller.requestSync()},
        onAccount = { showAccount=true })
    if (showAccount) {
        var email by remember { mutableStateOf(state.email.orEmpty()) }
        var password by remember { mutableStateOf("") }
        PhoneDialog(onDismiss = { showAccount=false }, dismissOnRequest = !state.signingIn) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.watch_sync_account),style=MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.watch_sync_account_hint),style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value=email,onValueChange={email=it},singleLine=true,
                        enabled=!state.signingIn,label={Text(stringResource(R.string.watch_sync_email))},
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=password,onValueChange={password=it},singleLine=true,
                        enabled=!state.signingIn,label={Text(stringResource(R.string.watch_sync_password))},
                        visualTransformation=PasswordVisualTransformation(),
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
                    state.error?.let { Text(syncErrorText(it),color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall) }
                    if (state.verificationSent) Text(stringResource(R.string.watch_sync_verification_sent),
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
                    if (state.offline) Text(stringResource(R.string.watch_sync_wait_network),
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        TextButton(onClick={showAccount=false},enabled=!state.signingIn) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        Button(enabled=email.isNotBlank() && password.isNotBlank() && !state.signingIn && !state.offline,
                            onClick={ scope.launch {
                                val success=withContext(Dispatchers.IO) { controller.signIn(email,password) }
                                if (success) { password=""; showAccount=false }
                            } }) {
                            if (state.signingIn) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
                            else Text(stringResource(R.string.watch_sync_bind))
                        }
                    }
                    TextButton(onClick={scope.launch {
                        val success=withContext(Dispatchers.IO) {controller.signUp(email,password)}
                        if(success) {password="";showAccount=false}
                    }},enabled=email.isNotBlank() && password.length>=8 && !state.signingIn && !state.offline,
                        modifier=Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.watch_sync_create_account))
                    }
                    if (state.email!=null) TextButton(onClick={scope.launch {
                        withContext(Dispatchers.IO) { controller.disconnect() }
                        password=""; showAccount=false
                    }},enabled=!state.signingIn,modifier=Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.watch_sync_disconnect))
                    }
                }
            }
        }
    }
}

@Composable
internal fun WatchSyncSettingsContent(state: WatchSyncUiState, onToggle:(Boolean)->Unit,
    onSync:()->Unit,onAccount:()->Unit) {
    val status=when {
        !state.configured -> stringResource(R.string.watch_sync_unconfigured)
        !state.enabled -> stringResource(R.string.watch_sync_disabled)
        state.email==null -> stringResource(R.string.watch_sync_needs_account)
        state.offline -> stringResource(R.string.watch_sync_wait_network)
        state.syncing -> stringResource(R.string.watch_sync_running)
        state.error!=null -> syncErrorText(state.error)
        state.lastSuccessMs>0 -> stringResource(R.string.watch_sync_last_success,
            SimpleDateFormat("MM-dd HH:mm",LocalConfiguration.current.locales[0]).format(Date(state.lastSuccessMs)))
        else -> stringResource(R.string.watch_sync_waiting)
    }
    Column {
        SettingsItem(icon=Icons.Rounded.CloudSync,title=stringResource(R.string.watch_sync_title),subtitle=status,
            accentColor=Color(0xFF22D3EE),trailing={
                Switch(checked=state.enabled,onCheckedChange=onToggle,enabled=state.configured && !state.signingIn,
                    colors=accentSwitchColors(Color(0xFF22D3EE)))
            })
        if (state.configured && state.email!=null) {
            Row(Modifier.fillMaxWidth().padding(start=72.dp,end=8.dp,bottom=4.dp),
                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(state.email,modifier=Modifier.weight(1f).clickable(onClick=onAccount).heightIn(min=48.dp).padding(vertical=12.dp),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                TextButton(onClick=onSync,enabled=state.enabled && !state.offline && !state.syncing) {
                    Text(stringResource(R.string.watch_sync_now))
                }
            }
        } else if (state.configured && state.enabled) {
            TextButton(onClick=onAccount,modifier=Modifier.padding(start=60.dp)) {
                Text(stringResource(R.string.watch_sync_account))
            }
        }
    }
}

@Composable
private fun syncErrorText(error: WatchSyncError): String = stringResource(when(error) {
    WatchSyncError.AUTH -> R.string.watch_sync_error_auth
    WatchSyncError.NETWORK -> R.string.watch_sync_error_network
    WatchSyncError.SERVER -> R.string.watch_sync_error_server
    WatchSyncError.IDENTITY -> R.string.watch_sync_error_identity
    WatchSyncError.CREDENTIALS -> R.string.watch_sync_error_credentials
})
