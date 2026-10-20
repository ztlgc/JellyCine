package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.VideoRatingPermissionException
import com.jellycine.data.repository.VideoRatingNotConfirmedException
import com.jellycine.data.repository.VideoRatingSessionException
import com.jellycine.app.ui.screens.dashboard.media.LibraryCatalogCache
import com.jellycine.app.ui.components.common.PhoneDialog
import com.jellycine.shared.playback.UserDataRefreshSignals
import com.jellycine.shared.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun VideoRatingSection(sessionId: String?, item: BaseItemDto, repository: MediaRepository) {
    key(sessionId, item.id) {
        val itemId = item.id
        val canRate = !sessionId.isNullOrBlank() && !itemId.isNullOrBlank()
        var serverItem by remember { mutableStateOf(item) }
        val rating = serverItem.communityRating?.takeIf { it.isFinite() && it in 0f..10f }
        var showDialog by rememberSaveable { mutableStateOf(false) }
        var selectedScoreTenths by rememberSaveable { mutableStateOf<Int?>(null) }
        var lowScoresExpanded by rememberSaveable { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(true) }
        var loadFailed by remember { mutableStateOf(false) }
        var saveError by remember { mutableStateOf<Int?>(null) }
        var refreshVersion by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        suspend fun publish(confirmed: BaseItemDto) {
            serverItem = confirmed
            UserDataRefreshSignals.notifyRatingChanged(sessionId.orEmpty(), itemId.orEmpty(), confirmed.communityRating)
            try {
                LibraryCatalogCache.getInstance(context.cacheDir).updateRating(
                    sessionId.orEmpty(), itemId.orEmpty(), confirmed.communityRating)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The server result is already confirmed. */ }
        }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshVersion++
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(showDialog, refreshVersion) {
            if (canRate && !saving) {
                loading = true
                loadFailed = false
                try {
                    repository.loadVideoRating(itemId.orEmpty(), sessionId.orEmpty()).fold(
                        onSuccess = { confirmed ->
                            publish(confirmed)
                            if (showDialog) {
                                selectedScoreTenths = VideoRatingScale.fromServer(confirmed.communityRating)
                                lowScoresExpanded = selectedScoreTenths?.let { it in VideoRatingScale.lowScores } == true
                            }
                        },
                        onFailure = { loadFailed = true }
                    )
                } finally { loading = false }
            }
        }
        VideoRatingButton(rating = rating, enabled = canRate,
            onClick = {
                selectedScoreTenths = VideoRatingScale.fromServer(rating)
                lowScoresExpanded = selectedScoreTenths?.let { it in VideoRatingScale.lowScores } == true
                saveError = null
                loading = true
                loadFailed = false
                showDialog = true
            })

        if (showDialog) {
            PhoneDialog(
                onDismiss = { showDialog = false },
                dismissOnRequest = !saving,
                maxWidth = 352.dp,
                scrimColor = Color.Black.copy(alpha = 0.6f)
            ) {
                VideoRatingDialogContent(
                    currentRating = rating,
                    selectedScoreTenths = selectedScoreTenths,
                    lowScoresExpanded = lowScoresExpanded,
                    enabled = canRate && !saving && !loading && !loadFailed && serverItem.canEditItems != false,
                    loading = loading,
                    saving = saving,
                    errorMessage = when {
                        saveError != null -> stringResource(saveError!!)
                        loadFailed -> stringResource(R.string.video_rating_load_error)
                        serverItem.canEditItems == false -> stringResource(R.string.video_rating_permission_error)
                        else -> null
                    },
                    onScoreSelected = { selectedScoreTenths = it; saveError = null },
                    onLowScoresToggle = { lowScoresExpanded = !lowScoresExpanded },
                    onRetry = if (loadFailed) ({ refreshVersion++ }) else null,
                    onSave = saveRating@ {
                        val score = selectedScoreTenths?.let(VideoRatingScale::toServer) ?: return@saveRating
                        saving = true
                        saveError = null
                        scope.launch {
                            try {
                                val confirmed = repository.saveVideoRating(itemId.orEmpty(), score, sessionId.orEmpty()).getOrThrow()
                                publish(confirmed)
                                showDialog = false
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                saveError = when (error) {
                                    is VideoRatingPermissionException -> R.string.video_rating_permission_error
                                    is VideoRatingNotConfirmedException -> R.string.video_rating_unconfirmed_error
                                    is VideoRatingSessionException -> R.string.video_rating_session_error
                                    else -> R.string.video_rating_save_error
                                }
                            } finally {
                                saving = false
                            }
                        }
                    },
                    onCancel = { showDialog = false }
                )
            }
        }
    }
}

@Composable
internal fun VideoRatingButton(rating: Float?, enabled: Boolean = true, onClick: () -> Unit) {
    val ratingDescription = stringResource(R.string.video_rating_title)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(32.dp).semantics { contentDescription = ratingDescription },
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9FC9FB), contentColor = Color(0xFF08243A)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(Icons.Rounded.Star, null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (rating == null) stringResource(R.string.video_rating_action)
            else String.format(java.util.Locale.getDefault(), "%.1f", rating), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(13.dp))
    }
}
