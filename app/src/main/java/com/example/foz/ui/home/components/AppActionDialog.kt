package com.example.foz.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.foz.R
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f))
            .padding(24.dp)
            .pointerInput(selectedApp.packageName) {
                detectTapGestures(onTap = { onDismiss() })
            }
            .semantics {
                onClick(label = "Dismiss") { onDismiss(); true }
            }
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = selectedApp.name, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    onClick = {
                        onRenameApp(selectedApp)
                        // Don't dismiss yet, let the rename dialog handle it
                    }
                )

                if (isIconPackActive) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_change_icon)) },
                        onClick = {
                            onChangeIcon(selectedApp)
                        }
                    )
                }

                val isHidden = hiddenPackageNames.contains(selectedApp.packageName)
                DropdownMenuItem(
                    text = { Text(stringResource(if (isHidden) R.string.action_unhide_app else R.string.action_hide_app)) },
                    onClick = {
                        onHideApp(selectedApp)
                        onDismiss()
                    }
                )

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_app_info)) },
                    onClick = {
                        onOpenAppInfo(selectedApp)
                        onDismiss()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_uninstall)) },
                    onClick = {
                        onUninstallApp(selectedApp)
                        onDismiss()
                    }
                )
                
                val isPinned = pinnedPackageNames.contains(selectedApp.packageName)
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (isPinned) R.string.action_unpin_favorite else R.string.action_pin_favorite))
                    },
                    onClick = {
                        onTogglePinned(selectedApp)
                        onDismiss()
                    }
                )
                
                if (isPinned) {
                    val pinIndex = pinnedPackageNames.indexOf(selectedApp.packageName)
                    if (pinIndex > 0) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move_up)) },
                            onClick = {
                                onMovePinned(selectedApp, -1)
                                onDismiss()
                            }
                        )
                    }
                    if (pinIndex < pinnedPackageNames.size - 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move_down)) },
                            onClick = {
                                onMovePinned(selectedApp, 1)
                                onDismiss()
                            }
                        )
                    }
                }
                
                shortcuts.forEach { shortcut ->
                    DropdownMenuItem(
                        text = { Text(shortcut.label) },
                        onClick = {
                            onLaunchShortcut(shortcut)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
