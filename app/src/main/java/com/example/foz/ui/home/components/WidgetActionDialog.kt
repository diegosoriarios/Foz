package com.example.foz.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.ui.components.FozBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetActionDialog(
    onRemove: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onConfigure: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    FozBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Widget Actions",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.widget_move_left)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                modifier = Modifier.clickable { onMoveLeft() }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.widget_move_right)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onMoveRight() }
            )

            if (onConfigure != null) {
                ListItem(
                    headlineContent = { Text("Configure Widget") },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.clickable { onConfigure() }
                )
            }
            
            ListItem(
                headlineContent = { Text("Remove Widget") },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { onRemove() }
            )
        }
    }
}
