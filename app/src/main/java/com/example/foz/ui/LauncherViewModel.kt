package com.example.foz.ui

import android.app.Application
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
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val appRepository = AppRepository(
        packageManager = application.packageManager,
        launcherApps = application.getSystemService(LauncherApps::class.java)
    )
    private val prefsManager = PrefsManager(application)
    private val appWidgetManager = AppWidgetManager.getInstance(application)
    private val appWidgetHost = AppWidgetHost(application, APP_WIDGET_HOST_ID)

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private var clockJob: Job? = null

    init {
        observePinnedAndWidgets()
        refreshApps()
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
                state.copy(
                    allApps = apps,
                    filteredApps = filtered,
                    pinnedApps = apps.filter { state.pinnedPackageNames.contains(it.packageName) },
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
        _uiState.update { it.copy(drawerOpen = true) }
    }

    fun closeDrawer() {
        _uiState.update { it.copy(drawerOpen = false) }
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

    fun togglePinned(app: AppInfo) {
        viewModelScope.launch {
            val currentlyPinned = _uiState.value.pinnedPackageNames.contains(app.packageName)
            prefsManager.setAppPinned(app.packageName, !currentlyPinned)
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
            combine(prefsManager.pinnedApps, prefsManager.widgetIds) { pinned, widgetIds ->
                pinned to widgetIds
            }.collect { (pinned, widgetIds) ->
                _uiState.update { state ->
                    state.copy(
                        pinnedPackageNames = pinned,
                        pinnedApps = state.allApps.filter { pinned.contains(it.packageName) },
                        widgetIds = widgetIds
                    )
                }
            }
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
