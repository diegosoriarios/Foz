package com.example.foz.ui

import android.app.Application
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.foz.data.AppRepository
import com.example.foz.data.IconPackManager
import com.example.foz.data.PrefsManager
import com.example.foz.data.WeatherRepository
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.model.IconPackInfo
import com.example.foz.model.WeatherModel
import com.example.foz.model.WidgetInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.Locale

data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
data class Tuple6<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)
data class Tuple7<A, B, C, D, E, F, G>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G)
data class Tuple8<A, B, C, D, E, F, G, H>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G, val h: H)
data class Tuple10<A, B, C, D, E, F, G, H, I, J>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G, val h: H, val i: I, val j: J)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val launcherApps = application.getSystemService(LauncherApps::class.java)
    private val appRepository = AppRepository(
        packageManager = application.packageManager,
        launcherApps = launcherApps
    )
    private val prefsManager = PrefsManager(application)
    private val appWidgetManager = AppWidgetManager.getInstance(application)
    private val appWidgetHost = AppWidgetHost(application, APP_WIDGET_HOST_ID)
    private val weatherRepository = WeatherRepository()
    private val iconPackManager = IconPackManager(application)

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private var clockJob: Job? = null
    private var lastWeatherSuccess = true

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            refreshApps()
            refreshIconPacks()
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refreshApps()
            refreshIconPacks()
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            refreshApps()
            refreshIconPacks()
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            refreshApps()
            refreshIconPacks()
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            refreshApps()
            refreshIconPacks()
        }
    }

    init {
        launcherApps?.registerCallback(packageCallback)
        observePinnedAndWidgets()
        observeLauncherOnboarding()
        observeInitialOnboarding()
        observeLauncherSettings()
        observeCustomizations()
        observeWeather()
        observeMedia()
        observeNotifications()
        refreshLauncherRoleStatus()
        refreshIconPacks()
        refreshApps()
        startClockTicker()
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            com.example.foz.data.NotificationRepository.getInstance().notifications.collect { list ->
                _uiState.update { it.copy(activeNotifications = list) }
            }
        }
    }

    fun toggleDebugNotifications() {
        val newState = !_uiState.value.showDebugNotifications
        if (newState) {
            com.example.foz.service.MediaSessionListenerService.requestRefresh()
        }
        _uiState.update { it.copy(showDebugNotifications = newState) }
    }

    fun showAppNotifications(packageName: String?) {
        _uiState.update { it.copy(showAppNotificationsPackage = packageName) }
    }

    fun dismissNotification(key: String) {
        com.example.foz.service.MediaSessionListenerService.cancelNotification(key)
    }

    fun dismissAllNotifications(packageName: String) {
        val notifications = _uiState.value.notificationsByPackage[packageName] ?: return
        viewModelScope.launch {
            // Dismiss from bottom to top (assuming oldest/last items are at the bottom)
            notifications.reversed().forEach { notification ->
                if (notification.isClearable) {
                    dismissNotification(notification.key)
                    delay(120) // Delay for animation effect
                }
            }
        }
    }

    fun triggerNotificationAction(action: com.example.foz.model.NotificationActionModel) {
        try {
            Log.d("LauncherViewModel", "Triggering action: ${action.title}")
            action.actionIntent?.send()
            
            viewModelScope.launch {
                delay(500)
                com.example.foz.service.MediaSessionListenerService.requestRefresh()
            }
        } catch (e: Exception) {
            Log.e("LauncherViewModel", "Failed to send action intent", e)
            _uiState.update { it.copy(errorMessage = "Failed to perform action: ${e.message}") }
        }
    }

    fun triggerNotificationContent(notification: com.example.foz.model.NotificationModel) {
        viewModelScope.launch {
            try {
                val pendingIntent = notification.contentIntent
                if (pendingIntent != null) {
                    Log.d("LauncherViewModel", "Triggering content intent for ${notification.packageName}")
                    
                    val context = getApplication<android.app.Application>()
                    
                    // Provide a fill-in intent to ensure activity flags are set
                    val fillInIntent = Intent().apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    // On Android 14+ (API 34), we MUST explicitly allow background activity starts 
                    // from a PendingIntent if we are not in a foreground state.
                    val options = if (Build.VERSION.SDK_INT >= 34) { // Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                        android.app.ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                            .toBundle()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        android.app.ActivityOptions.makeBasic().toBundle()
                    } else null

                    pendingIntent.send(context, 0, fillInIntent, null, null, null, options)
                    
                    // Wait a bit more for the app to handle the intent before removing the source
                    delay(800)
                    
                    if (notification.isClearable) {
                        dismissNotification(notification.key)
                    }
                } else {
                    _uiState.update { it.copy(errorMessage = "Notification has no valid click action.") }
                }
            } catch (e: Exception) {
                Log.e("LauncherViewModel", "Failed to send content intent", e)
                _uiState.update { it.copy(errorMessage = "Failed to open app: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun observeMedia() {
        viewModelScope.launch {
            val mediaManager = com.example.foz.MediaControllerManager.getInstance(getApplication())
            mediaManager.mediaState.collect { state ->
                _uiState.update { currentState ->
                    // Reset dismissal if music starts playing or if it's a completely different track
                    val shouldResetDismissal = state != null && (
                        (state.isPlaying && !currentState.mediaState?.isPlaying.let { it ?: false }) ||
                        (state.title != currentState.mediaState?.title)
                    )
                    
                    currentState.copy(
                        mediaState = state,
                        mediaDismissed = if (shouldResetDismissal) false else currentState.mediaDismissed
                    )
                }
            }
        }
    }

    fun mediaPlay() = com.example.foz.MediaControllerManager.getInstance(getApplication()).play()
    fun mediaPause() = com.example.foz.MediaControllerManager.getInstance(getApplication()).pause()
    fun mediaNext() = com.example.foz.MediaControllerManager.getInstance(getApplication()).next()
    fun mediaPrevious() = com.example.foz.MediaControllerManager.getInstance(getApplication()).previous()
    fun dismissMedia() {
        mediaPause()
        _uiState.update { it.copy(mediaDismissed = true) }
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
            val state = _uiState.value
            val iconPackPackage = state.iconPackPackageName
            
            val mapping = if (iconPackPackage != null) {
                iconPackManager.loadIconPackMapping(iconPackPackage)
            } else emptyMap<String, String>()

            val mappedApps = apps.map { app: AppInfo ->
                var updatedApp = app
                
                // Apply custom name
                state.customAppNames[app.packageName]?.let { customName ->
                    updatedApp = updatedApp.copy(name = customName)
                }

                // Apply icon (priority: custom icon > icon pack > default)
                val customDrawableName = state.customAppIcons[app.packageName]
                if (customDrawableName != null && iconPackPackage != null) {
                    val customIcon = iconPackManager.loadIcon(iconPackPackage, customDrawableName)
                    if (customIcon != null) {
                        updatedApp = updatedApp.copy(icon = customIcon)
                    }
                } else if (iconPackPackage != null) {
                    val componentKey = "ComponentInfo{${app.packageName}/${app.className}}"
                    val drawableName = mapping[componentKey]
                    val packIcon = drawableName?.let { iconPackManager.loadIcon(iconPackPackage, it) }
                    if (packIcon != null) {
                        updatedApp = updatedApp.copy(icon = packIcon)
                    }
                }
                
                updatedApp
            }.sortedBy { it.name.lowercase().removeAccents() }

            _uiState.update { currentState ->
                val filtered = applyQuery(mappedApps, currentState.searchQuery, currentState.hiddenApps)
                val pinnedMap = mappedApps.filter { currentState.pinnedPackageNames.contains(it.packageName) }.associateBy { it.packageName }
                val sortedPinnedApps = currentState.pinnedPackageNames.mapNotNull { pinnedMap[it] }
                
                currentState.copy(
                    allApps = mappedApps,
                    filteredApps = filtered,
                    pinnedApps = sortedPinnedApps,
                    sectionIndexes = buildSectionIndexes(filtered)
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = applyQuery(state.allApps, query, state.hiddenApps)
            state.copy(
                searchQuery = query,
                filteredApps = filtered,
                sectionIndexes = buildSectionIndexes(filtered)
            )
        }
    }

    fun openWidgetPicker() {
        _uiState.update { it.copy(showWidgetPicker = true, availableWidgets = availableWidgets()) }
    }

    fun dismissWidgetPicker() {
        _uiState.update { it.copy(showWidgetPicker = false, availableWidgets = emptyList()) }
    }

    fun showWeatherForecast() {
        _uiState.update { it.copy(showWeatherForecast = true) }
    }

    fun dismissWeatherForecast() {
        _uiState.update { it.copy(showWeatherForecast = false) }
    }

    fun openDrawer() {
        _uiState.update { it.copy(drawerOpen = true, swipeUpPanelOpen = false) }
    }

    fun openSwipeUpPanel() {
        _uiState.update { it.copy(swipeUpPanelOpen = true, drawerOpen = false) }
    }

    fun closeSwipeUpPanel() {
        _uiState.update { it.copy(
            swipeUpPanelOpen = false,
            searchQuery = "",
            filteredApps = it.allApps,
            sectionIndexes = buildSectionIndexes(it.allApps)
        ) }
    }

    fun openDrawerAtLetter(letter: Char) {
        _uiState.update { it.copy(drawerOpen = true, requestedSectionLetter = letter, swipeUpPanelOpen = false) }
    }

    fun clearRequestedSectionLetter() {
        _uiState.update { it.copy(requestedSectionLetter = null) }
    }

    fun closeDrawer() {
        _uiState.update { it.copy(
            drawerOpen = false,
            requestedSectionLetter = null,
            swipeUpPanelOpen = false,
            searchQuery = "",
            filteredApps = it.allApps,
            sectionIndexes = buildSectionIndexes(it.allApps)
        ) }
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

    fun setAppToRename(app: AppInfo?) {
        _uiState.update { it.copy(appToRename = app) }
    }

    fun setAppToSelectIcon(app: AppInfo?) {
        _uiState.update { it.copy(appToSelectIcon = app) }
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
                requestedSectionLetter = null,
                showWidgetPicker = false,
                selectedWidgetId = null,
                showWeatherForecast = false,
                showAppNotificationsPackage = null,
                errorMessage = null,
                appToRename = null,
                appToSelectIcon = null,
                searchQuery = "",
                filteredApps = it.allApps,
                sectionIndexes = buildSectionIndexes(it.allApps)
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

    fun showSettingsShade() {
        val context = getApplication<Application>()
        try {
            val service = context.getSystemService("statusbar")
            val method = service?.javaClass?.getMethod("expandSettingsPanel")
            method?.invoke(service)
        } catch (_: Throwable) {
        }
    }

    fun refreshLauncherRoleStatus() {
        _uiState.update { state ->
            state.copy(
                isLauncherDefault = isDefaultLauncher(),
                launcherStatusChecked = true,
                isNotificationListenerEnabled = isNotificationListenerEnabled()
            )
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val context = getApplication<Application>()
        val cn = ComponentName(context, com.example.foz.service.MediaSessionListenerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    fun openNotificationListenerSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        } else {
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
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

    fun setDrawerPaddingPercent(percent: Float) {
        viewModelScope.launch { prefsManager.setDrawerPaddingPercent(percent) }
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

    fun setMonochromeMode(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setMonochromeMode(enabled) }
    }

    fun setSuppressMonochromeDialog(suppress: Boolean) {
        viewModelScope.launch { prefsManager.setSuppressMonochromeDialog(suppress) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setHapticsEnabled(enabled) }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setAdBlockEnabled(enabled) }
    }

    fun clearVpnApprovalIntent() {
        _uiState.update { it.copy(vpnApprovalIntent = null) }
    }

    private fun syncAdBlockService(enabled: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, com.example.foz.service.AdBlockVpnService::class.java)
        if (enabled) {
            val vpnIntent = android.net.VpnService.prepare(context)
            if (vpnIntent != null) {
                _uiState.update { it.copy(vpnApprovalIntent = vpnIntent) }
            } else {
                context.startService(intent)
            }
        } else {
            intent.action = com.example.foz.service.AdBlockVpnService.ACTION_STOP
            context.startService(intent)
            _uiState.update { it.copy(vpnApprovalIntent = null) }
        }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setUseDynamicColor(enabled) }
    }

    fun setIconPack(packageName: String?) {
        viewModelScope.launch { prefsManager.setIconPackPackageName(packageName) }
    }

    fun hideApp(packageName: String, hide: Boolean) {
        viewModelScope.launch { prefsManager.setAppHidden(packageName, hide) }
    }

    fun renameApp(packageName: String, newName: String?) {
        viewModelScope.launch { prefsManager.setCustomAppName(packageName, newName) }
    }

    fun setCustomIcon(packageName: String, drawableName: String?) {
        viewModelScope.launch { prefsManager.setCustomAppIcon(packageName, drawableName) }
    }

    suspend fun getIconPackDrawables(packageName: String): List<String> {
        return iconPackManager.getAllIconDrawableNames(packageName)
    }

    fun loadPackIcon(packageName: String, drawableName: String) = iconPackManager.loadIcon(packageName, drawableName)

    fun refreshIconPacks() {
        viewModelScope.launch {
            val packs = iconPackManager.getInstalledIconPacks()
            _uiState.update { it.copy(availableIconPacks = packs) }
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            
            val hasFineLocation = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocation = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (hasFineLocation || hasCoarseLocation) {
                try {
                    val location = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) 
                        ?: locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    
                    if (location != null) {
                        var cityName: String? = ""
                        
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            withContext(Dispatchers.IO) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                    addresses?.firstOrNull()?.locality?.let { cityName = it }
                                } else {
                                    @Suppress("DEPRECATION")
                                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                    addresses?.firstOrNull()?.locality?.let { cityName = it }
                                }
                            }
                        } catch (e: Exception) {
                            // Geocoder failed
                        }

                        val weather = weatherRepository.fetchWeather(location.latitude, location.longitude, cityName)
                        if (weather != null) {
                            _uiState.update { it.copy(weather = weather) }
                            prefsManager.saveWeather(weatherRepository.toJson(weather))
                            lastWeatherSuccess = true
                            return@launch
                        }
                    }
                } catch (e: SecurityException) {
                }
            }

            lastWeatherSuccess = false
            if (_uiState.value.weather == null) {
                val weather = weatherRepository.getMockWeather()
                _uiState.update { it.copy(weather = weather) }
            }
        }
    }

    fun availableWidgets(): List<WidgetInfo> {
        val context = getApplication<Application>()
        val pm = context.packageManager
        return appWidgetManager?.installedProviders.orEmpty().mapNotNull { info ->
            try {
                val appInfo = pm.getApplicationInfo(info.provider.packageName, 0)
                WidgetInfo(
                    label = info.loadLabel(pm) ?: "Unknown Widget",
                    providerInfo = info,
                    icon = try { info.loadIcon(context, 0) } catch (e: Exception) { null },
                    preview = try { info.loadPreviewImage(context, 0) } catch (e: Exception) { null },
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    packageName = info.provider.packageName,
                    appIcon = pm.getApplicationIcon(appInfo)
                )
            } catch (e: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.appName.lowercase() }, { it.label.lowercase() }))
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
            val updated = _uiState.value.widgetIds.toMutableList()
            if (!updated.contains(widgetId)) {
                updated.add(widgetId)
                prefsManager.saveWidgetIds(updated)
            }
        }
    }

    fun removeWidgetId(widgetId: Int) {
        viewModelScope.launch {
            val updated = _uiState.value.widgetIds.toMutableList()
            updated.remove(widgetId)
            prefsManager.saveWidgetIds(updated)
            appWidgetHost.deleteAppWidgetId(widgetId)
        }
    }

    fun selectWidget(widgetId: Int?) {
        _uiState.update { it.copy(selectedWidgetId = widgetId) }
    }

    fun moveWidget(widgetId: Int, direction: Int) {
        viewModelScope.launch {
            val current = _uiState.value.widgetIds.toMutableList()
            val index = current.indexOf(widgetId)
            if (index != -1) {
                val newIndex = index + direction
                if (newIndex >= 0 && newIndex < current.size) {
                    current.removeAt(index)
                    current.add(newIndex, widgetId)
                    prefsManager.saveWidgetIds(current)
                }
            }
        }
    }

    fun resizeWidget(widgetId: Int, heightDp: Int) {
        viewModelScope.launch {
            prefsManager.setWidgetHeight(widgetId, heightDp)
        }
    }

    fun deleteWidgetId(widgetId: Int) {
        appWidgetHost.deleteAppWidgetId(widgetId)
    }

    override fun onCleared() {
        super.onCleared()
        launcherApps?.unregisterCallback(packageCallback)
        clockJob?.cancel()
    }

    fun appInfoIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun uninstallIntent(packageName: String): Intent {
        return Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
            var lastWeatherRefresh = 0L
            while (true) {
                val now = LocalDateTime.now()
                _uiState.update { it.copy(now = now) }
                
                // Refresh weather every 30 minutes (or 5 minutes if last refresh failed)
                val currentTime = System.currentTimeMillis()
                val refreshInterval = if (lastWeatherSuccess) 30 * 60 * 1000L else 5 * 60 * 1000L
                if (currentTime - lastWeatherRefresh > refreshInterval) {
                    refreshWeather()
                    lastWeatherRefresh = currentTime
                }

                delay(1000)
            }
        }
    }

    private fun observeWeather() {
        viewModelScope.launch {
            prefsManager.lastWeather.collect { json ->
                if (json != null && _uiState.value.weather == null) {
                    weatherRepository.fromJson(json)?.let { weather ->
                        _uiState.update { it.copy(weather = weather) }
                    }
                }
            }
        }
    }

    private fun observePinnedAndWidgets() {
        viewModelScope.launch {
            combine(
                prefsManager.pinnedApps,
                prefsManager.widgetIds,
                prefsManager.widgetHeights
            ) { pinned, widgetIds, widgetHeights ->
                Triple(pinned, widgetIds, widgetHeights)
            }.collect { (pinned, widgetIds, widgetHeights) ->
                _uiState.update { state ->
                    val pinnedMap = state.allApps.filter { pinned.contains(it.packageName) }.associateBy { it.packageName }
                    val sortedPinnedApps = pinned.mapNotNull { pinnedMap[it] }
                    
                    state.copy(
                        pinnedPackageNames = pinned,
                        pinnedApps = sortedPinnedApps,
                        widgetIds = widgetIds,
                        widgetHeights = widgetHeights
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

    fun setSwipeLeftAction(action: String) {
        viewModelScope.launch { prefsManager.setSwipeLeftAction(action) }
    }

    // fun setSwipeRightAction(action: String) {
    //     viewModelScope.launch { prefsManager.setSwipeRightAction(action) }
    // }

    private fun observeLauncherSettings() {
        viewModelScope.launch {
            combine(
                combine(
                    prefsManager.clockUse24h,
                    prefsManager.appIconSizeDp,
                    prefsManager.drawerPaddingPercent,
                    prefsManager.swipeUpEnabled,
                    prefsManager.swipeDownEnabled
                ) { a, b, c, d, e ->
                    Tuple5(a, b, c, d, e)
                },
                combine(
                    combine(
                        prefsManager.themeMode,
                        prefsManager.useDynamicColor,
                        prefsManager.showNotifications,
                        prefsManager.monochromeMode,
                        prefsManager.suppressMonochromeDialog
                    ) { a, b, c, d, e -> Tuple5(a, b, c, d, e) },
                    combine(
                        prefsManager.usageLimitsEnabled,
                        prefsManager.hapticsEnabled,
                        prefsManager.adBlockEnabled,
                        prefsManager.iconPackPackageName,
                        prefsManager.swipeLeftAction,
                        // prefsManager.swipeRightAction
                    ) { a, b, c, d, e -> Tuple5(a, b, c, d, e) }
                ) { t1, t2 ->
                    Tuple10(t1.a, t1.b, t1.c, t1.d, t1.e, t2.a, t2.b, t2.c, t2.d, t2.e)
                }
            ) { tuple1, tuple2 ->
                LauncherSettingsSnapshot(
                    clockUse24h = tuple1.a,
                    iconSizeDp = tuple1.b,
                    drawerPaddingPercent = tuple1.c,
                    swipeUpEnabled = tuple1.d,
                    swipeDownEnabled = tuple1.e,
                    themeMode = tuple2.a,
                    useDynamicColor = tuple2.b,
                    showNotifications = tuple2.c,
                    monochromeMode = tuple2.d,
                    suppressMonochromeDialog = tuple2.e,
                    usageLimitsEnabled = tuple2.f,
                    hapticsEnabled = tuple2.g,
                    adBlockEnabled = tuple2.h,
                    iconPackPackageName = tuple2.i,
                    swipeLeftAction = tuple2.j,
                    // swipeRightAction = "none"
                )
            }.collect { snapshot ->
                val iconPackChanged = snapshot.iconPackPackageName != _uiState.value.iconPackPackageName
                val adBlockChanged = snapshot.adBlockEnabled != _uiState.value.adBlockEnabled
                
                _uiState.update {
                    it.copy(
                        clockUse24h = snapshot.clockUse24h,
                        appIconSizeDp = snapshot.iconSizeDp,
                        drawerPaddingPercent = snapshot.drawerPaddingPercent,
                        swipeUpEnabled = snapshot.swipeUpEnabled,
                        swipeDownEnabled = snapshot.swipeDownEnabled,
                        themeMode = snapshot.themeMode,
                        useDynamicColor = snapshot.useDynamicColor,
                        showNotifications = snapshot.showNotifications,
                        monochromeMode = snapshot.monochromeMode,
                        suppressMonochromeDialog = snapshot.suppressMonochromeDialog,
                        usageLimitsEnabled = snapshot.usageLimitsEnabled,
                        hapticsEnabled = snapshot.hapticsEnabled,
                        adBlockEnabled = snapshot.adBlockEnabled,
                        iconPackPackageName = snapshot.iconPackPackageName,
                        swipeLeftAction = snapshot.swipeLeftAction,
                        // swipeRightAction = snapshot.swipeRightAction
                    )
                }
                if (iconPackChanged) {
                    refreshApps()
                }
                if (adBlockChanged) {
                    syncAdBlockService(snapshot.adBlockEnabled)
                }
            }
        }
    }

    private fun observeCustomizations() {
        viewModelScope.launch {
            combine(
                prefsManager.customAppNames,
                prefsManager.customAppIcons,
                prefsManager.hiddenApps
            ) { names, icons, hidden ->
                Triple(names, icons, hidden)
            }.collect { (names, icons, hidden) ->
                _uiState.update { state ->
                    state.copy(
                        customAppNames = names,
                        customAppIcons = icons,
                        hiddenApps = hidden
                    )
                }
                refreshApps()
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

    private fun applyQuery(apps: List<AppInfo>, query: String, hiddenApps: Set<String>): List<AppInfo> {
        val base = if (query.isBlank()) {
            apps.filter { !hiddenApps.contains(it.packageName) }
        } else {
            apps
        }
        if (query.isBlank()) return base
        val normalizedQuery = query.removeAccents()
        return base.filter { it.name.removeAccents().contains(normalizedQuery, ignoreCase = true) }
    }

    private fun buildSectionIndexes(apps: List<AppInfo>): Map<Char, Int> {
        val indexMap = linkedMapOf<Char, Int>()
        apps.forEachIndexed { index, app ->
            val firstChar = app.name.trim().firstOrNull() ?: return@forEachIndexed
            val first = firstChar.toString().removeAccents().firstOrNull()?.uppercaseChar() ?: firstChar.uppercaseChar()
            val key = when {
                first in 'A'..'Z' -> first
                first.isDigit() -> '#'
                else -> null // Only index A-Z and digits (#)
            }
            if (key != null && !indexMap.containsKey(key)) {
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
    val drawerPaddingPercent: Float,
    val swipeUpEnabled: Boolean,
    val swipeDownEnabled: Boolean,
    val themeMode: String,
    val useDynamicColor: Boolean,
    val showNotifications: Boolean,
    val monochromeMode: Boolean,
    val suppressMonochromeDialog: Boolean,
    val usageLimitsEnabled: Boolean,
    val hapticsEnabled: Boolean,
    val adBlockEnabled: Boolean,
    val iconPackPackageName: String?,
    val swipeLeftAction: String,
    // val swipeRightAction: String
)

private fun String.removeAccents(): String {
    return java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
}
