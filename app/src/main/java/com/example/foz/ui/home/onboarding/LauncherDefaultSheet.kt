package com.example.foz.ui.home.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.ui.components.FozBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherDefaultSheet(
    onSetAsDefault: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    FozBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.launcher_sheet_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.launcher_sheet_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.launcher_sheet_reminder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    onDismiss()
                    onSetAsDefault()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.launcher_sheet_confirm))
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.launcher_sheet_dismiss))
            }
        }
    }
}
