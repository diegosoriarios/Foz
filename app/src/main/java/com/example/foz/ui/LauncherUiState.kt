package com.example.foz.ui

import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import java.time.LocalDateTime

data class LauncherUiState(
    val now: LocalDateTime = LocalDateTime.now(),
    val allApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val pinnedPackageNames: Set<String> = emptySet(),
    val pinnedApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val drawerOpen: Boolean = false,
    val swipeUpPanelOpen: Boolean = false,
    val selectedApp: AppInfo? = null,
    val selectedAppShortcuts: List<AppShortcut> = emptyList(),
    val sectionIndexes: Map<Char, Int> = emptyMap(),
    val widgetIds: Set<Int> = emptySet(),
    val wallpaperChangeToken: Int = 0,
    val requestedSectionLetter: Char? = null,
    val isLauncherDefault: Boolean = true,
    val launcherStatusChecked: Boolean = false,
    val launcherOnboardingDismissed: Boolean = false,
    val initialOnboardingCompleted: Boolean = false,
    val onboardingSelectedPackages: Set<String> = emptySet(),
    val clockUse24h: Boolean = true,
    val appIconSizeDp: Int = 36,
    val swipeUpEnabled: Boolean = true,
    val swipeDownEnabled: Boolean = true,
    val themeMode: String = "system",
    val showNotifications: Boolean = true,
    val usageLimitsEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true
)
