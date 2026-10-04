package com.example.foz.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.memory.MemoryFact
import com.example.foz.model.ModelDownloader
import com.example.foz.model.ModelSpec
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.settings.SettingsItem
import com.example.foz.ui.settings.SettingsActionRow
import com.example.foz.ui.settings.SettingsHeaderRow
import com.example.foz.ui.settings.SettingsToggleRow
import com.example.foz.ui.settings.permissionStatusText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AssistantSettingsScreen(
    state: LauncherUiState,
    onClose: () -> Unit,
    onAssistantEnabledChanged: (Boolean) -> Unit,
    onAssistantSpeakChanged: (Boolean) -> Unit,
    onAssistantVolumeButtonChanged: (Boolean) -> Unit,
    onAssistantMicButtonChanged: (Boolean) -> Unit = {},
    onAssistantBubbleChanged: (Boolean) -> Unit = {},
    onAssistantKeepLoadedChanged: (Boolean) -> Unit = {},
    onAssistantMemoryChanged: (Boolean) -> Unit = {},
    onLoadMemoryFacts: () -> Map<String, List<MemoryFact>> = { emptyMap() },
    onDeleteMemoryFact: (String, String) -> Unit = { _, _ -> },
    onClearMemorySubject: (String) -> Unit = {},
    onForgetAllMemory: () -> Unit = {},
    onFreeMemory: () -> Unit = {},
    onAssistantModelPick: () -> Unit,
    onAssistantModelDelete: () -> Unit,
    onAssistantOpenDownloadPage: () -> Unit,
    downloadState: ModelDownloader.State = ModelDownloader.State.Idle,
    hfToken: String = "",
    onHfTokenChanged: (String) -> Unit = {},
    onDownloadModel: (ModelSpec) -> Unit = {},
    onSelectDownloaded: (ModelSpec) -> Unit = {},
    onCancelDownload: () -> Unit = {},
    downloadedIds: Set<String> = emptySet(),
    onMicPermissionClick: () -> Unit,
    onCalendarReadClick: () -> Unit,
    onCalendarWriteClick: () -> Unit,
    onContactsClick: () -> Unit,
    assistantFreeRamBytes: Long = 0L,
    assistantTotalRamBytes: Long = 0L
) {
    var showModelSheet by remember { mutableStateOf(false) }
    var showMemoryViewer by remember { mutableStateOf(false) }

    val items = listOfNotNull(
        SettingsItem.Toggle(
            stringResource(R.string.settings_assistant_enable),
            state.assistantEnabled,
            onAssistantEnabledChanged
        ),
        SettingsItem.Action(
            title = stringResource(R.string.settings_assistant_model),
            description = assistantModelStatusText(state.assistantModelStatus),
            onClick = { showModelSheet = true }
        ),
        SettingsItem.Toggle(
            stringResource(R.string.settings_assistant_memory),
            state.assistantMemoryEnabled,
            onAssistantMemoryChanged
        ).takeIf { state.assistantEnabled },
        SettingsItem.Action(
            title = stringResource(R.string.settings_assistant_memory_viewer),
            onClick = { showMemoryViewer = true }
        ).takeIf { state.assistantEnabled },
        SettingsItem.Action(
            title = stringResource(R.string.settings_permission_microphone),
            description = permissionStatusText(state.micPermissionGranted),
            onClick = onMicPermissionClick
        ),
        SettingsItem.Action(
            title = stringResource(R.string.settings_permission_calendar_read),
            description = permissionStatusText(state.calendarReadGranted),
            onClick = onCalendarReadClick
        ),
        SettingsItem.Action(
            title = stringResource(R.string.settings_permission_calendar_write),
            description = permissionStatusText(state.calendarWriteGranted),
            onClick = onCalendarWriteClick
        ).takeIf { state.assistantEnabled },
        SettingsItem.Action(
            title = stringResource(R.string.settings_permission_contacts),
            description = permissionStatusText(state.contactsReadGranted),
            onClick = onContactsClick
        ).takeIf { state.assistantEnabled },
        SettingsItem.Toggle(
            stringResource(R.string.settings_assistant_keep_loaded),
            state.assistantKeepLoaded,
            onAssistantKeepLoadedChanged
        ).takeIf { state.assistantEnabled },
        SettingsItem.Action(
            title = stringResource(R.string.settings_assistant_free_memory),
            description = stringResource(R.string.assistant_model_status_loaded)
                .takeIf { state.assistantModelStatus == "loaded" },
            onClick = onFreeMemory
        ).takeIf { state.assistantEnabled && state.assistantModelStatus == "loaded" },
        SettingsItem.Toggle(
            stringResource(R.string.settings_assistant_speak),
            state.assistantSpeakResponses,
            onAssistantSpeakChanged
        ).takeIf { state.assistantEnabled },
        SettingsItem.Toggle(
            stringResource(R.string.settings_assistant_volume_button),
            state.assistantVolumeButtonEnabled,
            onAssistantVolumeButtonChanged
        ).takeIf { state.assistantEnabled },
        SettingsItem.Toggle(
            stringResource(R.string.settings_assistant_mic_button),
            state.assistantMicButtonVisible,
            onAssistantMicButtonChanged
        ).takeIf { state.assistantEnabled },
        SettingsItem.Toggle(
            stringResource(R.string.settings_assistant_bubble),
            state.assistantBubbleEnabled,
            onAssistantBubbleChanged
        ).takeIf { state.assistantEnabled }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.settings_header_assistant),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        val density = LocalDensity.current
        val listBottomPadding = 28.dp +
            with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is SettingsItem.Header -> SettingsHeaderRow(item.title)
                    is SettingsItem.Toggle -> SettingsToggleRow(item.title, item.value, item.onChange)
                    is SettingsItem.Action -> SettingsActionRow(item.title, item.description, item.onClick)
                    else -> {}
                }
            }
        }
    }

    if (showModelSheet) {
        ModelSetupSheet(
            status = state.assistantModelStatus,
            fileName = state.assistantModelFileName,
            error = state.assistantModelError,
            freeRamBytes = assistantFreeRamBytes,
            totalRamBytes = assistantTotalRamBytes,
            downloadedIds = downloadedIds,
            downloadState = downloadState,
            hfToken = hfToken,
            onHfTokenChanged = onHfTokenChanged,
            onDownloadModel = onDownloadModel,
            onSelectDownloaded = onSelectDownloaded,
            onCancelDownload = onCancelDownload,
            onOpenDownloadPage = onAssistantOpenDownloadPage,
            onSelectFile = onAssistantModelPick,
            onDeleteModel = onAssistantModelDelete,
            onDismiss = { showModelSheet = false }
        )
    }

    if (showMemoryViewer) {
        MemoryViewerDialog(
            loadFacts = onLoadMemoryFacts,
            onDeleteFact = onDeleteMemoryFact,
            onClearSubject = onClearMemorySubject,
            onForgetAll = onForgetAllMemory,
            onDismiss = { showMemoryViewer = false }
        )
    }
}

