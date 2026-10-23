package com.example.foz.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.ui.applist.AppIcon
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
    onToggleMute: (AppInfo) -> Unit,
    isMuted: Boolean,
    isIconPackActive: Boolean,
    isInFolder: Boolean = false,
    onAddToFolder: (AppInfo) -> Unit = {},
    onRemoveFromFolder: (AppInfo) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    var advancedExpanded by remember { mutableStateOf(false) }

    FozBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(
                    drawable = selectedApp.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = selectedApp.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onRenameApp(selectedApp) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(8.dp))

            val isPinned = pinnedPackageNames.contains(selectedApp.packageName)
            ListItem(
                headlineContent = { Text(stringResource(if (isPinned) R.string.action_unpin_favorite else R.string.action_pin_favorite)) },
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

            val isHidden = hiddenPackageNames.contains(selectedApp.packageName)
            ListItem(
                headlineContent = { Text(stringResource(if (isHidden) R.string.action_unhide_app else R.string.action_hide_app)) },
                leadingContent = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                modifier = Modifier.clickable {
                    onHideApp(selectedApp)
                    onDismiss()
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.action_app_info)) },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.clickable {
                    onOpenAppInfo(selectedApp)
                    onDismiss()
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(if (isMuted) R.string.action_unmute_app else R.string.action_mute_app)) },
                leadingContent = {
                    Icon(
                        if (isMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    onToggleMute(selectedApp)
                    onDismiss()
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.action_advanced)) },
                trailingContent = {
                    Icon(
                        if (advancedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { advancedExpanded = !advancedExpanded }
            )

            AnimatedVisibility(
                visible = advancedExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.action_uninstall)) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            onUninstallApp(selectedApp)
                            onDismiss()
                        }
                    )

                    if (isPinned) {
                        val pinIndex = pinnedPackageNames.indexOf(selectedApp.packageName)
                        if (pinIndex > 0) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.action_move_up)) },
                                leadingContent = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    onMovePinned(selectedApp, -1)
                                    onDismiss()
                                }
                            )
                        }
                        if (pinIndex < pinnedPackageNames.size - 1) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.action_move_down)) },
                                leadingContent = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    onMovePinned(selectedApp, 1)
                                    onDismiss()
                                }
                            )
                        }
                    }

                    if (isIconPackActive) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.action_change_icon)) },
                            leadingContent = { Icon(Icons.Default.AppRegistration, contentDescription = null) },
                            modifier = Modifier.clickable { onChangeIcon(selectedApp) }
                        )
                    }

                    if (isInFolder) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.folder_remove_from)) },
                            leadingContent = { Icon(Icons.Default.FolderOff, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onRemoveFromFolder(selectedApp)
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.folder_add_to)) },
                            leadingContent = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            modifier = Modifier.clickable {
                                onAddToFolder(selectedApp)
                            }
                        )
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

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))

            ListItem(
                headlineContent = { Text(stringResource(R.string.action_foz_settings)) },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onOpenSettings()
                }
            )
        }
    }
}
