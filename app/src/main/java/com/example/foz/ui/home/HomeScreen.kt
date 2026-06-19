package com.example.foz.ui.home

import android.appwidget.AppWidgetHostView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.applist.AlphabetSidebar
import com.example.foz.ui.components.FozBottomSheet
import com.example.foz.ui.home.components.AppActionDialog
import com.example.foz.ui.home.components.AppListItem
import com.example.foz.ui.home.components.CustomButton
import com.example.foz.ui.home.components.FavoriteAppItem
import com.example.foz.ui.home.components.HomeHeader
import com.example.foz.ui.home.components.MediaControlCard
import com.example.foz.ui.home.components.WeatherForecastContent
import com.example.foz.ui.home.onboarding.InitialOnboardingScreen
import com.example.foz.ui.home.onboarding.LauncherOnboardingCard
import com.example.foz.ui.home.panels.SwipeUpPanel
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    onRenameApp: (AppInfo, String?) -> Unit,
    onHideApp: (AppInfo, Boolean) -> Unit,
    onSetCustomIcon: (AppInfo, String?) -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onResizeWidget: (Int, Int) -> Unit,
    onConfigureWidget: (Int) -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onRequestLauncherRole: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onDismissLauncherOnboarding: () -> Unit,
    onToggleOnboardingFavorite: (AppInfo) -> Unit,
    onCompleteInitialOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onShowWeatherForecast: () -> Unit = {},
    onDismissWeatherForecast: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember(state.clockUse24h) {
        DateTimeFormatter.ofPattern(
            if (state.clockUse24h) "HH:mm" else "hh:mm a",
            Locale.getDefault()
        )
    }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    }
    
    val appListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var interactingLetter by remember { mutableStateOf<Char?>(null) }
    var sidebarSelectedIndex by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    
    var appToRename by remember { mutableStateOf<AppInfo?>(null) }
    var appToSelectIcon by remember { mutableStateOf<AppInfo?>(null) }
    
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

    BackHandler(enabled = true) {
        when {
            state.selectedApp != null -> onDismissAppActions()
            state.swipeUpPanelOpen -> onCloseSwipeUpPanel()
            state.drawerOpen -> onCloseDrawer()
            // Consumes back press when on home screen to prevent accidental exits or "refreshes"
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
            appListState.scrollToItem(index, scrollOffset = 0)
        }
        onRequestedSectionConsumed()
    }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            state.monochromeMode -> Color.Black
            state.drawerOpen -> Color.Black.copy(alpha = 0.6f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 300),
        label = "backgroundColorAnimation"
    )

    val onLetterSelected: (Char) -> Unit = { letter ->
        interactingLetter = letter
        if (state.drawerOpen) {
            if (letter == '★') {
                onCloseDrawer()
            } else {
                state.sectionIndexes[letter]?.let { index ->
                    coroutineScope.launch {
                        appListState.scrollToItem(
                            index = index,
                            scrollOffset = 0
                        )
                    }
                }
            }
        } else if (letter != '★') {
            onOpenDrawerAtLetter(letter)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(state.drawerOpen, state.swipeUpPanelOpen) {
                if (state.drawerOpen || state.swipeUpPanelOpen) return@pointerInput
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (state.swipeUpEnabled && totalDrag < -120f) {
                            onSwipeUp()
                        } else if (state.swipeDownEnabled && totalDrag > 120f) {
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
                .padding(vertical = 28.dp),
        ) {
            if (!state.drawerOpen) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    HomeHeader(
                        state = state,
                        appListState = appListState,
                        timeFormatter = timeFormatter,
                        dateFormatter = dateFormatter,
                        modifier = Modifier.fillMaxWidth(),
                        onLaunchApp = onLaunchApp,
                        onCloseDrawer = onCloseDrawer,
                        onShowWeatherForecast = onShowWeatherForecast,
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                if (state.mediaState != null) {
                    MediaControlCard(
                        state = state.mediaState,
                        onPlayPause = onMediaPlayPause,
                        onNext = onMediaNext,
                        onPrevious = onMediaPrevious
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (!state.isNotificationListenerEnabled && state.launcherStatusChecked) {
                    NotificationAccessPrompt(onOpenSettings = onOpenNotificationSettings)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(drawerCloseOnPullConnection)
            ) {
                MainContentArea(
                    state = state,
                    appListState = appListState,
                    interactingLetter = interactingLetter,
                    onLaunchApp = onLaunchApp,
                    onLongPressApp = onLongPressApp,
                    onCloseDrawer = onCloseDrawer,
                    onOpenWallpaperPicker = onOpenWallpaperPicker,
                    onOpenSettings = onOpenSettings
                )
            }
            
            if (!state.drawerOpen && state.launcherStatusChecked && !state.isLauncherDefault) {
                LauncherOnboardingCard(
                    dismissed = state.launcherOnboardingDismissed,
                    onRequestLauncherRole = onRequestLauncherRole,
                    onOpenLauncherSettings = onOpenLauncherSettings,
                    onDismiss = onDismissLauncherOnboarding
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        val sidebarPadding = (28 + 228).dp
        
        AlphabetSidebar(
            onLetterSelected = onLetterSelected,
            onBackToFavorites = onCloseDrawer,
            availableLetters = remember(state.sectionIndexes) {
                state.sectionIndexes.keys.filter { it in 'A'..'Z' || it == '#' }.sortedBy { if (it == '#') '{' else it }
            },
            isDrawerOpen = state.drawerOpen,
            isVisible = false,
            selectedIndex = sidebarSelectedIndex,
            onSelectedIndexChange = { sidebarSelectedIndex = it },
            onInteractionStarted = { letter -> onLetterSelected(letter) },
            onInteractionEnded = { interactingLetter = null },
            hapticsEnabled = state.hapticsEnabled,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .padding(top = sidebarPadding, bottom = 28.dp)
        )
        AlphabetSidebar(
            onLetterSelected = onLetterSelected,
            onBackToFavorites = onCloseDrawer,
            availableLetters = remember(state.sectionIndexes) {
                state.sectionIndexes.keys.filter { it in 'A'..'Z' || it == '#' }.sortedBy { if (it == '#') '{' else it }
            },
            isDrawerOpen = state.drawerOpen,
            selectedIndex = sidebarSelectedIndex,
            onSelectedIndexChange = { sidebarSelectedIndex = it },
            onInteractionStarted = { letter -> onLetterSelected(letter) },
            onInteractionEnded = { interactingLetter = null },
            showVariablePadding = true,
            hapticsEnabled = state.hapticsEnabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .padding(top = sidebarPadding, bottom = 28.dp)
        )

        SwipeUpPanelOverlay(
            visible = state.swipeUpPanelOpen,
            query = state.searchQuery,
            filteredApps = state.filteredApps,
            onLaunchApp = onLaunchApp,
            onLongPressApp = onLongPressApp,
            appIconSize = state.appIconSizeDp,
            widgetViews = widgetViews,
            widgetHeights = state.widgetHeights,
            onSearchChange = onSearchChange,
            onAddWidget = onAddWidget,
            onRemoveWidget = onRemoveWidget,
            onMoveWidget = onMoveWidget,
            onResizeWidget = onResizeWidget,
            onConfigureWidget = onConfigureWidget,
            onClose = onCloseSwipeUpPanel
        )

        if (state.showWeatherForecast && state.weather != null) {
            FozBottomSheet(onDismiss = onDismissWeatherForecast) {
                WeatherForecastContent(weather = state.weather)
            }
        }

        state.selectedApp?.let { selectedApp ->
            AppActionDialog(
                selectedApp = selectedApp,
                pinnedPackageNames = state.pinnedPackageNames,
                hiddenPackageNames = state.hiddenApps,
                shortcuts = state.selectedAppShortcuts,
                onDismiss = onDismissAppActions,
                onOpenAppInfo = onOpenAppInfo,
                onUninstallApp = onUninstallApp,
                onTogglePinned = onTogglePinned,
                onMovePinned = onMovePinned,
                onLaunchShortcut = onLaunchShortcut,
                onRenameApp = { appToRename = it },
                onHideApp = { onHideApp(it, !state.hiddenApps.contains(it.packageName)) },
                onChangeIcon = { appToSelectIcon = it },
                isIconPackActive = state.iconPackPackageName != null
            )
        }

        appToRename?.let { app ->
            AppRenameDialog(
                currentName = app.name,
                onConfirm = { newName ->
                    onRenameApp(app, newName.ifBlank { null })
                    appToRename = null
                    onDismissAppActions()
                },
                onDismiss = { appToRename = null }
            )
        }

        appToSelectIcon?.let { app ->
            if (state.iconPackPackageName != null) {
                IconPickerModal(
                    iconPackPackageName = state.iconPackPackageName,
                    onSelect = { iconName ->
                        onSetCustomIcon(app, iconName)
                        appToSelectIcon = null
                        onDismissAppActions()
                    },
                    onDismiss = { appToSelectIcon = null }
                )
            }
        }
    }
}

@Composable
fun AppRenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Rename App", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("App Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    androidx.compose.material3.Button(onClick = { onConfirm(name) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun IconPickerModal(
    iconPackPackageName: String,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val viewModel: com.example.foz.ui.LauncherViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var iconNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(iconPackPackageName) {
        iconNames = viewModel.getIconPackDrawables(iconPackPackageName)
    }
    
    val filteredIcons = remember(iconNames, searchQuery) {
        if (searchQuery.isBlank()) iconNames else iconNames.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Pick Icon", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search icons...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(64.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Surface(
                            onClick = { onSelect(null) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                Text("Default", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    items(filteredIcons.size) { index ->
                        val iconName = filteredIcons[index]
                        val icon = remember(iconName) { viewModel.loadPackIcon(iconPackPackageName, iconName) }
                        Surface(
                            onClick = { onSelect(iconName) },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                if (icon != null) {
                                    com.example.foz.ui.applist.AppIcon(
                                        drawable = icon,
                                        contentDescription = iconName,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}


@Composable
private fun MainContentArea(
    state: LauncherUiState,
    appListState: androidx.compose.foundation.lazy.LazyListState,
    interactingLetter: Char?,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onCloseDrawer: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.drawerOpen) {
            AppDrawerList(
                state = state,
                appListState = appListState,
                interactingLetter = interactingLetter,
                onLaunchApp = onLaunchApp,
                onLongPressApp = onLongPressApp,
                onCloseDrawer = onCloseDrawer,
                onOpenWallpaperPicker = onOpenWallpaperPicker,
                onOpenSettings = onOpenSettings
            )
        } else {
            FavoritesList(
                state = state,
                onLaunchApp = onLaunchApp,
                onLongPressApp = onLongPressApp
            )
        }
    }
}

@Composable
private fun AppDrawerList(
    state: LauncherUiState,
    appListState: androidx.compose.foundation.lazy.LazyListState,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onCloseDrawer: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onOpenSettings: () -> Unit,
    interactingLetter: Char?
) {
    val configuration = LocalConfiguration.current
    val viewportHeight = configuration.screenHeightDp.dp - 56.dp
    
    LazyColumn(
        state = appListState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = viewportHeight / 2,
            bottom = viewportHeight / 2
        )
    ) {
        itemsIndexed(state.filteredApps, key = { _, app -> app.packageName }) { _, app ->
            val isVisible = interactingLetter == null || app.name.startsWith(interactingLetter, ignoreCase = true)
            AppListItem(
                app = app,
                iconSize = state.appIconSizeDp,
                onClick = {
                    if (isVisible) {
                        onLaunchApp(app)
                        onCloseDrawer()
                    }
                },
                onLongClick = { if (isVisible) onLongPressApp(app) },
                modifier = Modifier.graphicsLayer { alpha = if (isVisible) 1f else 0f }
            )
        }
        item {
            AnimatedVisibility(visible = interactingLetter == null) {
                DrawerQuickActions(
                    onOpenWallpaperPicker = onOpenWallpaperPicker,
                    onOpenSettings = onOpenSettings,
                    state = state
                )
            }
        }
    }
}

@Composable
private fun DrawerQuickActions(
    onOpenWallpaperPicker: () -> Unit,
    onOpenSettings: () -> Unit,
    state: LauncherUiState,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 10.dp)
    ) {
        CustomButton(
            label = "Change wallpaper",
            onClick = onOpenWallpaperPicker,
            icon = Icons.Default.Wallpaper,
            iconSize = state.appIconSizeDp
        )
        CustomButton(
            label = "Settings",
            onClick = onOpenSettings,
            icon = Icons.Default.Settings,
            iconSize = state.appIconSizeDp
        )
    }
}

@Composable
private fun FavoritesList(
    state: LauncherUiState,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit
) {
    if (state.pinnedApps.isNotEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.pinnedApps.forEach { app ->
                    FavoriteAppItem(
                        app = app,
                        iconSize = state.appIconSizeDp,
                        onClick = { onLaunchApp(app) },
                        onLongClick = { onLongPressApp(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeUpPanelOverlay(
    visible: Boolean,
    query: String,
    filteredApps: List<AppInfo>,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    appIconSize: Int,
    widgetViews: List<Pair<Int, AppWidgetHostView>>,
    widgetHeights: Map<Int, Int>,
    onSearchChange: (String) -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onResizeWidget: (Int, Int) -> Unit,
    onConfigureWidget: (Int) -> Unit,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(260)) { it },
        exit = slideOutVertically(animationSpec = tween(260)) { it }
    ) {
        Surface(
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            SwipeUpPanel(
                query = query,
                onQueryChange = onSearchChange,
                filteredApps = filteredApps,
                onLaunchApp = onLaunchApp,
                onLongPressApp = onLongPressApp,
                appIconSize = appIconSize,
                widgetViews = widgetViews,
                widgetHeights = widgetHeights,
                onAddWidget = onAddWidget,
                onRemoveWidget = onRemoveWidget,
                onMoveWidget = onMoveWidget,
                onResizeWidget = onResizeWidget,
                onConfigureWidget = onConfigureWidget,
                onClose = onClose
            )
        }
    }
}

@Preview(showSystemUi = true, device = "spec:width=411dp,height=891dp")
@Composable
fun HomeScreenPreview() {
    val mockAppA = AppInfo(
        name = "App A",
        packageName = "com.example.a",
        icon = ContextCompat.getDrawable(LocalContext.current, com.example.foz.R.drawable.ic_launcher_foreground)!!,
        className = "com.example.sample.MainActivity",
    )
    val mockAppB = AppInfo(
        name = "App B",
        packageName = "com.example.b",
        icon = ContextCompat.getDrawable(LocalContext.current, com.example.foz.R.drawable.ic_launcher_foreground)!!,
        className = "com.example.sample.MainActivity",
    )

    val mockState = LauncherUiState(
        allApps = listOf(mockAppA, mockAppB),
        filteredApps = listOf(mockAppA, mockAppB),
        pinnedApps = listOf(mockAppA),
        initialOnboardingCompleted = true,
        drawerOpen = true,
        sectionIndexes = mapOf('A' to 0, 'B' to 1)
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
            onRenameApp = { _, _ -> /* TODO */ },
            onHideApp = { _, _ -> /* TODO */ },
            onSetCustomIcon = { _, _ -> /* TODO */ },
            onAddWidget = { /* TODO */ },
            onRemoveWidget = { /* TODO */ },
            onMoveWidget = { _, _ -> /* TODO */ },
            onResizeWidget = { _, _ -> /* TODO */ },
            onConfigureWidget = { _ -> /* TODO */ },
            onOpenWallpaperPicker = { /* TODO */ },
            onRequestLauncherRole = { /* TODO */ },
            onOpenLauncherSettings = { /* TODO */ },
            onDismissLauncherOnboarding = { /* TODO */ },
            onToggleOnboardingFavorite = { /* TODO */ },
            onCompleteInitialOnboarding = { /* TODO */ },
            onOpenSettings = { /* TODO */ },
            onMediaPlayPause = { /* TODO */ },
            onMediaNext = { /* TODO */ },
            onMediaPrevious = { /* TODO */ },
            onOpenNotificationSettings = { /* TODO */ }
        )
    }
}

@Composable
fun NotificationAccessPrompt(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enable Media Controls",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Grant Foz permission to show what's playing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.Button(
                onClick = onOpenSettings,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Grant Permission", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
