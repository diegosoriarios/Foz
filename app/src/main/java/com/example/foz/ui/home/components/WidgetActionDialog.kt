package com.example.foz.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foz.ui.components.FozBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetActionDialog(
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onResize: (Int) -> Unit,
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
                headlineContent = { Text("Move Up") },
                leadingContent = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                modifier = Modifier.clickable { onMoveUp() }
            )
            ListItem(
                headlineContent = { Text("Move Down") },
                leadingContent = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                modifier = Modifier.clickable { onMoveDown() }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            
            Text(
                text = "Resize",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val sizes = listOf("Small" to 100, "Medium" to 180, "Large" to 400)
                sizes.forEach { (label, height) ->
                    OutlinedButton(
                        onClick = { onResize(height) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
