package com.example.foz.ui

import android.app.Application
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.foz.BuildConfig
import com.example.foz.data.AppRepository
import com.example.foz.data.CalendarRepository
import com.example.foz.data.CalculatorEvaluator
import com.example.foz.data.ContactRepository
import com.example.foz.data.IconCacheManager
import com.example.foz.data.IconPackManager
import com.example.foz.data.InstantAnswerRepository
import com.example.foz.data.PrefsManager
import com.example.foz.data.UsageStatsRepository
import com.example.foz.data.WeatherRepository
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.model.ContactInfo
import com.example.foz.model.IconPackInfo
import com.example.foz.model.WeatherModel
import com.example.foz.model.WidgetInfo
import com.example.foz.model.WidgetSummary
import com.example.foz.ui.widgets.FozAppWidgetHostView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.util.Locale

data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
data class Tuple6<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)
data class Tuple7<A, B, C, D, E, F, G>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G)
data class Tuple8<A, B, C, D, E, F, G, H>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G, val h: H)
data class Tuple9<A, B, C, D, E, F, G, H, I>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G, val h: H, val i: I)
data class Tuple10<A, B, C, D, E, F, G, H, I, J>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G, val h: H, val i: I, val j: J)
data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val launcherApps = application.getSystemService(LauncherApps::class.java)
    private val appRepository = AppRepository(
        packageManager = application.packageManager,
        launcherApps = launcherApps
    )
    private val prefsManager = PrefsManager(application)
    private val appWidgetManager = AppWidgetManager.getInstance(application)
    private val appWidgetHost = object : AppWidgetHost(application, APP_WIDGET_HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo,
        ): AppWidgetHostView = FozAppWidgetHostView(context)
    }
    private val weatherRepository = WeatherRepository()
    private val iconPackManager = IconPackManager(application)
    private val contactRepository = ContactRepository(application)
    private val instantAnswerRepository = InstantAnswerRepository()
    private val usageStatsRepository = UsageStatsRepository(application)
    private val calendarRepository = CalendarRepository(application)
    private val iconCache = IconCacheManager(application)

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private var clockJob: Job? = null
    private var searchExtrasJob: Job? = null
    private var lastWeatherSuccess = true
    private var appsRetryJob: Job? = null
    private var appsRetryAttempts = 0
    private var lastSuccessfulAppsLoadAt = 0L
    private var iconsJob: Job? = null
    private var widgetRecreateJob: Job? = null
    private val hostViewCache = mutableMapOf<Int, AppWidgetHostView>()

    @Volatile
    private var selfUpdateCheckDone = false

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
        observeSelfUpdate()
        observePinnedAndWidgets()
        observeLauncherOnboarding()
        observeInitialOnboarding()
        observeLauncherSettings()
        observeCustomizations()
        observeWeather()
        observeMedia()
        observeNotifications()
        observeFolders()
        refreshUsageStats()
        refreshCalendarEvents()
        refreshLauncherRoleStatus()
        refreshIconPacks()
        restoreAppsCache()
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

    private fun observeFolders() {
        viewModelScope.launch {
            prefsManager.folders.collect { folders ->
                _uiState.update { it.copy(folders = folders) }
            }
        }
    }

    fun createFolder(name: String, app: AppInfo? = null) {
        viewModelScope.launch {
            val folder = com.example.foz.model.FolderInfo(
                id = "folder_${System.currentTimeMillis()}",
                name = name.ifBlank { getApplication<Application>().getString(com.example.foz.R.string.folder_default_name) },
                packageNames = app?.let { listOf(it.packageName) } ?: emptyList()
            )
            prefsManager.setFolders(_uiState.value.folders + folder)
            app?.let { prefsManager.setAppPinned(it.packageName, false) }
            _uiState.update { it.copy(folderPickerForApp = null) }
        }
    }

    fun renameFolder(folderId: String, name: String) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            val updated = _uiState.value.folders.map { folder ->
                if (folder.id == folderId) folder.copy(name = name.trim()) else folder
            }
            prefsManager.setFolders(updated)
            _uiState.update { it.copy(showFolderActions = null) }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            prefsManager.setFolders(_uiState.value.folders.filter { it.id != folderId })
            _uiState.update { it.copy(showFolderActions = null, openFolderId = null) }
        }
    }

    fun moveFolder(folderId: String, direction: Int) {
        viewModelScope.launch {
            val current = _uiState.value.folders.toMutableList()
            val index = current.indexOfFirst { it.id == folderId }
            if (index != -1) {
                val newIndex = index + direction
                if (newIndex in current.indices) {
                    val folder = current.removeAt(index)
                    current.add(newIndex, folder)
                    prefsManager.setFolders(current)
                }
            }
        }
    }

    fun addAppToFolder(app: AppInfo, folderId: String) {
        viewModelScope.launch {
            val updated = _uiState.value.folders.map { folder ->
                if (folder.id == folderId && !folder.packageNames.contains(app.packageName)) {
                    folder.copy(packageNames = folder.packageNames + app.packageName)
                } else {
                    folder
                }
            }
            prefsManager.setFolders(updated)
            prefsManager.setAppPinned(app.packageName, false)
            _uiState.update { it.copy(folderPickerForApp = null) }
        }
    }

    fun removeAppFromFolder(app: AppInfo, folderId: String) {
        viewModelScope.launch {
            val updated = _uiState.value.folders.map { folder ->
                if (folder.id == folderId) {
                    folder.copy(packageNames = folder.packageNames - app.packageName)
                } else {
                    folder
                }
            }
            prefsManager.setFolders(updated)
        }
    }

    fun openFolder(folderId: String) {
        _uiState.update { it.copy(openFolderId = folderId) }
    }

    fun dismissFolder() {
        _uiState.update { it.copy(openFolderId = null) }
    }

    fun showFolderActions(folderId: String?) {
        _uiState.update { it.copy(showFolderActions = folderId) }
    }

    fun showFolderPicker(app: AppInfo?) {
        _uiState.update { it.copy(folderPickerForApp = app) }
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

    fun triggerNotificationAction(
        action: com.example.foz.model.NotificationActionModel,
        replyText: String? = null
    ) {
        try {
            Log.d("LauncherViewModel", "Triggering action: ${action.title}")
            val context = getApplication<android.app.Application>()
            if (replyText != null && action.remoteInputKey != null) {
                val fillInIntent = Intent().apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(action.remoteInputKey, replyText)
                }
                action.actionIntent?.send(context, 0, fillInIntent)
            } else {
                action.actionIntent?.send()
            }

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
        if (BuildConfig.DEBUG) logCachedRemoteViews()
    }

    fun stopWidgetListening() {
        appWidgetHost.stopListening()
    }

    fun onOwnPackageReplaced() {
        bumpWidgetRecreateToken()
        scheduleWidgetRecreates()
    }

    private fun bumpWidgetRecreateToken() {
        _uiState.update { it.copy(widgetRecreateToken = it.widgetRecreateToken + 1) }
    }

    private fun scheduleWidgetRecreates() {
        widgetRecreateJob?.cancel()
        widgetRecreateJob = viewModelScope.launch {
            WIDGET_RECREATE_DELAYS_MS.forEach { delayMs ->
                delay(delayMs)
                bumpWidgetRecreateToken()
            }
        }
    }

    private fun observeSelfUpdate() {
        viewModelScope.launch {
            val storedMtime = try {
                prefsManager.lastApkMtime.first()
            } catch (_: Exception) {
                -1L
            }
            val storedVersion = try {
                prefsManager.lastVersionCode.first()
            } catch (_: Exception) {
                -1
            }
            val currentMtime = ownApkMtime()
            val currentVersion = currentVersionCode()

            // APK mtime changes on every install (even same-versionCode debug builds)
            val mtimeChanged = storedMtime != -1L && currentMtime > 0L && storedMtime != currentMtime
            val versionChanged = storedVersion != -1 && currentVersion != -1 && storedVersion != currentVersion
            if (mtimeChanged || versionChanged) {
                onOwnPackageReplaced()
            }
            if (currentMtime > 0L) {
                prefsManager.setLastApkMtime(currentMtime)
            }
            if (currentVersion != -1) {
                prefsManager.setLastVersionCode(currentVersion)
            }
            selfUpdateCheckDone = true
            if (!mtimeChanged && !versionChanged) {
                bumpWidgetRecreateToken()
            }
        }
    }

    private fun ownApkMtime(): Long {
        return try {
            val sourceDir = getApplication<Application>().applicationInfo.sourceDir
            if (sourceDir != null) File(sourceDir).lastModified() else 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun currentVersionCode(): Int {
        return try {
            val pm = getApplication<Application>().packageManager
            val info = pm.getPackageInfo(getApplication<Application>().packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (e: Exception) {
            -1
        }
    }

    fun refreshApps() {
        appsRetryJob?.cancel()
        iconsJob?.cancel()
        appsRetryAttempts = 0
        loadAppsInternal()
    }

    fun refreshAppsIfStale(maxAgeMs: Long = 5_000L) {
        if (System.currentTimeMillis() - lastSuccessfulAppsLoadAt >= maxAgeMs) {
            refreshApps()
        }
    }

    private fun loadAppsInternal() {
        viewModelScope.launch {
            // Read the customization truth from prefs (the settings flow may not have
            // emitted yet on a cold start)
            val iconPack = try {
                prefsManager.iconPackPackageName.first()
            } catch (_: Exception) {
                null
            }
            val customIcons = try {
                prefsManager.customAppIcons.first()
            } catch (_: Exception) {
                emptyMap<String, String>()
            }

            val apps = try {
                // Phase 1: fast icon-less query so names/order/favorites render instantly
                appRepository.getLaunchableApps(loadIcons = false)
            } catch (e: Exception) {
                Log.e("LauncherViewModel", "Failed to load launchable apps", e)
                emptyList<AppInfo>()
            }

            if (apps.isEmpty()) {
                // The system can transiently return an empty activity list right after
                // the launcher itself is updated; keep the current list and retry.
                Log.w("LauncherViewModel", "Empty app list returned; scheduling retry")
                scheduleAppsRetry()
                return@launch
            }

            appsRetryAttempts = 0
            applyApps(apps, persistCache = true, bumpVersion = true)
            // Phase 2: icons stream in per batch (disk cache first, APK fallback)
            loadIconsProgressively(apps, iconPack, customIcons)
        }
    }

    private fun scheduleAppsRetry() {
        if (appsRetryAttempts >= MAX_APPS_LOAD_RETRIES) return
        appsRetryAttempts++
        appsRetryJob?.cancel()
        appsRetryJob = viewModelScope.launch {
            delay(APPS_RETRY_BASE_DELAY_MS * (1L shl (appsRetryAttempts - 1)))
            loadAppsInternal()
        }
    }

    private fun restoreAppsCache() {
        viewModelScope.launch {
            val cached = try {
                prefsManager.appsCache.first()
            } catch (e: Exception) {
                Log.e("LauncherViewModel", "Failed to read apps cache", e)
                emptyList<AppInfo>()
            }
            if (cached.isEmpty()) return@launch
            // Only seed the state if the real list hasn't landed yet
            if (_uiState.value.allApps.isNotEmpty()) return@launch
            applyApps(cached, persistCache = false, bumpVersion = false)
        }
    }

    private suspend fun applyApps(
        apps: List<AppInfo>,
        persistCache: Boolean,
        bumpVersion: Boolean
    ) {
        val state = _uiState.value

        // Keep previously resolved icons (cache restore or earlier refresh) so the
        // icon-less query never blanks the UI
        val existingIcons = state.allApps.associate { "${it.packageName}/${it.className}" to it.icon }

        val mappedApps = apps.map { app: AppInfo ->
            var updatedApp = app

            // Apply custom name
            state.customAppNames[app.packageName]?.let { customName ->
                updatedApp = updatedApp.copy(name = customName)
            }

            if (updatedApp.icon == null) {
                existingIcons["${app.packageName}/${app.className}"]?.let { previousIcon ->
                    updatedApp = updatedApp.copy(icon = previousIcon)
                }
            }

            updatedApp
        }.sortedBy { it.name.lowercase().removeAccents() }

        _uiState.update { currentState ->
            val sortedApps = orderApps(mappedApps, currentState)
            val filtered = applyQuery(sortedApps, currentState.searchQuery, currentState.hiddenApps)
            val pinnedMap = sortedApps.filter { currentState.pinnedPackageNames.contains(it.packageName) }.associateBy { it.packageName }
            val sortedPinnedApps = currentState.pinnedPackageNames.mapNotNull { pinnedMap[it] }

            currentState.copy(
                allApps = sortedApps,
                filteredApps = filtered,
                pinnedApps = sortedPinnedApps,
                sectionIndexes = buildSections(filtered, currentState),
                appListVersion = if (bumpVersion) currentState.appListVersion + 1 else currentState.appListVersion
            )
        }

        if (persistCache && mappedApps.isNotEmpty()) {
            prefsManager.saveAppsCache(mappedApps)
            lastSuccessfulAppsLoadAt = System.currentTimeMillis()
        }
    }

    private fun loadIconsProgressively(entries: List<AppInfo>, iconPack: String?, customIcons: Map<String, String>) {
        iconsJob?.cancel()
        iconsJob = viewModelScope.launch {
            val mapping = if (iconPack != null) {
                try {
                    iconPackManager.loadIconPackMapping(iconPack)
                } catch (_: Exception) {
                    emptyMap<String, String>()
                }
            } else emptyMap<String, String>()

            // Resolve pinned favorites first so the home screen fills in fastest
            val ordered = entries.sortedByDescending { it.packageName in _uiState.value.pinnedPackageNames }
            val validKeys = mutableSetOf<String>()

            ordered.chunked(ICON_LOAD_BATCH_SIZE).forEach { batch ->
                val resolved = withContext(Dispatchers.IO) {
                    batch.mapNotNull { app ->
                        val customDrawable = customIcons[app.packageName]
                        val packDrawable = mapping["ComponentInfo{${app.packageName}/${app.className}}"]
                        val key = iconCache.cacheKey(
                            app.packageName,
                            app.className,
                            app.lastUpdateTime,
                            IconCacheManager.variantKey(iconPack, customDrawable)
                        )
                        validKeys.add(key)
                        val cached = iconCache.get(key)
                        val drawable = cached
                            ?: resolveAppIcon(app, iconPack, customDrawable, packDrawable)?.also { iconCache.put(key, it) }
                        drawable?.let { IconUpdate(app.packageName, app.className, it) }
                    }
                }
                applyIconUpdates(resolved)
            }

            withContext(Dispatchers.IO) {
                iconCache.cleanUp(validKeys)
            }
        }
    }

    private suspend fun resolveAppIcon(
        app: AppInfo,
        iconPack: String?,
        customDrawable: String?,
        packDrawable: String?
    ): Drawable? {
        if (iconPack != null) {
            if (customDrawable != null) {
                iconPackManager.loadIcon(iconPack, customDrawable)?.let { return it }
            }
            if (packDrawable != null) {
                iconPackManager.loadIcon(iconPack, packDrawable)?.let { return it }
            }
        }
        return appRepository.getActivityIcon(app.packageName, app.className)
    }

    private fun applyIconUpdates(updates: List<IconUpdate>) {
        if (updates.isEmpty()) return
        val byComponent = updates.associate { "${it.packageName}/${it.className}" to it.drawable }
        _uiState.update { state ->
            fun patchIcons(list: List<AppInfo>): List<AppInfo> = list.map { app ->
                byComponent["${app.packageName}/${app.className}"]?.let { app.copy(icon = it) } ?: app
            }
            state.copy(
                allApps = patchIcons(state.allApps),
                filteredApps = patchIcons(state.filteredApps),
                pinnedApps = patchIcons(state.pinnedApps)
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = applyQuery(state.allApps, query, state.hiddenApps)
            state.copy(
                searchQuery = query,
                filteredApps = filtered,
                sectionIndexes = buildSections(filtered, state)
            )
        }
        scheduleSearchExtras(query)
    }

    private fun orderApps(apps: List<AppInfo>, state: LauncherUiState): List<AppInfo> {
        return if (state.sortByUsageEnabled && state.usageCounts.isNotEmpty()) {
            apps.sortedWith(
                compareByDescending<AppInfo> { state.usageCounts[it.packageName] ?: 0 }
                    .thenBy { it.name.lowercase().removeAccents() }
            )
        } else {
            apps.sortedBy { it.name.lowercase().removeAccents() }
        }
    }

    private fun buildSections(apps: List<AppInfo>, state: LauncherUiState): Map<Char, Int> {
        if (state.sortByUsageEnabled && state.usageCounts.isNotEmpty()) return emptyMap()
        return buildSectionIndexes(apps)
    }

    private fun reapplyAppOrdering() {
        _uiState.update { state ->
            val sortedApps = orderApps(state.allApps, state)
            val filtered = applyQuery(sortedApps, state.searchQuery, state.hiddenApps)
            state.copy(
                allApps = sortedApps,
                filteredApps = filtered,
                sectionIndexes = buildSections(filtered, state)
            )
        }
    }

    private fun scheduleSearchExtras(query: String) {
        searchExtrasJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchContacts = emptyList(), instantAnswer = null, instantAnswerLoading = false) }
            return
        }
        val isCalculation = CalculatorEvaluator.evaluate(query) != null
        searchExtrasJob = viewModelScope.launch {
            delay(300)
            val state = _uiState.value

            if (state.searchContactsEnabled && hasContactsPermission()) {
                val contacts = contactRepository.searchContacts(query)
                _uiState.update { current ->
                    if (current.searchQuery == query) current.copy(searchContacts = contacts) else current
                }
            } else {
                _uiState.update { it.copy(searchContacts = emptyList()) }
            }

            val current = _uiState.value
            if (!isCalculation && current.webAnswersEnabled && current.filteredApps.isEmpty()) {
                _uiState.update { it.copy(instantAnswerLoading = true) }
                val answer = instantAnswerRepository.fetchInstantAnswer(query, Locale.getDefault().language)
                _uiState.update { latest ->
                    if (latest.searchQuery == query) {
                        latest.copy(instantAnswer = answer, instantAnswerLoading = false)
                    } else {
                        latest
                    }
                }
            } else {
                _uiState.update { it.copy(instantAnswer = null, instantAnswerLoading = false) }
            }
        }
    }

    private fun hasContactsPermission(): Boolean {
        return getApplication<Application>().checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    fun searchWeb(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val context = getApplication<Application>()
        val webIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, trimmed)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(webIntent)
        } catch (_: Exception) {
            try {
                val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=$encoded"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    fun openContact(contact: ContactInfo) {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(contact.phoneNumber)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun openUrl(url: String) {
        val context = getApplication<Application>()
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
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
        searchExtrasJob?.cancel()
        _uiState.update {
            val visibleApps = applyQuery(it.allApps, "", it.hiddenApps)
            it.copy(
                drawerOpen = true,
                swipeUpPanelOpen = false,
                searchQuery = "",
                filteredApps = visibleApps,
                sectionIndexes = buildSectionIndexes(visibleApps),
                searchContacts = emptyList(),
                instantAnswer = null,
                instantAnswerLoading = false
            )
        }
    }

    fun openSwipeUpPanel() {
        _uiState.update { it.copy(swipeUpPanelOpen = true, drawerOpen = false) }
    }

    fun closeSwipeUpPanel() {
        searchExtrasJob?.cancel()
        _uiState.update { it.copy(
            swipeUpPanelOpen = false,
            searchQuery = "",
            filteredApps = it.allApps,
            sectionIndexes = buildSectionIndexes(it.allApps),
            searchContacts = emptyList(),
            instantAnswer = null,
            instantAnswerLoading = false
        ) }
    }

    fun openDrawerAtLetter(letter: Char) {
        searchExtrasJob?.cancel()
        _uiState.update {
            val visibleApps = applyQuery(it.allApps, "", it.hiddenApps)
            it.copy(
                drawerOpen = true,
                requestedSectionLetter = letter,
                swipeUpPanelOpen = false,
                searchQuery = "",
                filteredApps = visibleApps,
                sectionIndexes = buildSectionIndexes(visibleApps),
                searchContacts = emptyList(),
                instantAnswer = null,
                instantAnswerLoading = false
            )
        }
    }

    fun clearRequestedSectionLetter() {
        _uiState.update { it.copy(requestedSectionLetter = null) }
    }

    fun closeDrawer() {
        searchExtrasJob?.cancel()
        _uiState.update { it.copy(
            drawerOpen = false,
            requestedSectionLetter = null,
            swipeUpPanelOpen = false,
            searchQuery = "",
            filteredApps = it.allApps,
            sectionIndexes = buildSectionIndexes(it.allApps),
            searchContacts = emptyList(),
            instantAnswer = null,
            instantAnswerLoading = false
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
        searchExtrasJob?.cancel()
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
                sectionIndexes = buildSectionIndexes(it.allApps),
                searchContacts = emptyList(),
                instantAnswer = null,
                instantAnswerLoading = false
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

    fun refreshUsageStats() {
        val granted = usageStatsRepository.hasPermission()
        viewModelScope.launch(Dispatchers.IO) {
            val counts = if (granted) {
                usageStatsRepository.getLaunchCounts(
                    days = 7,
                    excludePackages = setOf(getApplication<Application>().packageName)
                )
            } else emptyMap()
            val screenTime = if (granted) usageStatsRepository.getScreenTimeToday() else 0L
            _uiState.update { it.copy(
                isUsageStatsPermissionGranted = granted,
                usageCounts = counts,
                usageTodayMs = screenTime
            ) }
            if (granted && _uiState.value.sortByUsageEnabled) {
                reapplyAppOrdering()
            }
        }
    }

    fun dismissBreakReminder() {
        _uiState.update { it.copy(breakReminderDismissed = true) }
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun setUsageLimitHours(hours: Int) {
        viewModelScope.launch { prefsManager.setUsageLimitHours(hours) }
    }

    fun refreshCalendarEvents() {
        val granted = hasCalendarPermission()
        viewModelScope.launch(Dispatchers.IO) {
            val days = _uiState.value.calendarAgendaDays
            val events = if (granted) calendarRepository.getUpcomingEvents(days = days) else emptyList()
            _uiState.update { it.copy(
                isCalendarPermissionGranted = granted,
                calendarEvents = events
            ) }
        }
    }

    fun showAgenda() {
        _uiState.update { it.copy(showAgenda = true) }
        refreshCalendarEvents()
    }

    fun dismissAgenda() {
        _uiState.update { it.copy(showAgenda = false) }
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(isCalendarPermissionGranted = granted) }
        if (granted) {
            refreshCalendarEvents()
        }
    }

    fun setCalendarPreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setCalendarPreviewEnabled(enabled) }
    }

    fun setCalendarPreviewCount(count: Int) {
        viewModelScope.launch { prefsManager.setCalendarPreviewCount(count) }
    }

    fun setCalendarAgendaDays(days: Int) {
        viewModelScope.launch { prefsManager.setCalendarAgendaDays(days) }
    }

    fun setWeatherEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setWeatherEnabled(enabled) }
    }

    fun setWeatherImperial(imperial: Boolean) {
        viewModelScope.launch { prefsManager.setWeatherImperial(imperial) }
    }

    fun setMediaCardEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setMediaCardEnabled(enabled) }
    }

    suspend fun isWeatherEnabled(): Boolean = prefsManager.weatherEnabled.first()

    private fun hasCalendarPermission(): Boolean {
        return getApplication<Application>().checkSelfPermission(
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun setSuggestAppsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setSuggestAppsEnabled(enabled) }
    }

    fun setSortByUsageEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setSortByUsageEnabled(enabled) }
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

    fun setSwipeUpEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setSwipeUpEnabled(enabled) }
    }

    fun setSwipeDownEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setSwipeDownEnabled(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefsManager.setThemeMode(mode) }
    }

    fun setQuickRepliesEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setQuickRepliesEnabled(enabled) }
    }

    fun setNotificationSummaryEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setNotificationSummaryEnabled(enabled) }
    }

    fun muteApp(packageName: String, muted: Boolean) {
        viewModelScope.launch { prefsManager.setAppMuted(packageName, muted) }
    }

    fun setSearchContactsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setSearchContactsEnabled(enabled) }
    }

    fun setWebAnswersEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setWebAnswersEnabled(enabled) }
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
        if (!_uiState.value.weatherEnabled) return
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

                        val weather = weatherRepository.fetchWeather(
                            location.latitude,
                            location.longitude,
                            cityName,
                            imperial = _uiState.value.weatherImperial
                        )
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
        if (!selfUpdateCheckDone) {
            return emptyList()
        }
        val widgetIds = _uiState.value.widgetIds
        hostViewCache.keys.retainAll(widgetIds.toSet())
        return widgetIds.mapNotNull { widgetId ->
            val info = appWidgetManager.getAppWidgetInfo(widgetId) ?: return@mapNotNull null
            val hostView = hostViewCache.getOrPut(widgetId) {
                val created = appWidgetHost.createView(getApplication(), widgetId, info)
                if (BuildConfig.DEBUG) logWidgetHostViewTree(widgetId, created)
                created
            }
            widgetId to hostView
        }
    }

    private fun logCachedRemoteViews() {
        try {
            val mViewsField = AppWidgetHost::class.java.getDeclaredField("mViews")
            mViewsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val cachedViews = mViewsField.get(appWidgetHost) as? Map<Int, Any> ?: return
            cachedViews.forEach { (appWidgetId, remoteViews) ->
                val appPackage = try {
                    val appField = remoteViews.javaClass.getDeclaredField("mApplication")
                    appField.isAccessible = true
                    (appField.get(remoteViews) as? android.content.pm.ApplicationInfo)?.packageName ?: "null"
                } catch (e: Exception) {
                    "unreadable(${e.javaClass.simpleName})"
                }
                Log.d(WIDGET_DIAG_TAG, "cached[id=$appWidgetId] ${remoteViews.javaClass.simpleName} app=$appPackage")
                try {
                    val actionsField = remoteViews.javaClass.getDeclaredField("mActions")
                    actionsField.isAccessible = true
                    val actions = actionsField.get(remoteViews) as? List<Any> ?: return@forEach
                    actions.forEachIndexed { index, action ->
                        logRemoteViewAction(appWidgetId, index, action)
                    }
                } catch (e: Exception) {
                    Log.d(WIDGET_DIAG_TAG, "cached[id=$appWidgetId] mActions unreadable: ${e.javaClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            Log.d(WIDGET_DIAG_TAG, "mViews unreadable: ${e.javaClass.simpleName}")
        }
    }

    private fun logRemoteViewAction(appWidgetId: Int, index: Int, action: Any) {
        try {
            val description = StringBuilder(action.javaClass.simpleName)
            val viewId = action.reflectInt("viewId")
            description.append(" viewId=0x").append(Integer.toHexString(viewId))
            listOf("methodName", "value", "drawableId", "srcId", "duration", "strokeColor").forEach { name ->
                try {
                    val field = action.javaClass.getDeclaredField(name)
                    field.isAccessible = true
                    when (val value = field.get(action)) {
                        null -> Unit
                        is Int -> description.append(' ').append(name).append("=0x")
                            .append(Integer.toHexString(value)).append('(').append(value).append(')')
                        else -> description.append(' ').append(name).append('=').append(value)
                    }
                } catch (_: NoSuchFieldException) {
                }
            }
            Log.d(WIDGET_DIAG_TAG, "cached[id=$appWidgetId] action[$index] $description")
        } catch (e: Exception) {
            Log.d(WIDGET_DIAG_TAG, "cached[id=$appWidgetId] action[$index] unreadable: ${e.javaClass.simpleName}")
        }
    }

    private fun Any.reflectInt(name: String): Int {
        return try {
            val field = javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.getInt(this)
        } catch (e: Exception) {
            -1
        }
    }

    private fun logWidgetHostViewTree(appWidgetId: Int, root: View) {
        try {
            val queue = ArrayDeque<Pair<Int, View>>()
            queue.add(0 to root)
            while (queue.isNotEmpty()) {
                val (depth, view) = queue.removeFirst()
                val background = try {
                    view.background?.javaClass?.simpleName
                } catch (e: Exception) {
                    "?"
                }
                val foreground = try {
                    view.foreground?.javaClass?.simpleName
                } catch (e: Exception) {
                    "?"
                }
                val image = (view as? ImageView)?.let { imageView ->
                    try {
                        imageView.drawable?.javaClass?.simpleName
                    } catch (e: Exception) {
                        "?"
                    }
                }
                val contextPackage = try {
                    view.context.packageName
                } catch (e: Exception) {
                    "?"
                }
                Log.d(
                    WIDGET_DIAG_TAG,
                    "tree[id=$appWidgetId] d$depth ${view.javaClass.simpleName}" +
                        " id=0x${Integer.toHexString(view.id)}" +
                        " ctx=$contextPackage" +
                        " bg=$background fg=$foreground img=$image"
                )
                (view as? ViewGroup)?.let { group ->
                    for (i in 0 until group.childCount) {
                        queue.add(depth + 1 to group.getChildAt(i))
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(WIDGET_DIAG_TAG, "tree[id=$appWidgetId] unreadable: ${e.javaClass.simpleName}")
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
            hostViewCache.remove(widgetId)
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

    fun setWidgetPanelHeight(heightDp: Int) {
        viewModelScope.launch {
            prefsManager.setWidgetPanelHeight(heightDp)
        }
    }

    fun resizeWidget(widgetId: Int, heightDp: Int) {
        viewModelScope.launch {
            prefsManager.setWidgetHeight(widgetId, heightDp)
        }
    }

    fun widgetSummaries(): List<WidgetSummary> {
        val pm = getApplication<Application>().packageManager
        return _uiState.value.widgetIds.mapNotNull { widgetId ->
            val info = appWidgetManager.getAppWidgetInfo(widgetId) ?: return@mapNotNull null
            val label = try {
                info.loadLabel(pm)?.toString()
            } catch (_: Exception) {
                null
            } ?: "Widget"
            WidgetSummary(widgetId, label, info.configure != null)
        }
    }

    fun deleteWidgetId(widgetId: Int) {
        hostViewCache.remove(widgetId)
        appWidgetHost.deleteAppWidgetId(widgetId)
    }

    override fun onCleared() {
        super.onCleared()
        launcherApps?.unregisterCallback(packageCallback)
        clockJob?.cancel()
        widgetRecreateJob?.cancel()
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
            var lastUsageRefresh = 0L
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

                // Refresh usage stats every minute (screen time tracking for break reminders)
                if (currentTime - lastUsageRefresh > 60 * 1000L) {
                    refreshUsageStats()
                    lastUsageRefresh = currentTime
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

    private fun observeLauncherSettings() {
        viewModelScope.launch {
            combine(
                combine(
                    prefsManager.clockUse24h,
                    prefsManager.appIconSizeDp,
                    prefsManager.swipeUpEnabled,
                    prefsManager.swipeDownEnabled,
                    prefsManager.weatherEnabled
                ) { a, b, c, d, e ->
                    Tuple5(a, b, c, d, e)
                },
                combine(
                    combine(
                        prefsManager.themeMode,
                        prefsManager.useDynamicColor,
                        prefsManager.notificationSummaryEnabled,
                        prefsManager.quickRepliesEnabled,
                        prefsManager.monochromeMode
                    ) { a, b, c, d, e -> Tuple5(a, b, c, d, e) },
                combine(
                    prefsManager.suppressMonochromeDialog,
                    prefsManager.usageLimitsEnabled,
                    prefsManager.hapticsEnabled,
                    prefsManager.adBlockEnabled,
                    prefsManager.iconPackPackageName
                ) { a, b, c, d, e -> Tuple5(a, b, c, d, e) }
                ) { t1, t2 ->
                    Tuple10(t1.a, t1.b, t1.c, t1.d, t1.e, t2.a, t2.b, t2.c, t2.d, t2.e)
                },
                combine(
                    prefsManager.widgetPanelHeight,
                    prefsManager.searchContactsEnabled,
                    prefsManager.webAnswersEnabled
                ) { a, b, c -> Triple(a, b, c) },
                combine(
                    combine(
                        prefsManager.usageLimitHours,
                        prefsManager.suggestAppsEnabled,
                        prefsManager.sortByUsageEnabled,
                        prefsManager.calendarPreviewEnabled
                    ) { a, b, c, d -> Tuple4(a, b, c, d) },
                    combine(
                        prefsManager.weatherImperial,
                        prefsManager.mediaCardEnabled,
                        prefsManager.calendarPreviewCount,
                        prefsManager.calendarAgendaDays
                    ) { a, b, c, d -> Tuple4(a, b, c, d) }
                ) { t1, t2 ->
                    Tuple8(t1.a, t1.b, t1.c, t1.d, t2.a, t2.b, t2.c, t2.d)
                }
            ) { tuple1, tuple2, tuple3, tuple4 ->
                LauncherSettingsSnapshot(
                    clockUse24h = tuple1.a,
                    iconSizeDp = tuple1.b,
                    swipeUpEnabled = tuple1.c,
                    swipeDownEnabled = tuple1.d,
                    weatherEnabled = tuple1.e,
                    themeMode = tuple2.a,
                    useDynamicColor = tuple2.b,
                    notificationSummaryEnabled = tuple2.c,
                    quickRepliesEnabled = tuple2.d,
                    monochromeMode = tuple2.e,
                    suppressMonochromeDialog = tuple2.f,
                    usageLimitsEnabled = tuple2.g,
                    hapticsEnabled = tuple2.h,
                    adBlockEnabled = tuple2.i,
                    iconPackPackageName = tuple2.j,
                    widgetPanelHeightDp = tuple3.first,
                    searchContactsEnabled = tuple3.second,
                    webAnswersEnabled = tuple3.third,
                    usageLimitHours = tuple4.a,
                    suggestAppsEnabled = tuple4.b,
                    sortByUsageEnabled = tuple4.c,
                    calendarPreviewEnabled = tuple4.d,
                    weatherImperial = tuple4.e,
                    mediaCardEnabled = tuple4.f,
                    calendarPreviewCount = tuple4.g,
                    calendarAgendaDays = tuple4.h
                )
            }.collect { snapshot ->
                val iconPackChanged = snapshot.iconPackPackageName != _uiState.value.iconPackPackageName
                val adBlockChanged = snapshot.adBlockEnabled != _uiState.value.adBlockEnabled
                val sortChanged = snapshot.sortByUsageEnabled != _uiState.value.sortByUsageEnabled
                val weatherEnabledChanged = snapshot.weatherEnabled != _uiState.value.weatherEnabled
                val weatherUnitsChanged = snapshot.weatherImperial != _uiState.value.weatherImperial
                val agendaDaysChanged = snapshot.calendarAgendaDays != _uiState.value.calendarAgendaDays

                _uiState.update {
                    it.copy(
                        clockUse24h = snapshot.clockUse24h,
                        appIconSizeDp = snapshot.iconSizeDp,
                        swipeUpEnabled = snapshot.swipeUpEnabled,
                        swipeDownEnabled = snapshot.swipeDownEnabled,
                        widgetPanelHeightDp = snapshot.widgetPanelHeightDp,
                        themeMode = snapshot.themeMode,
                        useDynamicColor = snapshot.useDynamicColor,
                        notificationSummaryEnabled = snapshot.notificationSummaryEnabled,
                        quickRepliesEnabled = snapshot.quickRepliesEnabled,
                        monochromeMode = snapshot.monochromeMode,
                        suppressMonochromeDialog = snapshot.suppressMonochromeDialog,
                        usageLimitsEnabled = snapshot.usageLimitsEnabled,
                        hapticsEnabled = snapshot.hapticsEnabled,
                        adBlockEnabled = snapshot.adBlockEnabled,
                        iconPackPackageName = snapshot.iconPackPackageName,
                        searchContactsEnabled = snapshot.searchContactsEnabled,
                        webAnswersEnabled = snapshot.webAnswersEnabled,
                        usageLimitHours = snapshot.usageLimitHours,
                        suggestAppsEnabled = snapshot.suggestAppsEnabled,
                        sortByUsageEnabled = snapshot.sortByUsageEnabled,
                        calendarPreviewEnabled = snapshot.calendarPreviewEnabled,
                        weatherEnabled = snapshot.weatherEnabled,
                        weatherImperial = snapshot.weatherImperial,
                        mediaCardEnabled = snapshot.mediaCardEnabled,
                        calendarPreviewCount = snapshot.calendarPreviewCount,
                        calendarAgendaDays = snapshot.calendarAgendaDays
                    )
                }
                if (iconPackChanged) {
                    refreshApps()
                }
                if (adBlockChanged) {
                    syncAdBlockService(snapshot.adBlockEnabled)
                }
                if (sortChanged) {
                    reapplyAppOrdering()
                }
                if (weatherEnabledChanged) {
                    if (snapshot.weatherEnabled) {
                        refreshWeather()
                    } else {
                        _uiState.update { it.copy(weather = null, showWeatherForecast = false) }
                    }
                } else if (weatherUnitsChanged && snapshot.weatherEnabled) {
                    refreshWeather()
                }
                if (agendaDaysChanged) {
                    refreshCalendarEvents()
                }
            }
        }
    }

    private fun observeCustomizations() {
        viewModelScope.launch {
            combine(
                prefsManager.customAppNames,
                prefsManager.customAppIcons,
                prefsManager.hiddenApps,
                prefsManager.mutedApps
            ) { names, icons, hidden, muted ->
                Tuple4(names, icons, hidden, muted)
            }.collect { (names, icons, hidden, muted) ->
                _uiState.update { state ->
                    state.copy(
                        customAppNames = names,
                        customAppIcons = icons,
                        hiddenApps = hidden,
                        mutedApps = muted
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
        private const val MAX_APPS_LOAD_RETRIES = 5
        private const val APPS_RETRY_BASE_DELAY_MS = 500L
        private const val ICON_LOAD_BATCH_SIZE = 16
        private const val WIDGET_DIAG_TAG = "FozWidgetDiag"
        private val WIDGET_RECREATE_DELAYS_MS = longArrayOf(500L, 5_000L, 30_000L)
    }
}

private data class IconUpdate(
    val packageName: String,
    val className: String,
    val drawable: Drawable
)

private data class LauncherSettingsSnapshot(
    val clockUse24h: Boolean,
    val iconSizeDp: Int,
    val swipeUpEnabled: Boolean,
    val swipeDownEnabled: Boolean,
    val widgetPanelHeightDp: Int,
    val themeMode: String,
    val useDynamicColor: Boolean,
    val notificationSummaryEnabled: Boolean,
    val quickRepliesEnabled: Boolean,
    val monochromeMode: Boolean,
    val suppressMonochromeDialog: Boolean,
    val usageLimitsEnabled: Boolean,
    val usageLimitHours: Int,
    val suggestAppsEnabled: Boolean,
    val sortByUsageEnabled: Boolean,
    val calendarPreviewEnabled: Boolean,
    val weatherEnabled: Boolean,
    val weatherImperial: Boolean,
    val mediaCardEnabled: Boolean,
    val calendarPreviewCount: Int,
    val calendarAgendaDays: Int,
    val hapticsEnabled: Boolean,
    val adBlockEnabled: Boolean,
    val iconPackPackageName: String?,
    val searchContactsEnabled: Boolean,
    val webAnswersEnabled: Boolean
)

private fun String.removeAccents(): String {
    return java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
}
