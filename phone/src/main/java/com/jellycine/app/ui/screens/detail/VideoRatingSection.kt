package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jellycine.data.preferences.VideoRatingStore
import com.jellycine.shared.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun VideoRatingSection(sessionId: String?, itemId: String?, store: VideoRatingStore) {
    key(sessionId, itemId) {
        val canRate = !sessionId.isNullOrBlank() && !itemId.isNullOrBlank()
        val ratingFlow = remember(sessionId, itemId, store) {
            if (canRate) store.observeRating(sessionId.orEmpty(), itemId.orEmpty()) else kotlinx.coroutines.flow.flowOf(null)
        }
        val rating by ratingFlow.collectAsState(initial = null)
        var showDialog by rememberSaveable { mutableStateOf(false) }
        var selectedScore by rememberSaveable { mutableStateOf<Int?>(null) }
        var saving by remember { mutableStateOf(false) }
        var saveError by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val accent = Color(0xFF9FC9FB)

        OutlinedButton(
            onClick = { selectedScore = rating; saveError = false; showDialog = true },
            enabled = canRate,
            modifier = Modifier.height(32.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Rounded.Star, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (rating == null) stringResource(R.string.video_rating_action)
                else stringResource(R.string.video_rating_value, rating!!), style = MaterialTheme.typography.labelSmall)
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { if (!saving) showDialog = false },
                title = { Text(stringResource(R.string.video_rating_title)) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(stringResource(R.string.video_rating_local), style = MaterialTheme.typography.bodySmall)
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in 0..1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (column in 1..5) {
                                        val score = row * 5 + column
                                        val selected = score == selectedScore
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (selected) Color(0xFF0A3454) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                                .selectable(selected = selected, enabled = !saving, role = Role.RadioButton,
                                                    onClick = { selectedScore = score; saveError = false })
                                        ) {
                                            Box(contentAlignment = Alignment.Center) { Text(score.toString()) }
                                        }
                                    }
                                }
                            }
                        }
                        if (saveError) {
                            Text(stringResource(R.string.video_rating_save_error), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = selectedScore != null && !saving,
                        onClick = {
                            val score = selectedScore ?: return@TextButton
                            saving = true
                            saveError = false
                            scope.launch {
                                try {
                                    store.saveRating(sessionId.orEmpty(), itemId.orEmpty(), score)
                                    showDialog = false
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    saveError = true
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.video_rating_save))
                    }
                },
                dismissButton = {
                    TextButton(enabled = !saving, onClick = { showDialog = false }) {
                        Text(stringResource(R.string.video_rating_cancel))
                    }
                }
            )
        }
    }
}
