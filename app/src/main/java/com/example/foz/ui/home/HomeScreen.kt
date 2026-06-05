package com.example.foz.ui.home

import android.appwidget.AppWidgetHostView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.applist.AppDrawerScreen
import com.example.foz.ui.applist.AppIcon
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: LauncherUiState,
    widgetViews: List<Pair<Int, AppWidgetHostView>>,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onCloseDrawer: () -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenAppInfo: (AppInfo) -> Unit,
    onUninstallApp: (AppInfo) -> Unit,
    onLaunchShortcut: (AppShortcut) -> Unit,
    onTogglePinned: (AppInfo) -> Unit,
    onDismissAppActions: () -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state.drawerOpen) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (totalDrag < -120f) {
                            onSwipeUp()
                        } else if (totalDrag > 120f) {
                            onSwipeDown()
                        }
                        totalDrag = 0f
                    }
                )
            }
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = state.now.format(timeFormatter),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = state.now.format(dateFormatter),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (state.pinnedApps.isNotEmpty()) {
                Text(
                    text = "Favorites",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.pinnedApps, key = { it.packageName }) { app ->
                        Surface(
                            onClick = { onLaunchApp(app) },
                            tonalElevation = 3.dp,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                AppIcon(
                                    drawable = app.icon,
                                    contentDescription = app.name,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
            if (widgetViews.isNotEmpty()) {
                Text(
                    text = "Widgets",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    widgetViews.forEach { (widgetId, hostView) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AndroidView(
                                factory = { hostView },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(onClick = { onRemoveWidget(widgetId) }, shape = MaterialTheme.shapes.medium) {
                                Text(text = "Remove", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Swipe up for apps • Swipe down for notifications",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        FloatingActionButton(
            onClick = onAddWidget,
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }

        AnimatedVisibility(
            visible = state.drawerOpen,
            enter = slideInVertically(animationSpec = tween(260)) { it },
            exit = slideOutVertically(animationSpec = tween(260)) { it }
        ) {
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                AppDrawerScreen(
                    apps = state.filteredApps,
                    query = state.searchQuery,
                    onQueryChange = onSearchChange,
                    onLaunchApp = {
                        onLaunchApp(it)
                        onCloseDrawer()
                    },
                    onLongPressApp = onLongPressApp,
                    sectionIndexes = state.sectionIndexes
                )
            }
        }

        val selectedApp = state.selectedApp
        if (selectedApp != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f))
                    .padding(24.dp)
                    .align(Alignment.Center)
                    .pointerInput(selectedApp.packageName) {
                        detectTapGestures(onTap = { onDismissAppActions() })
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
                                onDismissAppActions()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Uninstall") },
                            onClick = {
                                onUninstallApp(selectedApp)
                                onDismissAppActions()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (state.pinnedPackageNames.contains(selectedApp.packageName)) "Unpin from favorites" else "Pin to favorites")
                            },
                            onClick = {
                                onTogglePinned(selectedApp)
                                onDismissAppActions()
                            }
                        )
                        state.selectedAppShortcuts.forEach { shortcut ->
                            DropdownMenuItem(
                                text = { Text(shortcut.label) },
                                onClick = {
                                    onLaunchShortcut(shortcut)
                                    onDismissAppActions()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
