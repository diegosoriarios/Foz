package com.example.foz.ui

import android.app.Application
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.foz.data.AppRepository
import com.example.foz.data.PrefsManager
import com.example.foz.data.WeatherRepository
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.model.WeatherModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val appRepository = AppRepository(
        packageManager = application.packageManager,
        launcherApps = application.getSystemService(LauncherApps::class.java)
    )
    private val prefsManager = PrefsManager(application)
    private val appWidgetManager = AppWidgetManager.getInstance(application)
    private val appWidgetHost = AppWidgetHost(application, APP_WIDGET_HOST_ID)
    private val weatherRepository = WeatherRepository()

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private var clockJob: Job? = null

    init {
        observePinnedAndWidgets()
        observeLauncherOnboarding()
        observeInitialOnboarding()
        observeLauncherSettings()
        refreshLauncherRoleStatus()
        refreshApps()
        refreshWeather()
        startClockTicker()
    }

    fun startWidgetListening() {
        appWidgetHost.startListening()
    }

    fun stopWidgetListening() {
        appWidgetHost.stopListening()
    }

    fun refreshApps() {
        viewModelScope.launch {
            val apps = appRepository.getLaunchableApps()
            _uiState.update { state ->
                val filtered = applyQuery(apps, state.searchQuery)
                val pinnedMap = apps.filter { state.pinnedPackageNames.contains(it.packageName) }.associateBy { it.packageName }
                val sortedPinnedApps = state.pinnedPackageNames.mapNotNull { pinnedMap[it] }
                
                state.copy(
                    allApps = apps,
                    filteredApps = filtered,
                    pinnedApps = sortedPinnedApps,
                    sectionIndexes = buildSectionIndexes(filtered)
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = applyQuery(state.allApps, query)
            state.copy(
                searchQuery = query,
                filteredApps = filtered,
                sectionIndexes = buildSectionIndexes(filtered)
            )
        }
    }

    fun openDrawer() {
        _uiState.update { it.copy(drawerOpen = true, swipeUpPanelOpen = false) }
    }

    fun openSwipeUpPanel() {
        _uiState.update { it.copy(swipeUpPanelOpen = true, drawerOpen = false) }
    }

    fun closeSwipeUpPanel() {
        _uiState.update { it.copy(swipeUpPanelOpen = false) }
    }

    fun openDrawerAtLetter(letter: Char) {
        _uiState.update { it.copy(drawerOpen = true, requestedSectionLetter = letter, swipeUpPanelOpen = false) }
    }

    fun clearRequestedSectionLetter() {
        _uiState.update { it.copy(requestedSectionLetter = null) }
    }

    fun closeDrawer() {
        _uiState.update { it.copy(drawerOpen = false, requestedSectionLetter = null, swipeUpPanelOpen = false) }
    }

    fun onAppLongPress(app: AppInfo) {
        _uiState.update { it.copy(selectedApp = app, selectedAppShortcuts = emptyList()) }
        viewModelScope.launch {
            val shortcuts = appRepository.getShortcuts(app.packageName)
            _uiState.update { state ->
                if (state.selectedApp?.packageName == app.packageName) {
                    state.copy(selectedAppShortcuts = shortcuts)
                } else {
                    state
                }
            }
        }
    }

    fun dismissAppActions() {
        _uiState.update { it.copy(selectedApp = null, selectedAppShortcuts = emptyList()) }
    }

    fun resetNavigationIfBlocked() {
        _uiState.update {
            it.copy(
                drawerOpen = false,
                swipeUpPanelOpen = false,
                selectedApp = null,
                selectedAppShortcuts = emptyList(),
                requestedSectionLetter = null
            )
        }
    }

    fun togglePinned(app: AppInfo) {
        viewModelScope.launch {
            val currentlyPinned = _uiState.value.pinnedPackageNames.contains(app.packageName)
            prefsManager.setAppPinned(app.packageName, !currentlyPinned)
        }
    }

    fun movePinned(app: AppInfo, direction: Int) {
        viewModelScope.launch {
            prefsManager.moveAppPinned(app.packageName, direction)
        }
    }

    fun onWallpaperChanged() {
        _uiState.update { state ->
            state.copy(wallpaperChangeToken = state.wallpaperChangeToken + 1)
        }
    }

    fun showNotificationShade() {
        val context = getApplication<Application>()
        try {
            val service = context.getSystemService("statusbar")
            val method = service?.javaClass?.getMethod("expandNotificationsPanel")
            method?.invoke(service)
        } catch (_: Throwable) {
        }
    }

    fun refreshLauncherRoleStatus() {
        _uiState.update { state ->
            state.copy(
                isLauncherDefault = isDefaultLauncher(),
                launcherStatusChecked = true
            )
        }
    }

    fun dismissLauncherOnboarding() {
        viewModelScope.launch {
            prefsManager.setLauncherOnboardingDismissed(true)
        }
    }

    fun toggleOnboardingFavorite(packageName: String) {
        _uiState.update { state ->
            val updated = state.onboardingSelectedPackages.toMutableSet()
            if (updated.contains(packageName)) {
                updated.remove(packageName)
            } else if (updated.size < 8) {
                updated.add(packageName)
            }
            state.copy(onboardingSelectedPackages = updated)
        }
    }

    fun completeInitialOnboarding() {
        val selected = _uiState.value.onboardingSelectedPackages
        if (selected.size < 2 || selected.size > 8) return
        viewModelScope.launch {
            _uiState.value.pinnedPackageNames.forEach { pkg ->
                if (!selected.contains(pkg)) {
                    prefsManager.setAppPinned(pkg, false)
                }
            }
            selected.forEach { pkg ->
                if (!_uiState.value.pinnedPackageNames.contains(pkg)) {
                    prefsManager.setAppPinned(pkg, true)
                }
            }
            prefsManager.setInitialOnboardingCompleted(true)
        }
    }

    fun setClockUse24h(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setClockUse24h(enabled) }
    }

    fun setAppIconSizeDp(sizeDp: Int) {
        viewModelScope.launch { prefsManager.setAppIconSizeDp(sizeDp) }
    }

    fun setSwipeUpEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setSwipeUpEnabled(enabled) }
    }

    fun setSwipeDownEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setSwipeDownEnabled(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefsManager.setThemeMode(mode) }
    }

    fun setShowNotifications(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setShowNotifications(enabled) }
    }

    fun setUsageLimitsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setUsageLimitsEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setHapticsEnabled(enabled) }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            // For now, use mock data - replace with actual API call in production
            val weather = weatherRepository.getMockWeather()
            _uiState.update { it.copy(weather = weather) }
        }
    }

    fun availableWidgets(): List<ComponentName> {
        return appWidgetManager.installedProviders.orEmpty().map { it.provider }
    }

    fun widgetHostViews(): List<Pair<Int, AppWidgetHostView>> {
        return _uiState.value.widgetIds.mapNotNull { widgetId ->
            val info = appWidgetManager.getAppWidgetInfo(widgetId) ?: return@mapNotNull null
            widgetId to appWidgetHost.createView(getApplication(), widgetId, info)
        }
    }

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun addWidget(provider: ComponentName): Boolean {
        val widgetId = appWidgetHost.allocateAppWidgetId()
        val bound = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider)
        if (!bound) {
            appWidgetHost.deleteAppWidgetId(widgetId)
            return false
        }
        addWidgetId(widgetId)
        return true
    }

    fun addWidgetId(widgetId: Int) {
        viewModelScope.launch {
            val updated = _uiState.value.widgetIds.toMutableSet()
            updated.add(widgetId)
            prefsManager.saveWidgetIds(updated)
        }
    }

    fun removeWidgetId(widgetId: Int) {
        viewModelScope.launch {
            val updated = _uiState.value.widgetIds.toMutableSet()
            updated.remove(widgetId)
            prefsManager.saveWidgetIds(updated)
            appWidgetHost.deleteAppWidgetId(widgetId)
        }
    }

    fun deleteWidgetId(widgetId: Int) {
        appWidgetHost.deleteAppWidgetId(widgetId)
    }

    fun appInfoIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    }

    fun uninstallIntent(packageName: String): Intent {
        return Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
        }
    }

    fun startShortcut(shortcut: AppShortcut) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val launcherApps = getApplication<Application>().getSystemService(LauncherApps::class.java) ?: return
        try {
            launcherApps.startShortcut(
                shortcut.packageName,
                shortcut.id,
                null,
                null,
                Process.myUserHandle()
            )
        } catch (_: Throwable) {
        }
    }

    private fun startClockTicker() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(now = LocalDateTime.now()) }
                delay(1000)
            }
        }
    }

    private fun observePinnedAndWidgets() {
        viewModelScope.launch {
            combine(
                prefsManager.pinnedApps,
                prefsManager.widgetIds
            ) { pinned, widgetIds ->
                pinned to widgetIds
            }.collect { (pinned, widgetIds) ->
                _uiState.update { state ->
                    val pinnedMap = state.allApps.filter { pinned.contains(it.packageName) }.associateBy { it.packageName }
                    val sortedPinnedApps = pinned.mapNotNull { pinnedMap[it] }
                    
                    state.copy(
                        pinnedPackageNames = pinned,
                        pinnedApps = sortedPinnedApps,
                        widgetIds = widgetIds
                    )
                }
            }
        }
    }

    private fun observeLauncherOnboarding() {
        viewModelScope.launch {
            prefsManager.launcherOnboardingDismissed.collect { dismissed ->
                _uiState.update { it.copy(launcherOnboardingDismissed = dismissed) }
            }
        }
    }

    private fun observeInitialOnboarding() {
        viewModelScope.launch {
            prefsManager.initialOnboardingCompleted.collect { completed ->
                _uiState.update { state ->
                    state.copy(
                        initialOnboardingCompleted = completed,
                        onboardingSelectedPackages = if (completed) emptySet() else state.pinnedPackageNames.toSet()
                    )
                }
            }
        }
    }

    private fun observeLauncherSettings() {
        viewModelScope.launch {
            combine(
                combine(
                    prefsManager.clockUse24h,
                    prefsManager.appIconSizeDp,
                    prefsManager.swipeUpEnabled,
                    prefsManager.swipeDownEnabled
                ) { a, b, c, d ->
                    Tuple4(a, b, c, d)
                },
                combine(
                    prefsManager.themeMode,
                    prefsManager.showNotifications,
                    prefsManager.usageLimitsEnabled,
                    prefsManager.hapticsEnabled
                ) { e, f, g, h ->
                    Tuple4(e, f, g, h)
                }
            ) { tuple1, tuple2 ->
                LauncherSettingsSnapshot(
                    clockUse24h = tuple1.a,
                    iconSizeDp = tuple1.b,
                    swipeUpEnabled = tuple1.c,
                    swipeDownEnabled = tuple1.d,
                    themeMode = tuple2.a,
                    showNotifications = tuple2.b,
                    usageLimitsEnabled = tuple2.c,
                    hapticsEnabled = tuple2.d
                )
            }.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        clockUse24h = snapshot.clockUse24h,
                        appIconSizeDp = snapshot.iconSizeDp,
                        swipeUpEnabled = snapshot.swipeUpEnabled,
                        swipeDownEnabled = snapshot.swipeDownEnabled,
                        themeMode = snapshot.themeMode,
                        showNotifications = snapshot.showNotifications,
                        usageLimitsEnabled = snapshot.usageLimitsEnabled,
                        hapticsEnabled = snapshot.hapticsEnabled
                    )
                }
            }
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val resolveInfo = context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                },
                0
            )
            resolveInfo?.activityInfo?.packageName == context.packageName
        }
    }

    private fun applyQuery(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isBlank()) return apps
        return apps.filter { it.name.contains(query, ignoreCase = true) }
    }

    private fun buildSectionIndexes(apps: List<AppInfo>): Map<Char, Int> {
        val indexMap = linkedMapOf<Char, Int>()
        apps.forEachIndexed { index, app ->
            val first = app.name.firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (first in 'A'..'Z') first else '#'
            if (!indexMap.containsKey(key)) {
                indexMap[key] = index
            }
        }
        return indexMap
    }

    companion object {
        private const val APP_WIDGET_HOST_ID = 9824
    }
}

private data class LauncherSettingsSnapshot(
    val clockUse24h: Boolean,
    val iconSizeDp: Int,
    val swipeUpEnabled: Boolean,
    val swipeDownEnabled: Boolean,
    val themeMode: String,
    val showNotifications: Boolean,
    val usageLimitsEnabled: Boolean,
    val hapticsEnabled: Boolean
)
