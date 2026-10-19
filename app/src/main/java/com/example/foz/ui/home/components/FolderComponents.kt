package com.example.foz.ui.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.model.AppInfo
import com.example.foz.model.FolderInfo
import com.example.foz.ui.applist.AppIcon

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItem(
    folder: FolderInfo,
    apps: List<AppInfo>,
    iconSize: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folderApps = folder.packageNames.mapNotNull { pkg -> apps.find { it.packageName == pkg } }

    Surface(
        color = Color.Transparent,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            FolderIconThumbnail(
                folderApps = folderApps,
                size = iconSize.dp
            )
            TextWithOutline(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
                mainColor = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun FolderIconThumbnail(
    folderApps: List<AppInfo>,
    size: androidx.compose.ui.unit.Dp
) {
    val miniSize = (size.value / 2f - 2f).dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (folderApps.isEmpty()) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size / 2)
            )
        } else if (folderApps.size == 1) {
            AppIcon(
                drawable = folderApps[0].icon,
                contentDescription = null,
                modifier = Modifier.size(size - 6.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                folderApps.take(4).chunked(2).forEach { rowApps ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        rowApps.forEach { app ->
                            AppIcon(
                                drawable = app.icon,
                                contentDescription = null,
                                modifier = Modifier.size(miniSize)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderContent(
    folder: FolderInfo,
    apps: List<AppInfo>,
    sortAlphabetical: Boolean = false,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onRemoveApp: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val folderApps = folder.packageNames.mapNotNull { pkg -> apps.find { it.packageName == pkg } }
        .let { if (sortAlphabetical) it.sortedBy { app -> app.name.lowercase() } else it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = folder.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(modifier = Modifier.height(8.dp))

        if (folderApps.isEmpty()) {
            Text(
                text = stringResource(R.string.folder_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                folderApps.forEach { app ->
                    ListItem(
                        headlineContent = { Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            AppIcon(
                                drawable = app.icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onRemoveApp(app) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.folder_remove_from),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        },
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    onLaunchApp(app)
                                    onDismiss()
                                },
                                onLongClick = { onLongPressApp(app) }
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun AddToFolderSheet(
    folders: List<FolderInfo>,
    onPick: (FolderInfo) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.folder_add_to),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(modifier = Modifier.height(8.dp))

        if (folders.isNotEmpty()) {
            folders.forEach { folder ->
                ListItem(
                    headlineContent = { Text(folder.name) },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                    trailingContent = { Text(folder.packageNames.size.toString()) },
                    modifier = Modifier.clickable {
                        onPick(folder)
                        onDismiss()
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            OutlinedTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                label = { Text(stringResource(R.string.folder_name_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    onCreate(newFolderName)
                    onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.folder_new))
            }
        }
    }
}

@Composable
fun FolderActionsSheet(
    folder: FolderInfo,
    folderIndex: Int,
    folderCount: Int,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = folder.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.folder_name_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    onRename(name)
                    onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.folder_rename))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (folderIndex > 0) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.action_move_up)) },
                leadingContent = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) },
                modifier = Modifier.clickable {
                    onMove(-1)
                    onDismiss()
                }
            )
        }
        if (folderIndex < folderCount - 1) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.action_move_down)) },
                leadingContent = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                modifier = Modifier.clickable {
                    onMove(1)
                    onDismiss()
                }
            )
        }
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.folder_delete),
                    color = MaterialTheme.colorScheme.error
                )
            },
            leadingContent = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            modifier = Modifier.clickable {
                onDelete()
                onDismiss()
            }
        )
    }
}
