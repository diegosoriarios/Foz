package com.example.foz.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

class PrefsManager(
    private val context: Context,
    private val dataStoreOverride: DataStore<Preferences>? = null
) {
    private val dataStore = dataStoreOverride ?: context.dataStore

    private val pinnedAppsKey = stringPreferencesKey("pinned_apps_ordered")
    private val legacyPinnedAppsKey = stringSetPreferencesKey("pinned_apps")
    private val widgetIdsKey = stringSetPreferencesKey("widget_ids")
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

    val pinnedApps: Flow<List<String>> = dataStore.data.map { prefs ->
        val orderedStr = prefs[pinnedAppsKey]
        if (orderedStr != null) {
            if (orderedStr.isEmpty()) emptyList() else orderedStr.split(",")
        } else {
            // fallback to legacy
            val legacy = prefs[legacyPinnedAppsKey] ?: emptySet()
            legacy.toList()
        }
    }

    val widgetIds: Flow<Set<Int>> = dataStore.data.map { prefs ->
        (prefs[widgetIdsKey] ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
    }

    val launcherOnboardingDismissed: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[launcherOnboardingDismissedKey] ?: false
    }

    val initialOnboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[initialOnboardingCompletedKey] ?: false
    }

    val clockUse24h: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[clockUse24hKey] ?: true
    }

    val appIconSizeDp: Flow<Int> = dataStore.data.map { prefs ->
        prefs[appIconSizeDpKey] ?: 36
    }

    val drawerPaddingPercent: Flow<Float> = dataStore.data.map { prefs ->
        prefs[drawerPaddingPercentKey] ?: 0.5f
    }

    val swipeUpEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[swipeUpEnabledKey] ?: true
    }

    val swipeDownEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[swipeDownEnabledKey] ?: true
    }

    val themeMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[themeModeKey] ?: "system"
    }

    val showNotifications: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[showNotificationsKey] ?: true
    }

    val usageLimitsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[usageLimitsEnabledKey] ?: false
    }

    val hapticsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[hapticsEnabledKey] ?: true
    }

    suspend fun setAppPinned(packageName: String, pinned: Boolean) {
        dataStore.edit { prefs ->
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
        dataStore.edit { prefs ->
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

    suspend fun saveWidgetIds(ids: Set<Int>) {
        dataStore.edit { prefs ->
            prefs[widgetIdsKey] = ids.map { it.toString() }.toSet()
        }
    }

    suspend fun setLauncherOnboardingDismissed(dismissed: Boolean) {
        dataStore.edit { prefs ->
            prefs[launcherOnboardingDismissedKey] = dismissed
        }
    }

    suspend fun setInitialOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[initialOnboardingCompletedKey] = completed
        }
    }

    suspend fun setClockUse24h(use24h: Boolean) {
        dataStore.edit { prefs ->
            prefs[clockUse24hKey] = use24h
        }
    }

    suspend fun setAppIconSizeDp(sizeDp: Int) {
        dataStore.edit { prefs ->
            prefs[appIconSizeDpKey] = sizeDp.coerceIn(24, 64)
        }
    }

    suspend fun setDrawerPaddingPercent(percent: Float) {
        dataStore.edit { prefs ->
            prefs[drawerPaddingPercentKey] = percent.coerceIn(0f, 1f)
        }
    }

    suspend fun setSwipeUpEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[swipeUpEnabledKey] = enabled
        }
    }

    suspend fun setSwipeDownEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[swipeDownEnabledKey] = enabled
        }
    }

    suspend fun setThemeMode(mode: String) {
        val normalized = when (mode) {
            "light", "dark", "system" -> mode
            else -> "system"
        }
        dataStore.edit { prefs ->
            prefs[themeModeKey] = normalized
        }
    }

    suspend fun setShowNotifications(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[showNotificationsKey] = show
        }
    }

    suspend fun setUsageLimitsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[usageLimitsEnabledKey] = enabled
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[hapticsEnabledKey] = enabled
        }
    }

}
