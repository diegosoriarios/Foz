package com.example.foz.ui

import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.model.WeatherModel
import com.example.foz.model.WidgetInfo
import java.time.LocalDateTime

data class LauncherUiState(
    val now: LocalDateTime = LocalDateTime.now(),
    val allApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val pinnedPackageNames: List<String> = emptyList(),
    val pinnedApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val drawerOpen: Boolean = false,
    val swipeUpPanelOpen: Boolean = false,
    val selectedApp: AppInfo? = null,
    val selectedAppShortcuts: List<AppShortcut> = emptyList(),
    val sectionIndexes: Map<Char, Int> = emptyMap(),
    val widgetIds: List<Int> = emptyList(),
    val widgetHeights: Map<Int, Int> = emptyMap(),
    val selectedWidgetId: Int? = null,
    val wallpaperChangeToken: Int = 0,
    val requestedSectionLetter: Char? = null,
    val isLauncherDefault: Boolean = true,
    val launcherStatusChecked: Boolean = false,
    val launcherOnboardingDismissed: Boolean = false,
    val initialOnboardingCompleted: Boolean = false,
    val onboardingSelectedPackages: Set<String> = emptySet(),
    val clockUse24h: Boolean = true,
    val appIconSizeDp: Int = 32,
    val drawerPaddingPercent: Float = 0.5f,
    val swipeUpEnabled: Boolean = true,
    val swipeDownEnabled: Boolean = true,
    val themeMode: String = "system",
    val useDynamicColor: Boolean = true,
    val showNotifications: Boolean = true,
    val usageLimitsEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val weather: WeatherModel? = null,
    val showWidgetPicker: Boolean = false,
    val availableWidgets: List<WidgetInfo> = emptyList(),
    val iconPackPackageName: String? = null,
    val availableIconPacks: List<com.example.foz.model.IconPackInfo> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val customAppNames: Map<String, String> = emptyMap(),
    val customAppIcons: Map<String, String> = emptyMap()
) {
    fun isDarkTheme(systemInDarkTheme: Boolean): Boolean {
        return when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> systemInDarkTheme
        }
    }
}
