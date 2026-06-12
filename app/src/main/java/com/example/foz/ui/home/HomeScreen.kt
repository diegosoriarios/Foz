package com.example.foz.ui.home

import android.appwidget.AppWidgetHostView
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.appcompat.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.applist.AlphabetSidebar
import com.example.foz.ui.applist.AppIcon
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.collections.List

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
    onMovePinned: (AppInfo, Int) -> Unit,
    onDismissAppActions: () -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onRequestLauncherRole: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onDismissLauncherOnboarding: () -> Unit,
    onToggleOnboardingFavorite: (AppInfo) -> Unit,
    onCompleteInitialOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = DateTimeFormatter.ofPattern(
        if (state.clockUse24h) "HH:mm" else "hh:mm a",
        Locale.getDefault()
    )
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

    val backgroundColor by animateColorAsState(
        targetValue = if (state.drawerOpen) Color.Black.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "backgroundColorAnimation"
    )

    val onLetterSelected: (Char) -> Unit = { letter ->
        if (state.drawerOpen) {
            state.sectionIndexes[letter]?.let { index ->
                coroutineScope.launch {
                    appListState.animateScrollToItem(index)
                }
            }
        } else {
            onOpenDrawerAtLetter(letter)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(state.drawerOpen, state.swipeUpPanelOpen) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (!state.drawerOpen && !state.swipeUpPanelOpen && state.swipeUpEnabled && totalDrag < -120f) {
                            onSwipeUp()
                        } else if (state.swipeUpPanelOpen && totalDrag > 120f) {
                            onCloseSwipeUpPanel()
                        } else if (!state.drawerOpen && !state.swipeUpPanelOpen && state.swipeDownEnabled && totalDrag > 120f) {
                            onSwipeDown()
                        }
                        totalDrag = 0f
                    }
                )
            }
    ) {
        if (!state.initialOnboardingCompleted) {
            InitialOnboardingScreen(
                apps = state.allApps,
                selectedPackages = state.onboardingSelectedPackages,
                onToggleApp = onToggleOnboardingFavorite,
                onContinue = onCompleteInitialOnboarding
            )
            return@Box
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            Box(modifier = Modifier.height(228.dp)) {
                if (!state.drawerOpen) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
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
                        // Weather details
                        state.weather?.let { weather ->
                            Text(
                                text = "${weather.temperature}°C • ${weather.condition}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "${weather.location} • ${weather.humidity}% humidity",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
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
                                                modifier = Modifier.size(state.appIconSizeDp.dp)
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
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(onClick = onOpenWallpaperPicker, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
                                        Text(
                                            text = "Change wallpaper",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                    Surface(onClick = onOpenSettings, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
                                        Text(
                                            text = "Settings",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (state.pinnedApps.isNotEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopStart
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    state.pinnedApps.forEach { app ->
                                        Surface(
                                            tonalElevation = 3.dp,
                                            shape = MaterialTheme.shapes.large,
                                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp).combinedClickable(
                                                onClick = { onLaunchApp(app) },
                                                onLongClick = { onLongPressApp(app) }
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                            ) {
                                                AppIcon(
                                                    drawable = app.icon,
                                                    contentDescription = app.name,
                                                    modifier = Modifier.size(state.appIconSizeDp.dp)
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
                }
                AlphabetSidebar(
                    onLetterSelected = onLetterSelected,
                    onBackToFavorites = onCloseDrawer,
                    isDrawerOpen = state.drawerOpen,
                    isVisible = false,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                )
                AlphabetSidebar(
                    onLetterSelected = onLetterSelected,
                    onBackToFavorites = onCloseDrawer,
                    isDrawerOpen = state.drawerOpen,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (state.launcherStatusChecked && !state.isLauncherDefault) {
                LauncherOnboardingCard(
                    dismissed = state.launcherOnboardingDismissed,
                    onRequestLauncherRole = onRequestLauncherRole,
                    onOpenLauncherSettings = onOpenLauncherSettings,
                    onDismiss = onDismissLauncherOnboarding
                )
                Spacer(modifier = Modifier.height(12.dp))
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
                        if (state.pinnedPackageNames.contains(selectedApp.packageName)) {
                            val pinIndex = state.pinnedPackageNames.indexOf(selectedApp.packageName)
                            if (pinIndex > 0) {
                                DropdownMenuItem(
                                    text = { Text("Move up in favorites") },
                                    onClick = {
                                        onMovePinned(selectedApp, -1)
                                        onDismissAppActions()
                                    }
                                )
                            }
                            if (pinIndex < state.pinnedPackageNames.size - 1) {
                                DropdownMenuItem(
                                    text = { Text("Move down in favorites") },
                                    onClick = {
                                        onMovePinned(selectedApp, 1)
                                        onDismissAppActions()
                                    }
                                )
                            }
                        }
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

// SettingsScreen has been moved to its own file.

@Composable
private fun InitialOnboardingScreen(
    apps: List<AppInfo>,
    selectedPackages: Set<String>,
    onToggleApp: (AppInfo) -> Unit,
    onContinue: () -> Unit
) {
    val canContinue = selectedPackages.size in 2..8
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Text(
            text = "Welcome to Foz",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Foz keeps things simple: swipe up for apps and widgets, long-press apps for quick actions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Choose 2 to 8 favorite apps to continue (${selectedPackages.size}/8)",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                val isSelected = selectedPackages.contains(app.packageName)
                Surface(
                    tonalElevation = if (isSelected) 4.dp else 1.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = isSelected || selectedPackages.size < 8,
                            onClick = { onToggleApp(app) }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppIcon(
                            drawable = app.icon,
                            contentDescription = app.name,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isSelected) "Selected" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun LauncherOnboardingCard(
    dismissed: Boolean,
    onRequestLauncherRole: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                text = if (dismissed) "Foz is not your default launcher" else "Set Foz as your default launcher",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (dismissed) {
                    "Tap below when you want to switch to Foz."
                } else {
                    "You need to grant launcher role to use Foz when pressing Home."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = onRequestLauncherRole, shape = MaterialTheme.shapes.medium) {
                    Text(
                        text = if (dismissed) "Set as launcher" else "Continue",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Surface(onClick = onOpenLauncherSettings, shape = MaterialTheme.shapes.medium) {
                    Text(
                        text = "Open settings",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                if (!dismissed) {
                    Surface(onClick = onDismiss, shape = MaterialTheme.shapes.medium) {
                        Text(
                            text = "Maybe later",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
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

@Preview
@Composable
fun HomeScreenPreview() {
    val mockAppInfo = AppInfo(
        name = "Sample App",
        packageName = "com.example.sample",
        icon = ContextCompat.getDrawable(LocalContext.current, com.example.foz.R.drawable.ic_launcher_background)!!,
        className = "com.example.sample.MainActivity",
    )

    val mockAppInfo2 = AppInfo(
        name = "App",
        packageName = "com.example.sample",
        icon = ContextCompat.getDrawable(LocalContext.current, com.example.foz.R.drawable.ic_launcher_background)!!,
        className = "com.example.sample.MainActivity",
    )

    val mockState = LauncherUiState(
        allApps = listOf(mockAppInfo2, mockAppInfo, mockAppInfo, mockAppInfo, mockAppInfo,mockAppInfo),
        filteredApps = listOf(mockAppInfo),
        initialOnboardingCompleted = true,
        pinnedApps= listOf(mockAppInfo),
        drawerOpen = true
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            state = mockState,
            widgetViews = emptyList(),
            onLaunchApp = { /* TODO */ },
            onLongPressApp = { /* TODO */ },
            onSwipeUp = { /* TODO */ },
            onSwipeDown = { /* TODO */ },
            onCloseDrawer = { /* TODO */ },
            onCloseSwipeUpPanel = { /* TODO */ },
            onOpenDrawerAtLetter = { /* TODO */ },
            onRequestedSectionConsumed = { /* TODO */ },
            onSearchChange = { /* TODO */ },
            onOpenAppInfo = { /* TODO */ },
            onUninstallApp = { /* TODO */ },
            onLaunchShortcut = { /* TODO */ },
            onTogglePinned = { /* TODO */ },
            onMovePinned = { _, _ -> /* TODO */ },
            onDismissAppActions = { /* TODO */ },
            onAddWidget = { /* TODO */ },
            onRemoveWidget = { /* TODO */ },
            onOpenWallpaperPicker = { /* TODO */ },
            onRequestLauncherRole = { /* TODO */ },
            onOpenLauncherSettings = { /* TODO */ },
            onDismissLauncherOnboarding = { /* TODO */ },
            onToggleOnboardingFavorite = { /* TODO */ },
            onCompleteInitialOnboarding = { /* TODO */ },
            onOpenSettings = { /* TODO */ }
        )
    }
}
