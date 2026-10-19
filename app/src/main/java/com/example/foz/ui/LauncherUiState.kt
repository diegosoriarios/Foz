package com.example.foz.ui

import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.model.WeatherModel
import com.example.foz.model.WidgetInfo
import android.content.Intent
import java.time.LocalDateTime

data class MediaState(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val packageName: String? = null,
    val artwork: android.graphics.Bitmap? = null
)

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
    val widgetPanelHeightDp: Int = 180,
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
    val quickRepliesEnabled: Boolean = true,
    val notificationSummaryEnabled: Boolean = true,
    val mutedApps: Set<String> = emptySet(),
    val searchContactsEnabled: Boolean = false,
    val webAnswersEnabled: Boolean = true,
    val searchContacts: List<com.example.foz.model.ContactInfo> = emptyList(),
    val instantAnswer: com.example.foz.model.InstantAnswer? = null,
    val instantAnswerLoading: Boolean = false,
    val monochromeMode: Boolean = false,
    val suppressMonochromeDialog: Boolean = false,
    val usageLimitsEnabled: Boolean = false,
    val usageLimitHours: Int = 4,
    val suggestAppsEnabled: Boolean = true,
    val sortByUsageEnabled: Boolean = false,
    val isUsageStatsPermissionGranted: Boolean = false,
    val usageCounts: Map<String, Int> = emptyMap(),
    val usageTodayMs: Long = 0L,
    val breakReminderDismissed: Boolean = false,
    val calendarPreviewEnabled: Boolean = true,
    val isCalendarPermissionGranted: Boolean = false,
    val calendarEvents: List<com.example.foz.model.CalendarEventInfo> = emptyList(),
    val showAgenda: Boolean = false,
    val folders: List<com.example.foz.model.FolderInfo> = emptyList(),
    val openFolderId: String? = null,
    val showFolderActions: String? = null,
    val folderPickerForApp: AppInfo? = null,
    val hapticsEnabled: Boolean = true,
    val adBlockEnabled: Boolean = false,
    val weather: WeatherModel? = null,
    val showWeatherForecast: Boolean = false,
    val showWidgetPicker: Boolean = false,
    val availableWidgets: List<WidgetInfo> = emptyList(),
    val iconPackPackageName: String? = null,
    val availableIconPacks: List<com.example.foz.model.IconPackInfo> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val customAppNames: Map<String, String> = emptyMap(),
    val customAppIcons: Map<String, String> = emptyMap(),
    val mediaState: MediaState? = null,
    val isNotificationListenerEnabled: Boolean = false,
    val mediaDismissed: Boolean = false,
    val activeNotifications: List<com.example.foz.model.NotificationModel> = emptyList(),
    val showDebugNotifications: Boolean = false,
    val showAppNotificationsPackage: String? = null,
    val errorMessage: String? = null,
    val appToRename: AppInfo? = null,
    val appToSelectIcon: AppInfo? = null,
    val vpnApprovalIntent: Intent? = null
) {
    val notificationsByPackage: Map<String, List<com.example.foz.model.NotificationModel>>
        get() = activeNotifications.groupBy { it.packageName }

    val notificationSummary: Map<String, List<com.example.foz.model.NotificationModel>>
        get() = notificationsByPackage.filter { (packageName, _) -> !mutedApps.contains(packageName) }

    val suggestedApps: List<AppInfo>
        get() {
            if (!suggestAppsEnabled || usageCounts.isEmpty()) return emptyList()
            return usageCounts.entries
                .asSequence()
                .filter { (packageName, count) ->
                    count > 0 &&
                        packageName != "com.example.foz" &&
                        !pinnedPackageNames.contains(packageName) &&
                        !hiddenApps.contains(packageName)
                }
                .sortedByDescending { it.value }
                .take(4)
                .mapNotNull { (packageName, _) -> allApps.find { it.packageName == packageName } }
                .toList()
        }

    val showBreakReminder: Boolean
        get() = usageLimitsEnabled &&
            usageTodayMs >= usageLimitHours * 60L * 60L * 1000L &&
            !breakReminderDismissed

    fun isDarkTheme(systemInDarkTheme: Boolean): Boolean {
        return when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> systemInDarkTheme
        }
    }
}
