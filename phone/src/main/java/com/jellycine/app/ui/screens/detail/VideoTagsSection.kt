package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.jellycine.app.ui.components.common.PhoneDialog
import com.jellycine.app.ui.screens.dashboard.media.LibraryCatalogCache
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.VideoRatingPermissionException
import com.jellycine.data.repository.VideoRatingSessionException
import com.jellycine.data.repository.VideoTagsNotConfirmedException
import com.jellycine.data.repository.normalizeVideoTags
import com.jellycine.shared.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val TagAccent = Color(0xFF9FC9FB)
private val TagSecondaryText = Color(0xFFADB9C9)
private val TagBorder = Color(0xFF293340)
private val TagSurface = Color(0xFF101720)
private const val MAX_TAG_LENGTH = 64

/** Shows the item's tags beside the rating and lets the account edit the server-side list. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun VideoTagsSection(sessionId: String?, item: BaseItemDto, repository: MediaRepository) {
    key(sessionId, item.id) {
        val itemId = item.id
        val canEdit = !sessionId.isNullOrBlank() && !itemId.isNullOrBlank()
        var serverTags by remember { mutableStateOf(normalizeVideoTags(item.tags.orEmpty())) }
        var permissionDenied by remember { mutableStateOf(item.canEditItems == false) }
        var showDialog by rememberSaveable { mutableStateOf(false) }
        var draftTags by remember { mutableStateOf<List<String>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var loadFailed by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        var saveError by remember { mutableStateOf<Int?>(null) }
        var refreshVersion by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        // The detail payload normally carries Tags. When it does not (older payloads, other entry
        // points), read the full document once so the summary is not shown as "no tags" by mistake.
        // Deliberately not keyed on showDialog: closing the editor must not restore stale parent data.
        LaunchedEffect(itemId, item.tags) {
            val parentTags = item.tags
            if (parentTags != null) {
                serverTags = normalizeVideoTags(parentTags)
                return@LaunchedEffect
            }
            if (!canEdit) return@LaunchedEffect
            repository.loadVideoRating(itemId.orEmpty(), sessionId.orEmpty()).onSuccess { loaded ->
                serverTags = normalizeVideoTags(loaded.tags.orEmpty())
                permissionDenied = loaded.canEditItems == false
            }
        }

        // Reads the full server document so the editor starts from the confirmed tag list and permission.
        LaunchedEffect(showDialog, refreshVersion) {
            if (showDialog && canEdit && !saving) {
                loading = true
                loadFailed = false
                try {
                    repository.loadVideoRating(itemId.orEmpty(), sessionId.orEmpty()).fold(
                        onSuccess = { loaded ->
                            serverTags = normalizeVideoTags(loaded.tags.orEmpty())
                            draftTags = serverTags
                            permissionDenied = loaded.canEditItems == false
                        },
                        onFailure = { loadFailed = true }
                    )
                } finally {
                    loading = false
                }
            }
        }

        val summary = serverTags.take(2).joinToString(" · ") +
            if (serverTags.size > 2) " +${serverTags.size - 2}" else ""
        val tagsDescription = stringResource(R.string.video_tags_title)
        Button(
            onClick = {
                draftTags = serverTags
                saveError = null
                loading = true
                loadFailed = false
                showDialog = true
            },
            enabled = canEdit,
            modifier = Modifier.height(32.dp).widthIn(max = 240.dp).semantics { contentDescription = tagsDescription },
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C2735), contentColor = Color(0xFFCFE3F7)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(Icons.AutoMirrored.Outlined.Label, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (serverTags.isEmpty()) stringResource(R.string.video_tags_add) else summary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(13.dp))
        }

        if (showDialog) {
            PhoneDialog(
                onDismiss = { showDialog = false },
                dismissOnRequest = !saving,
                maxWidth = 352.dp,
                scrimColor = Color.Black.copy(alpha = 0.6f)
            ) {
                VideoTagsDialogContent(
                    tags = draftTags,
                    canSave = draftTags != serverTags,
                    loading = loading,
                    saving = saving,
                    enabled = canEdit && !loading && !loadFailed && !permissionDenied,
                    errorMessage = when {
                        saveError != null -> stringResource(saveError!!)
                        loadFailed -> stringResource(R.string.video_tags_load_error)
                        permissionDenied -> stringResource(R.string.video_rating_permission_error)
                        else -> null
                    },
                    onAdd = { name -> draftTags = normalizeVideoTags(draftTags + name); saveError = null },
                    onRemove = { tag -> draftTags = draftTags - tag; saveError = null },
                    onSave = {
                        val tagsToSave = draftTags
                        saving = true
                        saveError = null
                        scope.launch {
                            try {
                                val confirmed = repository.saveVideoTags(itemId.orEmpty(), tagsToSave, sessionId.orEmpty()).getOrThrow()
                                serverTags = normalizeVideoTags(confirmed.tags.orEmpty())
                                draftTags = serverTags
                                try {
                                    LibraryCatalogCache.getInstance(context.cacheDir).updateTags(
                                        sessionId.orEmpty(), itemId.orEmpty(), serverTags)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    /* The server result is already confirmed. */
                                }
                                showDialog = false
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                saveError = when (error) {
                                    is VideoRatingPermissionException -> R.string.video_rating_permission_error
                                    is VideoTagsNotConfirmedException -> R.string.video_tags_unconfirmed_error
                                    is VideoRatingSessionException -> R.string.video_rating_session_error
                                    else -> R.string.video_tags_save_error
                                }
                            } finally {
                                saving = false
                            }
                        }
                    },
                    onCancel = { showDialog = false },
                    onRetry = if (loadFailed || saveError != null) ({
                        saveError = null
                        refreshVersion++
                    }) else null
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VideoTagsDialogContent(
    tags: List<String>,
    canSave: Boolean,
    loading: Boolean,
    saving: Boolean,
    enabled: Boolean,
    errorMessage: String?,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)?
) {
    var input by remember { mutableStateOf("") }
    val inputEnabled = enabled && !saving && !loading
    fun submitInput() {
        val name = input.trim()
        if (name.isNotEmpty() && tags.none { it.equals(name, ignoreCase = true) }) onAdd(name)
        input = ""
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TagSurface,
        contentColor = Color(0xFFF1F4F8),
        border = BorderStroke(1.dp, TagBorder)
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.Label, null, tint = TagAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.video_tags_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            when {
                loading -> Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = TagAccent, strokeWidth = 2.dp)
                }
                tags.isEmpty() -> Text(stringResource(R.string.video_tags_empty), color = TagSecondaryText, fontSize = 13.sp)
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { if (inputEnabled) onRemove(tag) },
                            enabled = inputEnabled,
                            label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = {
                                Icon(Icons.Rounded.Close, stringResource(R.string.video_tags_remove, tag),
                                    modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(MAX_TAG_LENGTH) },
                    modifier = Modifier.weight(1f),
                    enabled = inputEnabled,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.video_tags_input_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitInput() })
                )
                TextButton(onClick = { submitInput() }, enabled = inputEnabled && input.isNotBlank()) {
                    Text(stringResource(R.string.video_tags_add_action))
                }
            }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp))
                if (onRetry != null) {
                    TextButton(onClick = onRetry, enabled = !loading && !saving,
                        colors = ButtonDefaults.textButtonColors(contentColor = TagAccent)) {
                        Text(stringResource(R.string.video_rating_retry))
                    }
                }
            }
            HorizontalDivider(color = TagBorder, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, enabled = !saving,
                    colors = ButtonDefaults.textButtonColors(contentColor = TagSecondaryText)) {
                    Text(stringResource(R.string.video_rating_cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = enabled && canSave && !saving,
                    modifier = Modifier.widthIn(min = 106.dp).heightIn(min = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TagAccent,
                        contentColor = Color(0xFF08243A), disabledContainerColor = Color(0xFF243245),
                        disabledContentColor = Color(0xFF95A5BA))
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = TagAccent, strokeWidth = 2.dp)
                    else Text(stringResource(R.string.video_rating_save), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
