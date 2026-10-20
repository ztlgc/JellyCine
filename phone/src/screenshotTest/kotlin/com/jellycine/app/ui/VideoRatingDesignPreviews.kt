package com.jellycine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.jellycine.app.ui.screens.detail.VideoRatingDialogContent

@Composable
private fun RatingPreview(expanded: Boolean = false, failed: Boolean = false,
    current: Float? = 8f, selected: Int? = if (expanded) 34 else 86, loading: Boolean = false) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0F15)).padding(16.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 352.dp)) {
                VideoRatingDialogContent(
                    currentRating = current,
                    selectedScoreTenths = selected,
                    lowScoresExpanded = expanded,
                    enabled = !failed && !loading,
                    loading = loading,
                    saving = false,
                    errorMessage = if (failed) "无法读取服务器当前评分。" else null,
                    onScoreSelected = {}, onLowScoresToggle = {}, onSave = {}, onCancel = {},
                    onRetry = if (failed) ({}) else null
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "Rating high scores", widthDp = 360, heightDp = 360, locale = "zh-rCN", showBackground = true)
@Preview(name = "Rating small", widthDp = 320, heightDp = 360, locale = "zh-rCN", fontScale = 1.15f, showBackground = true)
@Composable
fun VideoRatingDesignPreview() = RatingPreview()

@PreviewTest
@Preview(name = "Rating low scores", widthDp = 360, heightDp = 420, locale = "zh-rCN", showBackground = true)
@Composable
fun VideoRatingLowScorePreview() = RatingPreview(expanded = true)

@PreviewTest
@Preview(name = "Rating load failure", widthDp = 320, heightDp = 440, locale = "zh-rCN", fontScale = 1.15f, showBackground = true)
@Composable
fun VideoRatingFailurePreview() = RatingPreview(failed = true)

@PreviewTest
@Preview(name = "Rating boundary", widthDp = 320, heightDp = 420, locale = "zh-rCN", fontScale = 1.3f, showBackground = true)
@Composable
fun VideoRatingBoundaryPreview() = RatingPreview(expanded = true, current = 5.9f, selected = 59)

@PreviewTest
@Preview(name = "Rating unrated", widthDp = 320, heightDp = 360, locale = "zh-rCN", showBackground = true)
@Composable
fun VideoRatingUnratedPreview() = RatingPreview(current = null, selected = null)

@PreviewTest
@Preview(name = "Rating loading", widthDp = 320, heightDp = 360, locale = "zh-rCN", showBackground = true)
@Composable
fun VideoRatingLoadingPreview() = RatingPreview(loading = true)

@PreviewTest
@Preview(name = "Rating English", widthDp = 320, heightDp = 400, locale = "en", fontScale = 1.3f, showBackground = true)
@Composable
fun VideoRatingEnglishPreview() = RatingPreview(expanded = true)
