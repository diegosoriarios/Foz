package com.example.foz.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "launcher_prefs")

class PrefsManager(private val context: Context) {
    private val pinnedAppsKey = stringPreferencesKey("pinned_apps_ordered")
    private val legacyPinnedAppsKey = stringSetPreferencesKey("pinned_apps")
    private val widgetIdsKey = stringPreferencesKey("widget_ids_ordered")
    private val legacyWidgetIdsKey = stringSetPreferencesKey("widget_ids")
    private val widgetHeightsKey = stringPreferencesKey("widget_heights")
    private val launcherOnboardingDismissedKey = booleanPreferencesKey("launcher_onboarding_dismissed")
    private val initialOnboardingCompletedKey = booleanPreferencesKey("initial_onboarding_completed")
    private val clockUse24hKey = booleanPreferencesKey("clock_use_24h")
    private val appIconSizeDpKey = intPreferencesKey("app_icon_size_dp")
    private val drawerPaddingPercentKey = floatPreferencesKey("drawer_padding_percent")
    private val swipeUpEnabledKey = booleanPreferencesKey("swipe_up_enabled")
    private val swipeDownEnabledKey = booleanPreferencesKey("swipe_down_enabled")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val showNotificationsKey = booleanPreferencesKey("show_notifications")
    private val usageLimitsEnabledKey = booleanPreferencesKey("usage_limits_enabled")
    private val hapticsEnabledKey = booleanPreferencesKey("haptics_enabled")
    private val adBlockEnabledKey = booleanPreferencesKey("ad_block_enabled")
    private val monochromeModeKey = booleanPreferencesKey("monochrome_mode")
    private val suppressMonochromeDialogKey = booleanPreferencesKey("suppress_monochrome_dialog")
    private val useDynamicColorKey = booleanPreferencesKey("use_dynamic_color")
    private val iconPackPackageNameKey = stringPreferencesKey("icon_pack_package_name")
    private val customAppNamesKey = stringPreferencesKey("custom_app_names")
    private val customAppIconsKey = stringPreferencesKey("custom_app_icons")
    private val hiddenAppsKey = stringSetPreferencesKey("hidden_apps")
    private val lastWeatherKey = stringPreferencesKey("last_weather")