@Composable
private fun MemoryViewerDialog(
    loadFacts: () -> Map<String, List<MemoryFact>>,
    onDeleteFact: (String, String) -> Unit,
    onClearSubject: (String) -> Unit,
    onForgetAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var facts by remember { mutableStateOf<Map<String, List<MemoryFact>>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var confirmForgetAll by remember { mutableStateOf(false) }

    suspend fun reload() {
        facts = withContext(Dispatchers.IO) { loadFacts() }
        loaded = true
    }

    LaunchedEffect(Unit) { reload() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_assistant_memory_viewer)) },
        text = {
            if (!loaded) {
                Text(stringResource(R.string.memory_viewer_loading))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (facts.all { it.value.isEmpty() }) {
                        item { Text(stringResource(R.string.memory_viewer_empty)) }
                    }
                    facts.forEach { (subject, list) ->
                        if (list.isNotEmpty()) {
                            item(key = subject) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = subject.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = {
                                            onClearSubject(subject)
                                            scope.launch { reload() }
                                        }) {
                                            Text(stringResource(R.string.memory_clear_subject))
                                        }
                                    }
                                    list.forEach { fact ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = fact.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = {
                                                onDeleteFact(subject, fact.id)
                                                scope.launch { reload() }
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription =
                                                        stringResource(R.string.memory_delete_fact),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (loaded && facts.any { it.value.isNotEmpty() }) {
                TextButton(onClick = { confirmForgetAll = true }) {
                    Text(
                        stringResource(R.string.memory_forget_all),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_close)) }
        }
    )

    if (confirmForgetAll) {
        AlertDialog(
            onDismissRequest = { confirmForgetAll = false },
            title = { Text(stringResource(R.string.memory_forget_all)) },
            text = { Text(stringResource(R.string.memory_forget_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onForgetAll()
                    confirmForgetAll = false
                    scope.launch { reload() }
                }) {
                    Text(
                        stringResource(R.string.memory_forget_all),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmForgetAll = false }) {
                    Text(stringResource(R.string.memory_close))
                }
            }
        )
    }
}

@Composable
internal fun assistantModelStatusText(status: String): String {
    return when (status) {
        "copying" -> stringResource(R.string.assistant_model_status_copying)
        "selected" -> stringResource(R.string.assistant_model_status_selected)
        "loading" -> stringResource(R.string.assistant_model_status_loading)
        "loaded" -> stringResource(R.string.assistant_model_status_loaded)
        "error" -> stringResource(R.string.assistant_model_status_error)
        else -> stringResource(R.string.assistant_model_status_none)
    }
}
