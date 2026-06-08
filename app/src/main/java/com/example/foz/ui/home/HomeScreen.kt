package com.example.foz.ui.home

import android.appwidget.AppWidgetHostView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.applist.AlphabetSidebar
import com.example.foz.ui.applist.AppIcon
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

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
    onCloseSwipeUpPanel: () -> Unit,
    onOpenDrawerAtLetter: (Char) -> Unit,
    onRequestedSectionConsumed: () -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenAppInfo: (AppInfo) -> Unit,
    onUninstallApp: (AppInfo) -> Unit,
    onLaunchShortcut: (AppShortcut) -> Unit,
    onTogglePinned: (AppInfo) -> Unit,
    onDismissAppActions: () -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    val appListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val drawerCloseOnPullConnection = remember(state.drawerOpen, appListState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    state.drawerOpen &&
                    source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    appListState.firstVisibleItemIndex == 0 &&
                    appListState.firstVisibleItemScrollOffset == 0
                ) {
                    onCloseDrawer()
                }
                return Offset.Zero
            }
        }
    }

    // Handle back button press: close drawer/panel/menu and return to favorites
    BackHandler(enabled = state.drawerOpen || state.swipeUpPanelOpen || state.selectedApp != null) {
        when {
            state.selectedApp != null -> onDismissAppActions()
            state.swipeUpPanelOpen -> onCloseSwipeUpPanel()
            state.drawerOpen -> onCloseDrawer()
        }
    }

    LaunchedEffect(state.searchQuery, state.drawerOpen) {
        if (state.drawerOpen) {
            appListState.scrollToItem(0)
        }
    }

    LaunchedEffect(state.requestedSectionLetter, state.sectionIndexes, state.drawerOpen) {
        if (!state.drawerOpen) return@LaunchedEffect
        val letter = state.requestedSectionLetter ?: return@LaunchedEffect
        state.sectionIndexes[letter]?.let { index ->
            appListState.scrollToItem(index)
        }
        onRequestedSectionConsumed()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state.drawerOpen, state.swipeUpPanelOpen) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (!state.drawerOpen && !state.swipeUpPanelOpen && totalDrag < -120f) {
                            onSwipeUp()
                        } else if (state.swipeUpPanelOpen && totalDrag > 120f) {
                            onCloseSwipeUpPanel()
                        } else if (!state.drawerOpen && !state.swipeUpPanelOpen && totalDrag > 120f) {
                            onSwipeDown()
                        }
                        totalDrag = 0f
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(drawerCloseOnPullConnection)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (state.drawerOpen) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChange,
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                            },
                            singleLine = true,
                            placeholder = { Text("Search apps") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(
                            state = appListState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(state.filteredApps, key = { _, app -> app.packageName }) { _, app ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                onLaunchApp(app)
                                                onCloseDrawer()
                                            },
                                            onLongClick = { onLongPressApp(app) }
                                        )
                                        .padding(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        AppIcon(
                                            drawable = app.icon,
                                            contentDescription = app.name,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = app.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (state.pinnedApps.isNotEmpty()) {
                            Text(
                                text = "Favorites",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                state.pinnedApps.forEach { app ->
                                    Surface(
                                        onClick = { onLaunchApp(app) },
                                        tonalElevation = 3.dp,
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            AppIcon(
                                                drawable = app.icon,
                                                contentDescription = app.name,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Text(
                                                text = app.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                AlphabetSidebar(
                    onLetterSelected = { letter ->
                        if (state.drawerOpen) {
                            state.sectionIndexes[letter]?.let { index ->
                                coroutineScope.launch { appListState.animateScrollToItem(index) }
                            }
                        } else {
                            onOpenDrawerAtLetter(letter)
                        }
                    },
                    isDrawerOpen = state.drawerOpen,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = onOpenWallpaperPicker, shape = MaterialTheme.shapes.medium) {
                    Text(
                        text = "Change wallpaper",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.swipeUpPanelOpen,
            enter = slideInVertically(animationSpec = tween(260)) { it },
            exit = slideOutVertically(animationSpec = tween(260)) { it }
        ) {
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                SwipeUpPanel(
                    query = state.searchQuery,
                    onQueryChange = onSearchChange,
                    widgetViews = widgetViews,
                    onAddWidget = onAddWidget,
                    onRemoveWidget = onRemoveWidget,
                    onClose = onCloseSwipeUpPanel
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

@Composable
private fun SwipeUpPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    widgetViews: List<Pair<Int, AppWidgetHostView>>,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Apps & Widgets",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Surface(onClick = onClose, shape = MaterialTheme.shapes.medium) {
                Text(text = "Close", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
            },
            singleLine = true,
            placeholder = { Text("Search apps") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Surface(onClick = onAddWidget, shape = MaterialTheme.shapes.medium) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(text = "Add widget")
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
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
        } else {
            Text(
                text = "No widgets added",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
