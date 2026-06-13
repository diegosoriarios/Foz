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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.applist.AlphabetSidebar
import com.example.foz.ui.home.components.AppActionDialog
import com.example.foz.ui.home.components.AppListItem
import com.example.foz.ui.home.components.CustomButton
import com.example.foz.ui.home.components.FavoriteAppItem
import com.example.foz.ui.home.components.HomeHeader
import com.example.foz.ui.home.onboarding.InitialOnboardingScreen
import com.example.foz.ui.home.onboarding.LauncherOnboardingCard
import com.example.foz.ui.home.panels.SwipeUpPanel
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
    
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val viewportHeight = screenHeight - 56.dp // Screen height minus root vertical padding
    val centerOffset = with(density) { (viewportHeight / 2).toPx().toInt() }
    
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
            appListState.scrollToItem(index, scrollOffset = centerOffset)
        }
        onRequestedSectionConsumed()
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (state.drawerOpen) Color.Black.copy(alpha = 0.6f) else Color.Transparent,
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
                            scrollOffset = centerOffset - (centerOffset / 3)
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
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
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

        val sidebarPadding = (28 + 228 + 24).dp
        
        AlphabetSidebar(
            onLetterSelected = onLetterSelected,
            onBackToFavorites = onCloseDrawer,
            isDrawerOpen = state.drawerOpen,
            isVisible = false,
            onInteractionStarted = { letter -> onLetterSelected(letter) },
            onInteractionEnded = { interactingLetter = null },
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .padding(top = sidebarPadding, bottom = 28.dp)
        )
        AlphabetSidebar(
            onLetterSelected = onLetterSelected,
            onBackToFavorites = onCloseDrawer,
            isDrawerOpen = state.drawerOpen,
            onInteractionStarted = { letter -> onLetterSelected(letter) },
            onInteractionEnded = { interactingLetter = null },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .padding(top = sidebarPadding, bottom = 28.dp)
        )

        SwipeUpPanelOverlay(
            visible = state.swipeUpPanelOpen,
            query = state.searchQuery,
            widgetViews = widgetViews,
            onSearchChange = onSearchChange,
            onAddWidget = onAddWidget,
            onRemoveWidget = onRemoveWidget,
            onClose = onCloseSwipeUpPanel
        )

        state.selectedApp?.let { selectedApp ->
            AppActionDialog(
                selectedApp = selectedApp,
                pinnedPackageNames = state.pinnedPackageNames,
                shortcuts = state.selectedAppShortcuts,
                onDismiss = onDismissAppActions,
                onOpenAppInfo = onOpenAppInfo,
                onUninstallApp = onUninstallApp,
                onTogglePinned = onTogglePinned,
                onMovePinned = onMovePinned,
                onLaunchShortcut = onLaunchShortcut
            )
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
            //top = viewportHeight / 3,
            //bottom = viewportHeight / 2
        )
    ) {
        item {
            Spacer(modifier = Modifier.height(viewportHeight / 2))
        }
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
            DrawerQuickActions(
                onOpenWallpaperPicker = onOpenWallpaperPicker,
                onOpenSettings = onOpenSettings
            )
        }
    }
}

@Composable
private fun DrawerQuickActions(
    onOpenWallpaperPicker: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 10.dp)
    ) {
        CustomButton(label = "Change wallpaper", onClick = onOpenWallpaperPicker)
        CustomButton(label = "Settings", onClick = onOpenSettings)
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
                    .fillMaxWidth(),
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
    widgetViews: List<Pair<Int, AppWidgetHostView>>,
    onSearchChange: (String) -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
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
                widgetViews = widgetViews,
                onAddWidget = onAddWidget,
                onRemoveWidget = onRemoveWidget,
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
        icon = ContextCompat.getDrawable(LocalContext.current, com.example.foz.R.drawable.ic_launcher_background)!!,
        className = "com.example.sample.MainActivity",
    )
    val mockAppB = AppInfo(
        name = "App B",
        packageName = "com.example.b",
        icon = ContextCompat.getDrawable(LocalContext.current, com.example.foz.R.drawable.ic_launcher_background)!!,
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
