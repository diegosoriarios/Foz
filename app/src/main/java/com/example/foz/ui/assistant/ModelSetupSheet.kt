package com.example.foz.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.manager.AssistantRuntimeModelStatus
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

            if (fileName != null && status != AssistantRuntimeModelStatus.NONE.name.lowercase()) {
                Text(
                    text = stringResource(R.string.assistant_model_file_copied, fileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(R.string.assistant_model_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val freeGb = freeRamBytes / GB.toDouble()
            val totalGb = totalRamBytes / GB.toDouble()
            Text(
                text = stringResource(
                    R.string.assistant_model_ram,
                    String.format(Locale.getDefault(), "%.1f GB", freeGb),
                    String.format(Locale.getDefault(), "%.1f GB", totalGb)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (freeGb < MIN_FREE_GB) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (freeGb < MIN_FREE_GB) {
                Text(
                    text = stringResource(R.string.assistant_model_ram_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
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

private const val GB = 1024L * 1024 * 1024
private const val MIN_FREE_GB = 2.0
