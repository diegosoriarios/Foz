package com.example.foz.ui.home

import android.appwidget.AppWidgetHostView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.foz.R
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.applist.AlphabetSidebar
import com.example.foz.ui.components.FozBottomSheet
import com.example.foz.ui.home.components.AppActionDialog
import com.example.foz.ui.home.components.AppListItem
import com.example.foz.ui.home.components.AddToFolderSheet
import com.example.foz.ui.home.components.AgendaContent
import com.example.foz.ui.home.components.BreakReminderCard
import com.example.foz.ui.home.components.CalendarPreviewCard
import com.example.foz.ui.home.components.FolderActionsSheet
import com.example.foz.ui.home.components.FolderContent
import com.example.foz.ui.home.components.FolderItem
import com.example.foz.ui.home.components.CustomButton
import com.example.foz.ui.home.components.DrawerItem
import com.example.foz.ui.home.components.FavoriteAppItem
import com.example.foz.ui.home.components.HomeHeader
import com.example.foz.ui.home.components.MediaControlCard
import com.example.foz.ui.home.components.NotificationSummaryCard
import com.example.foz.ui.home.components.SuggestedAppsRow
import com.example.foz.ui.home.components.WeatherForecastContent
import com.example.foz.ui.home.components.WidgetPanel
import com.example.foz.ui.home.components.buildDrawerItems
import com.example.foz.ui.home.components.buildLazySectionIndexes
import com.example.foz.ui.home.onboarding.InitialOnboardingScreen
import com.example.foz.ui.home.onboarding.LauncherDefaultSheet
import com.example.foz.ui.home.panels.SwipeUpPanel
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val FavoritesEndPadding = 32.dp
private val AlphabetGestureWidth = 56.dp
private val AlphabetContentEndPadding = 8.dp
private val SwipeZoneEdgeInset = 64.dp

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    onSetAppToRename: (AppInfo?) -> Unit,
    onHideApp: (AppInfo, Boolean) -> Unit,
    onSetCustomIcon: (AppInfo, String?) -> Unit,
    onSetAppToSelectIcon: (AppInfo?) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onResizeWidget: (Int, Int) -> Unit,
    onConfigureWidget: (Int) -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onRequestLauncherRole: () -> Unit,
    onDismissLauncherOnboarding: () -> Unit,
    onToggleOnboardingFavorite: (AppInfo) -> Unit,
    onCompleteInitialOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onMediaDismiss: () -> Unit,
    onToggleDebugNotifications: () -> Unit,
    onShowAppNotifications: (String?) -> Unit,
    onDismissNotification: (String) -> Unit,
    onDismissAllNotifications: (String) -> Unit,
    onTriggerNotificationAction: (com.example.foz.model.NotificationActionModel, String?) -> Unit,
    onTriggerNotificationContent: (com.example.foz.model.NotificationModel) -> Unit,
    onMuteApp: (AppInfo, Boolean) -> Unit,
    onOpenContact: (com.example.foz.model.ContactInfo) -> Unit,
    onSearchWeb: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismissBreakReminder: () -> Unit,
    onClearError: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onShowWeatherForecast: () -> Unit = {},
    onDismissWeatherForecast: () -> Unit = {},
    onShowAgenda: () -> Unit = {},
    onDismissAgenda: () -> Unit = {},
    onRequestCalendarPermission: () -> Unit = {},
    onOpenFolder: (String) -> Unit = {},
    onDismissFolder: () -> Unit = {},
    onShowFolderActions: (String?) -> Unit = {},
    onRenameFolder: (String, String) -> Unit = { _, _ -> },
    onDeleteFolder: (String) -> Unit = {},
    onMoveFolder: (String, Int) -> Unit = { _, _ -> },
    onAddAppToFolder: (AppInfo, String) -> Unit = { _, _ -> },
    onRemoveAppFromFolder: (AppInfo, String) -> Unit = { _, _ -> },
    onCreateFolder: (String, AppInfo?) -> Unit = { _, _ -> },
    onShowFolderPicker: (AppInfo?) -> Unit = {},
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
    val drawerItems = remember(state.filteredApps) { buildDrawerItems(state.filteredApps) }
    val lazySectionIndexes = remember(drawerItems) { buildLazySectionIndexes(drawerItems) }
    val coroutineScope = rememberCoroutineScope()
    var interactingLetter by remember { mutableStateOf<Char?>(null) }
    var sidebarSelectedIndex by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val haptics = LocalHapticFeedback.current
    val swipeStartedInCenter = remember { booleanArrayOf(false) }
    
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
            state.folderPickerForApp != null -> onShowFolderPicker(null)
            state.showFolderActions != null -> onShowFolderActions(null)
            state.openFolderId != null -> onDismissFolder()
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

    LaunchedEffect(state.requestedSectionLetter, lazySectionIndexes, state.drawerOpen) {
        if (!state.drawerOpen) return@LaunchedEffect
        val letter = state.requestedSectionLetter ?: return@LaunchedEffect
        lazySectionIndexes[letter]?.let { index ->
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
                lazySectionIndexes[letter]?.let { index ->
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

    val openSearchLabel = stringResource(R.string.acc_open_search_widgets)
    val openNotificationsLabel = stringResource(R.string.acc_open_notifications)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .semantics {
                if (!state.drawerOpen && !state.swipeUpPanelOpen) {
                    customActions = listOfNotNull(
                        if (state.swipeUpEnabled) CustomAccessibilityAction(openSearchLabel) {
                            onSwipeUp()
                            true
                        } else null,
                        if (state.swipeDownEnabled) CustomAccessibilityAction(openNotificationsLabel) {
                            onSwipeDown()
                            true
                        } else null
                    )
                }
            }
            .pointerInput(Unit) {
                val edgeInsetPx = SwipeZoneEdgeInset.toPx()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.changedToDown() }
                        if (down != null && event.changes.count { it.pressed } == 1) {
                            swipeStartedInCenter[0] =
                                down.position.x in edgeInsetPx..(size.width - edgeInsetPx)
                        }
                    }
                }
            }
            .pointerInput(state.drawerOpen, state.swipeUpPanelOpen, state.swipeDownEnabled, state.swipeUpEnabled) {
                if (state.drawerOpen || state.swipeUpPanelOpen) return@pointerInput
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        if (swipeStartedInCenter[0]) {
                            if (state.swipeUpEnabled && totalDrag < -120f) {
                                if (state.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSwipeUp()
                            } else if (state.swipeDownEnabled && totalDrag > 120f) {
                                if (state.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSwipeDown()
                            }
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
        
        val homeScrollState = rememberScrollState()
        
        val homeSwipeConnection = remember(state.swipeUpEnabled, state.swipeDownEnabled, state.hapticsEnabled, homeScrollState.value, homeScrollState.maxValue) {
            object : NestedScrollConnection {
                var accumulated = 0f
                var fired = false
                var overscrollAccumulated = 0f
                var overscrollFired = false
                
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source == NestedScrollSource.UserInput) {
                        if (!swipeStartedInCenter[0]) {
                            accumulated = 0f
                            fired = false
                            overscrollAccumulated = 0f
                            overscrollFired = false
                            return Offset.Zero
                        }
                        val delta = available.y
                        val isAtTop = homeScrollState.value == 0
                        val isAtBottom = homeScrollState.value == homeScrollState.maxValue
                        
                        if (!fired) {
                            if (accumulated != 0f && (delta == 0f || (delta > 0f) != (accumulated > 0f))) {
                                accumulated = 0f
                                fired = false
                            }
                            accumulated += delta
                            if (accumulated < -120f || accumulated > 120f) {
                                fired = true
                                if (state.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (accumulated < 0f) {
                                    if (isAtTop) onSwipeUp()
                                } else {
                                    if (isAtBottom) onSwipeDown()
                                }
                            }
                        }
                        
                        if (isAtBottom && !overscrollFired && delta > 0f && consumed.y < 1f) {
                            overscrollAccumulated += delta
                            if (overscrollAccumulated > 120f) {
                                overscrollFired = true
                                if (state.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSwipeDown()
                            }
                        } else if (delta <= 0f || consumed.y >= 1f || !isAtBottom) {
                            overscrollAccumulated = 0f
                            overscrollFired = false
                        }
                    }
                    return Offset.Zero
                }
                
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    accumulated = 0f
                    fired = false
                    overscrollAccumulated = 0f
                    overscrollFired = false
                    return Velocity.Zero
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
        ) {
            if (!state.drawerOpen && !state.swipeUpPanelOpen) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    HomeHeader(
                        state = state,
                        appListState = appListState,
                        timeFormatter = timeFormatter,
                        dateFormatter = dateFormatter,
                        drawerItems = drawerItems,
                        modifier = Modifier.fillMaxWidth(),
                        onLaunchApp = onLaunchApp,
                        onCloseDrawer = onCloseDrawer,
                        onShowWeatherForecast = onShowWeatherForecast,
                        onShowAgenda = onShowAgenda,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(homeSwipeConnection)
                        .verticalScroll(homeScrollState),
                ) {
                if (widgetViews.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    WidgetPanel(
                        widgetViews = widgetViews,
                        widgetHeights = state.widgetHeights,
                        panelHeightDp = state.widgetPanelHeightDp,
                        onRemoveWidget = onRemoveWidget,
                        onMoveWidget = onMoveWidget,
                        onResizeWidget = onResizeWidget,
                        onConfigureWidget = onConfigureWidget,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                if (state.mediaCardEnabled && state.mediaState != null && !state.mediaDismissed &&
                    (state.mediaState.isPlaying || state.mediaState.title != null || state.mediaState.artist != null)) {
                    MediaControlCard(
                        state = state.mediaState,
                        onPlayPause = onMediaPlayPause,
                        onNext = onMediaNext,
                        onPrevious = onMediaPrevious,
                        onDismiss = onMediaDismiss
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (!state.isNotificationListenerEnabled && state.launcherStatusChecked) {
                    NotificationAccessPrompt(onOpenSettings = onOpenNotificationSettings)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (state.showBreakReminder) {
                    Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                        BreakReminderCard(
                            usageTodayMs = state.usageTodayMs,
                            onDismiss = onDismissBreakReminder
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (state.suggestedApps.isNotEmpty()) {
                    Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                        SuggestedAppsRow(
                            apps = state.suggestedApps,
                            onLaunchApp = onLaunchApp,
                            onLongPressApp = onLongPressApp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (state.calendarPreviewEnabled &&
                    state.isCalendarPermissionGranted &&
                    state.calendarEvents.isNotEmpty()
                ) {
                    Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                        CalendarPreviewCard(
                            events = state.calendarEvents.take(state.calendarPreviewCount),
                            onOpenAgenda = onShowAgenda
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (state.notificationSummaryEnabled && state.notificationSummary.isNotEmpty()) {
                    Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                        NotificationSummaryCard(
                            summary = state.notificationSummary,
                            apps = state.allApps,
                            onShowAppNotifications = onShowAppNotifications
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                    FavoritesList(
                        state = state,
                        onLaunchApp = onLaunchApp,
                        onLongPressApp = onLongPressApp,
                        onShowAppNotifications = onShowAppNotifications,
                        onOpenFolder = onOpenFolder,
                        onShowFolderActions = onShowFolderActions
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                }
            } else if (state.drawerOpen) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(drawerCloseOnPullConnection)
                ) {
                    AppDrawerList(
                        state = state,
                        appListState = appListState,
                        drawerItems = drawerItems,
                        interactingLetter = interactingLetter,
                        onLaunchApp = onLaunchApp,
                        onLongPressApp = onLongPressApp,
                        onShowAppNotifications = onShowAppNotifications,
                        onCloseDrawer = onCloseDrawer,
                        onOpenWallpaperPicker = onOpenWallpaperPicker,
                        onOpenSettings = onOpenSettings
                    )
                }
            }
        }

        val sidebarPadding = (28 + 212).dp
        
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
                .padding(start = 8.dp, top = sidebarPadding, bottom = 28.dp)
        )
        AlphabetSidebar(
            onLetterSelected = onLetterSelected,
            onBackToFavorites = onCloseDrawer,
            availableLetters = remember(state.sectionIndexes) {
                state.sectionIndexes.keys.filter { it in 'A'..'Z' || it == '#' }.sortedBy { if (it == '#') '{' else it }
            },
            isDrawerOpen = state.drawerOpen,
            isVisible = !state.swipeUpPanelOpen,
            selectedIndex = sidebarSelectedIndex,
            onSelectedIndexChange = { sidebarSelectedIndex = it },
            onInteractionStarted = { letter -> onLetterSelected(letter) },
            onInteractionEnded = { interactingLetter = null },
            showVariablePadding = true,
            hapticsEnabled = state.hapticsEnabled,
            gestureWidth = AlphabetGestureWidth,
            contentEndPadding = AlphabetContentEndPadding,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .padding(top = sidebarPadding, bottom = 28.dp)
        )

        SwipeUpPanelOverlay(
            visible = state.swipeUpPanelOpen,
            query = state.searchQuery,
            filteredApps = state.filteredApps,
            searchContacts = state.searchContacts,
            instantAnswer = state.instantAnswer,
            instantAnswerLoading = state.instantAnswerLoading,
            onLaunchApp = onLaunchApp,
            onLongPressApp = onLongPressApp,
            onOpenContact = onOpenContact,
            onSearchWeb = onSearchWeb,
            onOpenUrl = onOpenUrl,
            appIconSize = state.appIconSizeDp,
            onSearchChange = onSearchChange,
            onClose = onCloseSwipeUpPanel
        )

        if (state.launcherStatusChecked && !state.isLauncherDefault && !state.launcherOnboardingDismissed) {
            LauncherDefaultSheet(
                onSetAsDefault = onRequestLauncherRole,
                onDismiss = onDismissLauncherOnboarding
            )
        }

        if (state.showWeatherForecast && state.weatherEnabled && state.weather != null) {
            FozBottomSheet(onDismiss = onDismissWeatherForecast) {
                WeatherForecastContent(weather = state.weather)
            }
        }

        if (state.showAgenda) {
            FozBottomSheet(onDismiss = onDismissAgenda) {
                AgendaContent(
                    events = state.calendarEvents,
                    isPermissionGranted = state.isCalendarPermissionGranted,
                    onRequestPermission = onRequestCalendarPermission
                )
            }
        }

        val openFolderId = state.openFolderId
        if (openFolderId != null) {
            state.folders.find { it.id == openFolderId }?.let { folder ->
                FozBottomSheet(onDismiss = onDismissFolder) {
                    FolderContent(
                        folder = folder,
                        apps = state.allApps,
                        onLaunchApp = onLaunchApp,
                        onLongPressApp = onLongPressApp,
                        onRemoveApp = { app -> onRemoveAppFromFolder(app, folder.id) },
                        onDismiss = onDismissFolder
                    )
                }
            }
        }

        val folderActionsId = state.showFolderActions
        if (folderActionsId != null) {
            val actionFolderIndex = state.folders.indexOfFirst { it.id == folderActionsId }
            if (actionFolderIndex != -1) {
                FozBottomSheet(onDismiss = { onShowFolderActions(null) }) {
                    FolderActionsSheet(
                        folder = state.folders[actionFolderIndex],
                        folderIndex = actionFolderIndex,
                        folderCount = state.folders.size,
                        onRename = { name -> onRenameFolder(folderActionsId, name) },
                        onDelete = { onDeleteFolder(folderActionsId) },
                        onMove = { direction -> onMoveFolder(folderActionsId, direction) },
                        onDismiss = { onShowFolderActions(null) }
                    )
                }
            }
        }

        state.folderPickerForApp?.let { pickerApp ->
            FozBottomSheet(onDismiss = { onShowFolderPicker(null) }) {
                AddToFolderSheet(
                    folders = state.folders,
                    onPick = { folder -> onAddAppToFolder(pickerApp, folder.id) },
                    onCreate = { name -> onCreateFolder(name, pickerApp) },
                    onDismiss = { onShowFolderPicker(null) }
                )
            }
        }

        state.selectedApp?.let { selectedApp ->
            val appFolder = state.folders.find { it.packageNames.contains(selectedApp.packageName) }
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
                onRenameApp = { onSetAppToRename(it) },
                onHideApp = { onHideApp(it, !state.hiddenApps.contains(it.packageName)) },
                onChangeIcon = { onSetAppToSelectIcon(it) },
                onToggleMute = { onMuteApp(it, !state.mutedApps.contains(it.packageName)) },
                isMuted = state.mutedApps.contains(selectedApp.packageName),
                isIconPackActive = state.iconPackPackageName != null,
                isInFolder = appFolder != null,
                onAddToFolder = { app ->
                    onDismissAppActions()
                    onShowFolderPicker(app)
                },
                onRemoveFromFolder = { app ->
                    appFolder?.let { folder -> onRemoveAppFromFolder(app, folder.id) }
                    onDismissAppActions()
                }
            )
        }

        val appNotificationsPackage = state.showAppNotificationsPackage
        if (appNotificationsPackage != null) {
            state.notificationsByPackage[appNotificationsPackage]?.let { notifications ->
                val appName = state.allApps.find { it.packageName == appNotificationsPackage }?.name ?: appNotificationsPackage
                AppNotificationsDialog(
                    appName = appName,
                    packageName = appNotificationsPackage,
                    notifications = notifications,
                    quickRepliesEnabled = state.quickRepliesEnabled,
                    onDismiss = { onShowAppNotifications(null) },
                    onDismissNotification = onDismissNotification,
                    onDismissAll = { 
                        onDismissAllNotifications(appNotificationsPackage)
                        onShowAppNotifications(null)
                    },
                    onTriggerAction = onTriggerNotificationAction,
                    onTriggerContent = onTriggerNotificationContent
                )
            }
        }

        state.errorMessage?.let { message ->
            ErrorDialog(
                message = message,
                onDismiss = onClearError
            )
        }

        state.appToRename?.let { app ->
            AppRenameDialog(
                currentName = app.name,
                onConfirm = { newName ->
                    onRenameApp(app, newName.ifBlank { null })
                    onSetAppToRename(null)
                    onDismissAppActions()
                },
                onDismiss = { onSetAppToRename(null) }
            )
        }

        state.appToSelectIcon?.let { app ->
            if (state.iconPackPackageName != null) {
                IconPickerModal(
                    iconPackPackageName = state.iconPackPackageName,
                    onSelect = { iconName ->
                        onSetCustomIcon(app, iconName)
                        onSetAppToSelectIcon(null)
                        onDismissAppActions()
                    },
                    onDismiss = { onSetAppToSelectIcon(null) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    com.example.foz.ui.components.FozBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(24.dp).padding(bottom = 16.dp)) {
            Text(text = stringResource(R.string.dialog_rename_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.dialog_rename_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
                androidx.compose.material3.Button(onClick = { onConfirm(name) }) {
                    Text(stringResource(R.string.dialog_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    com.example.foz.ui.components.FozBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.8f)) {
            Text(text = stringResource(R.string.dialog_pick_icon_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.dialog_search_icons_hint)) },
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
                            Text(stringResource(R.string.dialog_icon_default), style = MaterialTheme.typography.labelSmall)
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
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    }
}


@Composable
private fun AppDrawerList(
    state: LauncherUiState,
    appListState: androidx.compose.foundation.lazy.LazyListState,
    drawerItems: List<DrawerItem>,
    interactingLetter: Char?,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onShowAppNotifications: (String) -> Unit,
    onCloseDrawer: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val viewportHeight = configuration.screenHeightDp.dp - 56.dp

    LazyColumn(
        state = appListState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = viewportHeight / 3,
            bottom = viewportHeight / 2
        )
    ) {
        itemsIndexed(
            drawerItems,
            key = { _, item ->
                when (item) {
                    is DrawerItem.Section -> "section_${item.letter}"
                    is DrawerItem.App -> item.app.packageName
                }
            }
        ) { _, item ->
            when (item) {
                is DrawerItem.Section -> SectionHeaderItem(
                    letter = item.letter,
                    isVisible = interactingLetter == null || interactingLetter == item.letter
                )
                is DrawerItem.App -> {
                    val app = item.app
                    val isVisible = interactingLetter == null || app.name.startsWith(interactingLetter, ignoreCase = true)
                    val notifications = state.notificationSummary[app.packageName] ?: emptyList()
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
                        notificationCount = notifications.size,
                        onNotificationClick = { onShowAppNotifications(app.packageName) },
                        modifier = Modifier.graphicsLayer { alpha = if (isVisible) 1f else 0f }
                    )
                }
            }
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
private fun SectionHeaderItem(
    letter: Char,
    isVisible: Boolean
) {
    Text(
        text = letter.toString(),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.18f),
        modifier = Modifier
            .padding(start = 4.dp, top = 24.dp, bottom = 2.dp)
            .graphicsLayer { alpha = if (isVisible) 1f else 0f }
    )
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
            label = stringResource(R.string.quick_action_wallpaper),
            onClick = onOpenWallpaperPicker,
            icon = Icons.Default.Wallpaper,
            iconSize = state.appIconSizeDp
        )
        CustomButton(
            label = stringResource(R.string.quick_action_settings),
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
    onLongPressApp: (AppInfo) -> Unit,
    onShowAppNotifications: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onShowFolderActions: (String?) -> Unit
) {
    if (state.pinnedApps.isNotEmpty() || state.folders.isNotEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = FavoritesEndPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.pinnedApps.forEach { app ->
                    val notifications = state.notificationSummary[app.packageName] ?: emptyList()
                    FavoriteAppItem(
                        app = app,
                        iconSize = state.appIconSizeDp,
                        onClick = { onLaunchApp(app) },
                        onLongClick = { onLongPressApp(app) },
                        notificationCount = notifications.size,
                        onNotificationClick = { onShowAppNotifications(app.packageName) }
                    )
                }
                state.folders.forEach { folder ->
                    FolderItem(
                        folder = folder,
                        apps = state.allApps,
                        iconSize = state.appIconSizeDp,
                        onClick = { onOpenFolder(folder.id) },
                        onLongClick = { onShowFolderActions(folder.id) }
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
    searchContacts: List<com.example.foz.model.ContactInfo>,
    instantAnswer: com.example.foz.model.InstantAnswer?,
    instantAnswerLoading: Boolean,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onOpenContact: (com.example.foz.model.ContactInfo) -> Unit,
    onSearchWeb: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    appIconSize: Int,
    onSearchChange: (String) -> Unit,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(260)) { it },
        exit = slideOutVertically(animationSpec = tween(260)) { it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                SwipeUpPanel(
                    query = query,
                    onQueryChange = onSearchChange,
                    filteredApps = filteredApps,
                    searchContacts = searchContacts,
                    instantAnswer = instantAnswer,
                    instantAnswerLoading = instantAnswerLoading,
                    onLaunchApp = onLaunchApp,
                    onLongPressApp = onLongPressApp,
                    onOpenContact = onOpenContact,
                    onSearchWeb = onSearchWeb,
                    onOpenAnswerUrl = onOpenUrl,
                    appIconSize = appIconSize,
                    onClose = onClose
                )
            }
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
            onSetAppToRename = { /* TODO */ },
            onHideApp = { _, _ -> /* TODO */ },
            onSetCustomIcon = { _, _ -> /* TODO */ },
            onSetAppToSelectIcon = { /* TODO */ },
            onRemoveWidget = { /* TODO */ },
            onMoveWidget = { _, _ -> /* TODO */ },
            onResizeWidget = { _, _ -> /* TODO */ },
            onConfigureWidget = { _ -> /* TODO */ },
            onOpenWallpaperPicker = { /* TODO */ },
            onRequestLauncherRole = { /* TODO */ },
            onDismissLauncherOnboarding = { /* TODO */ },
            onToggleOnboardingFavorite = { /* TODO */ },
            onCompleteInitialOnboarding = { /* TODO */ },
            onOpenSettings = { /* TODO */ },
            onMediaPlayPause = { /* TODO */ },
            onMediaNext = { /* TODO */ },
            onMediaPrevious = { /* TODO */ },
            onMediaDismiss = { /* TODO */ },
            onToggleDebugNotifications = { /* TODO */ },
            onShowAppNotifications = { /* TODO */ },
            onDismissNotification = { /* TODO */ },
            onDismissAllNotifications = { /* TODO */ },
            onTriggerNotificationAction = { _, _ -> /* TODO */ },
            onTriggerNotificationContent = { /* TODO */ },
            onMuteApp = { _, _ -> /* TODO */ },
            onOpenContact = { /* TODO */ },
            onSearchWeb = { /* TODO */ },
            onOpenUrl = { /* TODO */ },
            onDismissBreakReminder = { /* TODO */ },
            onClearError = { /* TODO */ },
            onOpenNotificationSettings = { /* TODO */ }
        )
    }
}

@Composable
fun AppNotificationsDialog(
    appName: String,
    packageName: String,
    notifications: List<com.example.foz.model.NotificationModel>,
    quickRepliesEnabled: Boolean,
    onDismiss: () -> Unit,
    onDismissNotification: (String) -> Unit,
    onDismissAll: () -> Unit,
    onTriggerAction: (com.example.foz.model.NotificationActionModel, String?) -> Unit,
    onTriggerContent: (com.example.foz.model.NotificationModel) -> Unit
) {
    var replyingAction by remember { mutableStateOf<com.example.foz.model.NotificationActionModel?>(null) }
    var replyText by remember { mutableStateOf("") }

    val sendReply: () -> Unit = {
        val action = replyingAction
        if (action != null && replyText.isNotBlank()) {
            onTriggerAction(action, replyText)
            replyingAction = null
            replyText = ""
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.IconButton(onClick = onDismissAll) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear All"
                        )
                    }
                }

                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No notifications",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = notifications,
                            key = { it.key }
                        ) { notification ->
                        Surface(
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .clickable { onTriggerContent(notification) }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    if (notification.largeIcon != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = notification.largeIcon.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp).clip(CircleShape).padding(end = 8.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = notification.title ?: "",
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            if (notification.isClearable) {
                                                androidx.compose.material3.IconButton(
                                                    onClick = { onDismissNotification(notification.key) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    androidx.compose.material3.Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Dismiss",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = notification.text ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                val visibleActions = if (quickRepliesEnabled) notification.actions else notification.actions.filter { !it.hasRemoteInput }
                                if (visibleActions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        visibleActions.forEach { action ->
                                            androidx.compose.material3.TextButton(
                                                onClick = {
                                                    if (action.hasRemoteInput) {
                                                        replyingAction = action
                                                        replyText = ""
                                                    } else {
                                                        onTriggerAction(action, null)
                                                    }
                                                },
                                                modifier = Modifier.height(32.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Text(
                                                    text = action.title?.toString() ?: "Action",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                AnimatedVisibility(visible = replyingAction != null) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = {
                                Text(
                                    text = replyingAction?.remoteInputLabel?.toString()
                                        ?: stringResource(R.string.notification_reply_hint),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            androidx.compose.material3.TextButton(onClick = {
                                replyingAction = null
                                replyText = ""
                            }) {
                                Text(stringResource(R.string.dialog_cancel))
                            }
                            androidx.compose.material3.TextButton(
                                onClick = sendReply,
                                enabled = replyText.isNotBlank()
                            ) {
                                Text(stringResource(R.string.notification_reply_send))
                            }
                        }
                    }
                }

                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Error", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(24.dp))
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("OK")
                }
            }
        }
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
                text = stringResource(R.string.notification_access_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.notification_access_desc),
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
                Text(stringResource(R.string.notification_access_grant), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
