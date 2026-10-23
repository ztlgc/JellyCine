package com.jellycine.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.jellycine.app.ui.components.common.AmoledSelectionDialog
import com.jellycine.app.ui.components.common.SelectionOption
import com.jellycine.app.ui.screens.dashboard.settings.SubtitleSettingsScreen
import com.jellycine.app.ui.screens.detail.DownloadQualityPicker
import com.jellycine.app.ui.screens.player.*
import com.jellycine.player.core.SubtitleTrackInfo
import com.jellycine.shared.R
import com.jellycine.shared.ui.theme.JellyCineTheme

@PreviewTest
@Preview(name = "Chinese subtitle settings", widthDp = 320, heightDp = 740, fontScale = 1.3f, locale = "zh-rCN")
@Composable
fun ChineseSubtitleSettingsPreview() {
    JellyCineTheme { SubtitleSettingsScreen() }
}

@PreviewTest
@Preview(name = "Chinese rendering options", widthDp = 320, heightDp = 640, fontScale = 1.3f, locale = "zh-rCN")
@Composable
fun ChineseRenderingOptionsPreview() {
    JellyCineTheme {
        AmoledSelectionDialog(
            title = stringResource(R.string.ui_tone_mapping),
            accentColor = androidx.compose.ui.graphics.Color(0xFF9FC9FB),
            options = listOf(
                SelectionOption("auto", stringResource(R.string.settings_auto), stringResource(R.string.ui_dynamically_picks_best_algorithm_per_scene), isDefault = true),
                SelectionOption("bt.2390", "BT.2390", stringResource(R.string.ui_broadcast_standard_natural_highlights)),
                SelectionOption("spline", stringResource(R.string.ui_spline), stringResource(R.string.ui_smooth_curve_preserves_mid_tones)),
                SelectionOption("hable", stringResource(R.string.ui_filmic), stringResource(R.string.ui_cinematic_roll_off_softer_highlights))
            ),
            selectedValue = "auto", onOptionSelected = {}, onDismiss = {}
        )
    }
}

@PreviewTest
@Preview(name = "Chinese download quality", widthDp = 320, heightDp = 640, fontScale = 1.3f, locale = "zh-rCN")
@Composable
fun ChineseDownloadQualityPreview() {
    JellyCineTheme {
        DownloadQualityPicker(7_200_000_000L, 1080, 12_000_000, 1_000_000_000L, emptyList(), {}, {})
    }
}

@PreviewTest
@Preview(name = "Chinese subtitle picker", widthDp = 320, heightDp = 640, fontScale = 1.3f, locale = "zh-rCN")
@Composable
fun ChineseSubtitlePickerPreview() {
    val off = SubtitleTrackInfo(id = "off", label = "Off", language = null, streamIndex = -1)
    val english = SubtitleTrackInfo(id = "subtitle:1", label = "Subtitle 1", language = "eng", isForced = true, isDefault = true, streamIndex = 1)
    JellyCineTheme { SubtitleTrackSelectionDialog(true, listOf(off, english), off, {}, {}) }
}

@PreviewTest
@Preview(name = "Chinese media information", widthDp = 740, heightDp = 360, fontScale = 1.3f, locale = "zh-rCN")
@Composable
fun ChineseMediaInformationPreview() {
    JellyCineTheme {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) { MediaInfoPanel(
            MediaMetadataInfo(
                videoFormat = VideoFormatInfo("HEVC", "1920x1080", "video/hevc"),
                audioFormat = AudioFormatInfo("AAC", "Stereo", "192 kbps", "48000 Hz", "eng", true),
                hardwareAcceleration = HardwareAccelerationInfo(true, decoderType = "Hardware", asyncModeEnabled = true),
                streamContainer = "MP4", playMethod = "Direct Play"
            ), {}, compact = false, panelWidth = 300.dp
        ) }
    }
}
