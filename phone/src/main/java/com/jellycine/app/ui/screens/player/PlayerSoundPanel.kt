package com.jellycine.app.ui.screens.player

import com.jellycine.app.locale.localizedPlaybackLabel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.jellycine.player.core.AudioTrackInfo
import com.jellycine.player.core.PlaybackOptions
import com.jellycine.shared.R
import java.util.Locale

/** A modal sound drawer keeps playback visible while blocking gestures behind the panel. */
@Composable
internal fun PlayerSoundPanel(
    audioGainPercent: Int,
    audioGainAvailable: Boolean,
    normalizationEnabled: Boolean,
    audioTracks: List<AudioTrackInfo>,
    currentAudioTrack: AudioTrackInfo?,
    initiallyExpandTracks: Boolean,
    onGainChange: (Int) -> Unit,
    onNormalizationChange: (Boolean) -> Unit,
    onTrackSelected: ((String) -> Unit)?,
    onOpenTrackDialog: () -> Unit,
    onDismiss: () -> Unit
) {
    var expandTracks by remember { mutableStateOf(initiallyExpandTracks) }
    var allPresets by remember { mutableStateOf(false) }
    val panel = Color(0xFF151C26)
    val border = Color(0xFF2A3544)
    val soundTitle = stringResource(R.string.player_sound_title)
    SoundPanelWindow(onDismiss = onDismiss) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 360.dp
            val panelWidth = if (maxWidth > maxHeight) (maxWidth * 0.40f).coerceIn(304.dp, 400.dp)
                else (maxWidth - 20.dp).coerceAtMost(380.dp)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).clickable(onClick = onDismiss).clearAndSetSemantics {})
            Surface(Modifier.align(Alignment.CenterEnd).width(panelWidth).fillMaxHeight().semantics { paneTitle = soundTitle }, color = panel,
                border = BorderStroke(1.dp, border)) {
                Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = if (compact) 12.dp else 16.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 44.dp else 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.player_sound_title), Modifier.weight(1f), color = Color.White,
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.player_control_close), tint = Color.White) }
                    }
                    Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                    Surface(color = Color(0xFF19222D), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, border)) {
                        Column {
                            Row(Modifier.fillMaxWidth().clickable {
                                if (onTrackSelected != null && audioTracks.isNotEmpty()) expandTracks = !expandTracks else onOpenTrackDialog()
                            }.padding(horizontal = 12.dp).heightIn(min = if (compact) 44.dp else 52.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.player_audio_short), color = Color.White, fontSize = if (compact) 13.sp else 15.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(LocalContext.current.localizedPlaybackLabel(currentAudioTrack?.label.orEmpty()).ifBlank { stringResource(R.string.player_audio_default) },
                                    Modifier.weight(1f), color = Color(0xFFD7DFE9), fontSize = if (compact) 12.sp else 13.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                Spacer(Modifier.width(8.dp))
                                Icon(if (expandTracks) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    null, tint = PlayerAccent, modifier = Modifier.size(18.dp))
                            }
                            if (expandTracks && onTrackSelected != null) audioTracks.forEach { track ->
                                val selected = track.id == currentAudioTrack?.id
                                Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton,
                                    onClick = { onTrackSelected(track.id) }).padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(LocalContext.current.localizedPlaybackLabel(track.label), Modifier.weight(1f), color = if (selected) PlayerAccent else Color.White,
                                        fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (selected) Icon(Icons.Outlined.Check, null, tint = PlayerAccent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                    Surface(color = Color(0xFF19222D), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, border)) {
                        Column(Modifier.padding(if (compact) 10.dp else 12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.player_gain_title), Modifier.weight(1f), color = Color.White,
                                    fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (audioGainPercent == 0) stringResource(R.string.player_gain_off_short) else
                                    String.format(Locale.getDefault(), "+%d%% · +%.1f dB", audioGainPercent,
                                        PlaybackOptions.gainDecibels(audioGainPercent)),
                                    color = PlayerAccent, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (!audioGainAvailable) Text(stringResource(R.string.player_gain_unavailable),
                                color = Color(0xFFADB9C9), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                            val presets: List<Int?> = if (allPresets) PlaybackOptions.gainPercentages.map { it }
                                else listOf(0, 30, 80, 150, 200, null)
                            presets.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth().padding(bottom = if (compact) 6.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { percent ->
                                        val selected = if (percent == null) audioGainPercent > 200 else percent == audioGainPercent
                                        val enabled = percent == null || percent == 0 || audioGainAvailable
                                        Surface(shape = RoundedCornerShape(8.dp),
                                            color = if (selected) PlayerAccent else Color(0xFF1D2834),
                                            border = BorderStroke(1.dp, if (selected) PlayerAccent else Color(0xFF3A4859)),
                                            modifier = Modifier.weight(1f).heightIn(min = if (compact) 36.dp else 40.dp).selectable(
                                                selected = selected, enabled = enabled, role = Role.RadioButton,
                                                onClick = { if (percent == null) allPresets = true else onGainChange(percent) })) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp, vertical = if (compact) 6.dp else 8.dp)) {
                                                Text(when (percent) {
                                                    null -> stringResource(R.string.player_gain_more)
                                                    0 -> stringResource(R.string.player_gain_off_short)
                                                    else -> "+$percent%"
                                                }, color = if (selected) Color(0xFF08243A) else Color.White.copy(alpha = if (enabled) 1f else 0.35f),
                                                    fontSize = if (compact) 12.sp else 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                            }
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            HorizontalDivider(Modifier.padding(top = 6.dp), color = border)
                            Row(Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.player_audio_normalization), Modifier.weight(1f), color = Color.White, fontSize = if (compact) 13.sp else 15.sp)
                                Switch(checked = normalizationEnabled, onCheckedChange = onNormalizationChange,
                                    enabled = audioGainAvailable || normalizationEnabled,
                                    colors = SwitchDefaults.colors(checkedTrackColor = PlayerAccent, checkedThumbColor = Color(0xFF08243A),
                                        uncheckedTrackColor = Color(0xFF3D4A5B), uncheckedThumbColor = Color.White))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Keep the same panel content visible in layoutlib, where native window dimming is simulated. */
@Composable
private fun SoundPanelWindow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (LocalInspectionMode.current) {
        content()
    } else {
        Dialog(onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val view = LocalView.current
            SideEffect {
                val window = generateSequence(view as android.view.View?) { it.parent as? android.view.View }
                    .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window?.setDimAmount(0f)
            }
            content()
        }
    }
}
