package com.example.foz.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.ui.components.FozBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionDialog(
    selectedApp: AppInfo,
    pinnedPackageNames: List<String>,
    hiddenPackageNames: Set<String>,
    shortcuts: List<AppShortcut>,
    onDismiss: () -> Unit,
    onOpenAppInfo: (AppInfo) -> Unit,
    onUninstallApp: (AppInfo) -> Unit,
    onTogglePinned: (AppInfo) -> Unit,
    onMovePinned: (AppInfo, Int) -> Unit,
    onLaunchShortcut: (AppShortcut) -> Unit,
    onRenameApp: (AppInfo) -> Unit,
    onHideApp: (AppInfo) -> Unit,
    onChangeIcon: (AppInfo) -> Unit,
    isIconPackActive: Boolean
) {
    FozBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = selectedApp.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("Rename") },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                modifier = Modifier.clickable { onRenameApp(selectedApp) }
            )

            if (isIconPackActive) {
                ListItem(
                    headlineContent = { Text("Change icon") },
                    leadingContent = { Icon(Icons.Default.AppRegistration, contentDescription = null) },
                    modifier = Modifier.clickable { onChangeIcon(selectedApp) }
                )
            }

            val isHidden = hiddenPackageNames.contains(selectedApp.packageName)
            ListItem(
                headlineContent = { Text(if (isHidden) "Unhide app" else "Hide app") },
                leadingContent = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                modifier = Modifier.clickable {
                    onHideApp(selectedApp)
                    onDismiss()
                }
            )

            ListItem(
                headlineContent = { Text("Open app info") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.clickable {
                    onOpenAppInfo(selectedApp)
                    onDismiss()
                }
            )
            ListItem(
                headlineContent = { Text("Uninstall") },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable {
                    onUninstallApp(selectedApp)
                    onDismiss()
                }
            )
            
            val isPinned = pinnedPackageNames.contains(selectedApp.packageName)
            ListItem(
                headlineContent = { Text(if (isPinned) "Unpin from favorites" else "Pin to favorites") },
                leadingContent = { 
                    Icon(
                        if (isPinned) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                        contentDescription = null
                    ) 
                },
                modifier = Modifier.clickable {
                    onTogglePinned(selectedApp)
                    onDismiss()
                }
            )
            
            if (isPinned) {
                val pinIndex = pinnedPackageNames.indexOf(selectedApp.packageName)
                if (pinIndex > 0) {
                    ListItem(
                        headlineContent = { Text("Move up in favorites") },
                        leadingContent = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) },
                        modifier = Modifier.clickable {
                            onMovePinned(selectedApp, -1)
                            onDismiss()
                        }
                    )
                }
                if (pinIndex < pinnedPackageNames.size - 1) {
                    ListItem(
                        headlineContent = { Text("Move down in favorites") },
                        leadingContent = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                        modifier = Modifier.clickable {
                            onMovePinned(selectedApp, 1)
                            onDismiss()
                        }
                    )
                }
            }
            
            if (shortcuts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Shortcuts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                shortcuts.forEach { shortcut ->
                    ListItem(
                        headlineContent = { Text(shortcut.label) },
                        modifier = Modifier.clickable {
                            onLaunchShortcut(shortcut)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
