package com.example.foz.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "launcher_prefs")

class PrefsManager(private val context: Context) {
    private val pinnedAppsKey = stringSetPreferencesKey("pinned_apps")
    private val widgetIdsKey = stringSetPreferencesKey("widget_ids")
    private val launcherOnboardingDismissedKey = booleanPreferencesKey("launcher_onboarding_dismissed")
    private val initialOnboardingCompletedKey = booleanPreferencesKey("initial_onboarding_completed")
    private val clockUse24hKey = booleanPreferencesKey("clock_use_24h")
    private val appIconSizeDpKey = intPreferencesKey("app_icon_size_dp")
    private val swipeUpEnabledKey = booleanPreferencesKey("swipe_up_enabled")
    private val swipeDownEnabledKey = booleanPreferencesKey("swipe_down_enabled")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val showNotificationsKey = booleanPreferencesKey("show_notifications")
    private val usageLimitsEnabledKey = booleanPreferencesKey("usage_limits_enabled")
    private val hapticsEnabledKey = booleanPreferencesKey("haptics_enabled")

    val pinnedApps: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[pinnedAppsKey] ?: emptySet()
    }

    val widgetIds: Flow<Set<Int>> = context.dataStore.data.map { prefs ->
        (prefs[widgetIdsKey] ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
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

    val hapticsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[hapticsEnabledKey] ?: true
    }

    suspend fun setAppPinned(packageName: String, pinned: Boolean) {
        context.dataStore.edit { prefs ->
            val current = (prefs[pinnedAppsKey] ?: emptySet()).toMutableSet()
            if (pinned) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }
            prefs[pinnedAppsKey] = current
        }
    }

    suspend fun saveWidgetIds(ids: Set<Int>) {
        context.dataStore.edit { prefs ->
            prefs[widgetIdsKey] = ids.map { it.toString() }.toSet()
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

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[hapticsEnabledKey] = enabled
        }
    }

}
