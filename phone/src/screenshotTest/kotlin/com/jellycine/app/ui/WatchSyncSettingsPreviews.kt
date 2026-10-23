package com.jellycine.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.jellycine.app.sync.*
import com.jellycine.app.ui.screens.dashboard.settings.WatchSyncSettingsContent
import com.jellycine.shared.ui.theme.JellyCineTheme

@PreviewTest
@Preview(name="Sync settings",widthDp=412,heightDp=560,locale="zh-rCN")
@Preview(name="Sync small large font",widthDp=320,heightDp=660,fontScale=1.3f,locale="zh-rCN")
@Composable
fun WatchSyncSettingsPreview() {
    JellyCineTheme {
        Surface {
            Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("设置",style=MaterialTheme.typography.titleLarge)
                OutlinedCard {
                    Text("屏幕时间",Modifier.padding(16.dp),style=MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    WatchSyncSettingsContent(WatchSyncUiState(configured=true,enabled=true,
                        email="watch@example.com",lastSuccessMs=1792597524000),{},{},{})
                }
                OutlinedCard {
                    WatchSyncSettingsContent(WatchSyncUiState(configured=true,enabled=true,
                        email="watch@example.com",error=WatchSyncError.NETWORK),{},{},{})
                }
                OutlinedCard {
                    WatchSyncSettingsContent(WatchSyncUiState(),{},{},{})
                }
            }
        }
    }
}
