package com.example.foz.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.manager.AssistantRuntimeModelStatus
import com.example.foz.model.ModelCatalog
import com.example.foz.model.ModelDownloader
import com.example.foz.model.ModelSpec
import com.example.foz.ui.components.FozBottomSheet
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSetupSheet(
    status: String,
    fileName: String?,
    error: String?,
    freeRamBytes: Long,
    totalRamBytes: Long,
    catalog: List<ModelSpec> = ModelCatalog.ALL,
    downloadedIds: Set<String> = emptySet(),
    downloadState: ModelDownloader.State = ModelDownloader.State.Idle,
    hfToken: String = "",
    onHfTokenChanged: (String) -> Unit = {},
    onDownloadModel: (ModelSpec) -> Unit = {},
    onSelectDownloaded: (ModelSpec) -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onOpenDownloadPage: () -> Unit,
    onSelectFile: () -> Unit,
    onDeleteModel: () -> Unit,
    onDismiss: () -> Unit
) {
    FozBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.assistant_model_title),
                style = MaterialTheme.typography.headlineSmall
            )

            val statusText = when (status) {
                AssistantRuntimeModelStatus.COPYING.name.lowercase() -> stringResource(R.string.assistant_model_status_copying)
                AssistantRuntimeModelStatus.SELECTED.name.lowercase() -> stringResource(R.string.assistant_model_status_selected)
                AssistantRuntimeModelStatus.LOADING.name.lowercase() -> stringResource(R.string.assistant_model_status_loading)
                AssistantRuntimeModelStatus.LOADED.name.lowercase() -> stringResource(R.string.assistant_model_status_loaded)
                AssistantRuntimeModelStatus.ERROR.name.lowercase() -> stringResource(R.string.assistant_model_status_error)
                else -> stringResource(R.string.assistant_model_status_none)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_assistant_model),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = stringResource(
                    R.string.assistant_model_ram,
                    String.format(Locale.getDefault(), "%.1f GB", freeRamBytes / GB.toDouble()),
                    String.format(Locale.getDefault(), "%.1f GB", totalRamBytes / GB.toDouble())
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (freeRamBytes / GB.toDouble() < MIN_FREE_GB) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            val noModel = status == AssistantRuntimeModelStatus.NONE.name.lowercase()
            if (noModel) {
                var tokenVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = hfToken,
                    onValueChange = onHfTokenChanged,
                    label = { Text(stringResource(R.string.assistant_download_token)) },
                    placeholder = { Text(stringResource(R.string.assistant_download_token_hint)) },
                    singleLine = true,
                    visualTransformation = if (tokenVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                imageVector = if (tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.assistant_download_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val failedMessage = (downloadState as? ModelDownloader.State.Failed)?.message
            if (failedMessage != null) {
                Text(
                    text = failedMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            catalog.forEach { spec ->
                ModelCatalogRow(
                    spec = spec,
                    isActive = fileName == spec.fileName,
                    isDownloaded = spec.id in downloadedIds || fileName == spec.fileName,
                    downloading = (downloadState as? ModelDownloader.State.Downloading)?.specId == spec.id,
                    progress = (downloadState as? ModelDownloader.State.Downloading)
                        ?.takeIf { it.specId == spec.id },
                    totalRamBytes = totalRamBytes,
                    onDownload = onDownloadModel,
                    onSelect = onSelectDownloaded,
                    onCancel = onCancelDownload
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetAction(text = stringResource(R.string.assistant_model_open_download_page), onClick = onOpenDownloadPage)
                SheetAction(text = stringResource(R.string.assistant_model_select_file), onClick = onSelectFile)
                if (fileName != null) {
                    SheetAction(
                        text = stringResource(R.string.assistant_model_delete),
                        onClick = onDeleteModel,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        }
    }
}

@Composable
private fun ModelCatalogRow(
    spec: ModelSpec,
    isActive: Boolean,
    isDownloaded: Boolean,
    downloading: Boolean,
    progress: ModelDownloader.State.Downloading?,
    totalRamBytes: Long,
    onDownload: (ModelSpec) -> Unit,
    onSelect: (ModelSpec) -> Unit,
    onCancel: () -> Unit
) {
    val ramWarning = remember(spec, totalRamBytes) {
        ModelCatalog.ramWarning(spec, totalRamBytes)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spec.displayName,
                    style = MaterialTheme.typography.labelLarge
                )
                val suffix = buildString {
                    append(formatBytes(spec.approxBytes))
                    if (spec.needsToken) {
                        append(" • ")
                        append(stringResource(R.string.assistant_model_needs_token))
                    } else {
                        append(" • ")
                        append(stringResource(R.string.assistant_model_no_token))
                    }
                }
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ramWarning != null) {
                    Text(
                        text = ramWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            when {
                isActive -> Text(
                    text = stringResource(R.string.assistant_model_status_active),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                downloading -> CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    strokeWidth = 2.dp
                )
                isDownloaded -> TextButton(onClick = { onSelect(spec) }) {
                    Text(stringResource(R.string.assistant_model_use))
                }
                else -> TextButton(onClick = { onDownload(spec) }) {
                    Text(
                        stringResource(
                            R.string.assistant_model_download_btn,
                            formatBytes(spec.approxBytes)
                        )
                    )
                }
            }
        }
        if (progress != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.received.toFloat() / progress.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.assistant_download_progress,
                            formatBytes(progress.received),
                            formatBytes(progress.total)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.assistant_download_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(text: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.0f MB", mb)
    }
}

private const val GB = 1024L * 1024 * 1024
private const val MIN_FREE_GB = 2.0
