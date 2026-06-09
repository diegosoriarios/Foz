package com.example.foz.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

}
