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
    val selectedApp: AppInfo? = null,
    val selectedAppShortcuts: List<AppShortcut> = emptyList(),
    val sectionIndexes: Map<Char, Int> = emptyMap(),
    val widgetIds: Set<Int> = emptySet()
)
