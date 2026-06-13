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
import androidx.compose.ui.unit.dp
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut

@Composable
fun AppActionDialog(
    selectedApp: AppInfo,
    pinnedPackageNames: List<String>,
    shortcuts: List<AppShortcut>,
    onDismiss: () -> Unit,
    onOpenAppInfo: (AppInfo) -> Unit,
    onUninstallApp: (AppInfo) -> Unit,
    onTogglePinned: (AppInfo) -> Unit,
    onMovePinned: (AppInfo, Int) -> Unit,
    onLaunchShortcut: (AppShortcut) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f))
            .padding(24.dp)
            .pointerInput(selectedApp.packageName) {
                detectTapGestures(onTap = { onDismiss() })
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
                    text = { Text("Open app info") },
                    onClick = {
                        onOpenAppInfo(selectedApp)
                        onDismiss()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Uninstall") },
                    onClick = {
                        onUninstallApp(selectedApp)
                        onDismiss()
                    }
                )
                
                val isPinned = pinnedPackageNames.contains(selectedApp.packageName)
                DropdownMenuItem(
                    text = {
                        Text(if (isPinned) "Unpin from favorites" else "Pin to favorites")
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
                            text = { Text("Move up in favorites") },
                            onClick = {
                                onMovePinned(selectedApp, -1)
                                onDismiss()
                            }
                        )
                    }
                    if (pinIndex < pinnedPackageNames.size - 1) {
                        DropdownMenuItem(
                            text = { Text("Move down in favorites") },
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