    val pinnedApps: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val orderedStr = prefs[pinnedAppsKey]
        if (orderedStr != null) {
            if (orderedStr.isEmpty()) emptyList() else orderedStr.split(",")
        } else {
            // fallback to legacy
            val legacy = prefs[legacyPinnedAppsKey] ?: emptySet()
            legacy.toList()
        }
    }

    val widgetIds: Flow<List<Int>> = context.dataStore.data.map { prefs ->
        val orderedStr = prefs[widgetIdsKey]
        if (orderedStr != null) {
            if (orderedStr.isEmpty()) emptyList() else orderedStr.split(",").mapNotNull { it.toIntOrNull() }
        } else {
            val legacy = prefs[legacyWidgetIdsKey] ?: emptySet()
            legacy.mapNotNull { it.toIntOrNull() }
        }
    }

    val widgetHeights: Flow<Map<Int, Int>> = context.dataStore.data.map { prefs ->
        val heightsStr = prefs[widgetHeightsKey] ?: ""
        if (heightsStr.isEmpty()) emptyMap() else {
            heightsStr.split(",").mapNotNull { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val id = parts[0].toIntOrNull()
                    val h = parts[1].toIntOrNull()
                    if (id != null && h != null) id to h else null
                } else null
            }.toMap()
        }
    }

    val launcherOnboardingDismissed: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[launcherOnboardingDismissedKey] ?: false
    }

    val initialOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[initialOnboardingCompletedKey] ?: false
    }

    val clockUse24h: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[clockUse24hKey] ?: true
    }

    val appIconSizeDp: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[appIconSizeDpKey] ?: 36
    }

    val drawerPaddingPercent: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[drawerPaddingPercentKey] ?: 0.5f
    }

    val swipeUpEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[swipeUpEnabledKey] ?: true
    }

    val swipeDownEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[swipeDownEnabledKey] ?: true
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey] ?: "system"
    }

    val showNotifications: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[showNotificationsKey] ?: true
    }

    val usageLimitsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[usageLimitsEnabledKey] ?: false
    }

    val monochromeMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[monochromeModeKey] ?: false
    }

    val suppressMonochromeDialog: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[suppressMonochromeDialogKey] ?: false
    }

    val hapticsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[hapticsEnabledKey] ?: true
    }

    val adBlockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[adBlockEnabledKey] ?: false
    }

    val useDynamicColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[useDynamicColorKey] ?: true
    }

    val iconPackPackageName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[iconPackPackageNameKey]
    }

    val customAppNames: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val str = prefs[customAppNamesKey] ?: ""
        if (str.isEmpty()) emptyMap() else {
            str.split(",").mapNotNull { pair ->
                val parts = pair.split("|")
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
        }
    }

    val customAppIcons: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val str = prefs[customAppIconsKey] ?: ""
        if (str.isEmpty()) emptyMap() else {
            str.split(",").mapNotNull { pair ->
                val parts = pair.split("|")
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
        }
    }

    val hiddenApps: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[hiddenAppsKey] ?: emptySet()
    }

    val lastWeather: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[lastWeatherKey]
    }

    suspend fun setAppPinned(packageName: String, pinned: Boolean) {
        context.dataStore.edit { prefs ->
            val orderedStr = prefs[pinnedAppsKey]
            val currentList = if (orderedStr != null) {
                if (orderedStr.isEmpty()) mutableListOf() else orderedStr.split(",").toMutableList()
            } else {
                (prefs[legacyPinnedAppsKey] ?: emptySet()).toMutableList()
            }
            
            if (pinned) {
                if (!currentList.contains(packageName)) {
                    currentList.add(packageName)
                }
            } else {
                currentList.remove(packageName)
            }
            prefs[pinnedAppsKey] = currentList.joinToString(",")
        }
    }

    suspend fun moveAppPinned(packageName: String, direction: Int) {
        context.dataStore.edit { prefs ->
            val orderedStr = prefs[pinnedAppsKey]
            val currentList = if (orderedStr != null) {
                if (orderedStr.isEmpty()) mutableListOf() else orderedStr.split(",").toMutableList()
            } else {
                (prefs[legacyPinnedAppsKey] ?: emptySet()).toMutableList()
            }
            
            val index = currentList.indexOf(packageName)
            if (index != -1) {
                val newIndex = index + direction
                if (newIndex >= 0 && newIndex < currentList.size) {
                    currentList.removeAt(index)
                    currentList.add(newIndex, packageName)
                    prefs[pinnedAppsKey] = currentList.joinToString(",")
                }
            }
        }
    }

    suspend fun saveWidgetIds(ids: List<Int>) {
        context.dataStore.edit { prefs ->
            prefs[widgetIdsKey] = ids.joinToString(",")
        }
    }

    suspend fun setWidgetHeight(widgetId: Int, heightDp: Int) {
        context.dataStore.edit { prefs ->
            val heightsStr = prefs[widgetHeightsKey] ?: ""
            val heightsMap = if (heightsStr.isEmpty()) mutableMapOf() else {
                heightsStr.split(",").mapNotNull { pair ->
                    val parts = pair.split(":")
                    if (parts.size == 2) {
                        val id = parts[0].toIntOrNull()
                        val h = parts[1].toIntOrNull()
                        if (id != null && h != null) id to h else null
                    } else null
                }.toMap().toMutableMap()
            }
            heightsMap[widgetId] = heightDp
            prefs[widgetHeightsKey] = heightsMap.map { "${it.key}:${it.value}" }.joinToString(",")
        }
    }

    suspend fun setLauncherOnboardingDismissed(dismissed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[launcherOnboardingDismissedKey] = dismissed
        }
    }

    suspend fun setInitialOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[initialOnboardingCompletedKey] = completed
        }
    }

    suspend fun setClockUse24h(use24h: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[clockUse24hKey] = use24h
        }
    }

    suspend fun setAppIconSizeDp(sizeDp: Int) {
        context.dataStore.edit { prefs ->
            prefs[appIconSizeDpKey] = sizeDp.coerceIn(24, 64)
        }
    }

    suspend fun setDrawerPaddingPercent(percent: Float) {
        context.dataStore.edit { prefs ->
            prefs[drawerPaddingPercentKey] = percent.coerceIn(0f, 1f)
        }
    }

    suspend fun setSwipeUpEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[swipeUpEnabledKey] = enabled
        }
    }

    suspend fun setSwipeDownEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[swipeDownEnabledKey] = enabled
        }
    }

    suspend fun setThemeMode(mode: String) {
        val normalized = when (mode) {
            "light", "dark", "system" -> mode
            else -> "system"
        }
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = normalized
        }
    }

    suspend fun setShowNotifications(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[showNotificationsKey] = show
        }
    }

    suspend fun setUsageLimitsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[usageLimitsEnabledKey] = enabled
        }
    }

    suspend fun setMonochromeMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[monochromeModeKey] = enabled
        }
    }

    suspend fun setSuppressMonochromeDialog(suppress: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[suppressMonochromeDialogKey] = suppress
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[hapticsEnabledKey] = enabled
        }
    }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[adBlockEnabledKey] = enabled
        }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[useDynamicColorKey] = enabled
        }
    }

    suspend fun setIconPackPackageName(packageName: String?) {
        context.dataStore.edit { prefs ->
            if (packageName == null) {
                prefs.remove(iconPackPackageNameKey)
            } else {
                prefs[iconPackPackageNameKey] = packageName
            }
        }
    }

    suspend fun setCustomAppName(packageName: String, name: String?) {
        context.dataStore.edit { prefs ->
            val str = prefs[customAppNamesKey] ?: ""
            val currentMap = if (str.isEmpty()) mutableMapOf() else {
                str.split(",").mapNotNull { pair ->
                    val parts = pair.split("|")
                    if (parts.size == 2) parts[0] to parts[1] else null
                }.toMap().toMutableMap()
            }
            if (name == null) {
                currentMap.remove(packageName)
            } else {
                currentMap[packageName] = name
            }
            prefs[customAppNamesKey] = currentMap.map { "${it.key}|${it.value}" }.joinToString(",")
        }
    }

    suspend fun setCustomAppIcon(packageName: String, drawableName: String?) {
        context.dataStore.edit { prefs ->
            val str = prefs[customAppIconsKey] ?: ""
            val currentMap = if (str.isEmpty()) mutableMapOf() else {
                str.split(",").mapNotNull { pair ->
                    val parts = pair.split("|")
                    if (parts.size == 2) parts[0] to parts[1] else null
                }.toMap().toMutableMap()
            }
            if (drawableName == null) {
                currentMap.remove(packageName)
            } else {
                currentMap[packageName] = drawableName
            }
            prefs[customAppIconsKey] = currentMap.map { "${it.key}|${it.value}" }.joinToString(",")
        }
    }

    suspend fun setAppHidden(packageName: String, hidden: Boolean) {
        context.dataStore.edit { prefs ->
            val currentSet = (prefs[hiddenAppsKey] ?: emptySet()).toMutableSet()
            if (hidden) {
                currentSet.add(packageName)
            } else {
                currentSet.remove(packageName)
            }
            prefs[hiddenAppsKey] = currentSet
        }
    }

    suspend fun saveWeather(weatherJson: String?) {
        context.dataStore.edit { prefs ->
            if (weatherJson == null) {
                prefs.remove(lastWeatherKey)
            } else {
                prefs[lastWeatherKey] = weatherJson
            }
        }
    }
}
