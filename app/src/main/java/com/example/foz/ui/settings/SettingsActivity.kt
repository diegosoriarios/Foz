package com.example.foz.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.appwidget.AppWidgetManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import com.example.foz.model.WidgetSummary
import com.example.foz.ui.LauncherViewModel
import com.example.foz.ui.home.components.WidgetPickerDialog

class SettingsActivity : AppCompatActivity() {
    private val viewModel: LauncherViewModel by viewModels()
    private var pendingContactsSearch = false
    private var pendingCalendarPreview = false
    private var pendingWeatherEnable = false

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingContactsSearch) {
            viewModel.setSearchContactsEnabled(true)
        }
        pendingContactsSearch = false
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && pendingWeatherEnable) {
            viewModel.setWeatherEnabled(true)
            viewModel.refreshWeather()
        } else if (!granted) {
            viewModel.setWeatherEnabled(false)
        }
        pendingWeatherEnable = false
    }

    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCalendarPermissionResult(granted)
        if (granted && pendingCalendarPreview) {
            viewModel.setCalendarPreviewEnabled(true)
        }
        pendingCalendarPreview = false
    }

    private val vpnApprovalLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // User approved VPN, try starting it again
            viewModel.setAdBlockEnabled(true)
        } else {
            // User denied VPN
            viewModel.setAdBlockEnabled(false)
        }
    }

    private val widgetBindLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val widgetId = result.data
            ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1

        if (result.resultCode == RESULT_OK && widgetId != -1) {
            val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
            if (info?.configure != null) {
                startConfigureWidget(widgetId)
            }
            viewModel.addWidgetId(widgetId)
        } else if (widgetId != -1) {
            viewModel.deleteWidgetId(widgetId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.refreshIconPacks()
        setContent {
            val state by viewModel.uiState.collectAsState()

            androidx.compose.runtime.LaunchedEffect(state.vpnApprovalIntent) {
                state.vpnApprovalIntent?.let { intent ->
                    vpnApprovalLauncher.launch(intent)
                    viewModel.clearVpnApprovalIntent()
                }
            }

            androidx.compose.runtime.SideEffect {
                if (state.monochromeMode) {
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    window.setFormat(android.graphics.PixelFormat.OPAQUE)
                } else {
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                }
            }

            val darkTheme = state.monochromeMode || state.isDarkTheme(androidx.compose.foundation.isSystemInDarkTheme())
            com.example.foz.ui.theme.FozTheme(
                darkTheme = darkTheme,
                dynamicColor = state.useDynamicColor && !state.monochromeMode,
                monochromeMode = state.monochromeMode
            ) {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        state = state,
                        onClose = { finish() },
                        onClockUse24hChanged = { viewModel.setClockUse24h(it) },
                        onIconSizeChanged = { viewModel.setAppIconSizeDp(it) },
                        onSwipeUpEnabledChanged = { viewModel.setSwipeUpEnabled(it) },
                        onSwipeDownEnabledChanged = { viewModel.setSwipeDownEnabled(it) },
                        onThemeModeChanged = { viewModel.setThemeMode(it) },
                        onUseDynamicColorChanged = { viewModel.setUseDynamicColor(it) },
                        onMonochromeModeChanged = { viewModel.setMonochromeMode(it) },
                        onSuppressMonochromeDialogChanged = { viewModel.setSuppressMonochromeDialog(it) },
                        onUsageLimitsChanged = { viewModel.setUsageLimitsEnabled(it) },
                        onHapticsChanged = { viewModel.setHapticsEnabled(it) },
                        onAdBlockEnabledChanged = { viewModel.setAdBlockEnabled(it) },
                        onQuickRepliesChanged = { viewModel.setQuickRepliesEnabled(it) },
                        onNotificationSummaryChanged = { viewModel.setNotificationSummaryEnabled(it) },
                        onContactsSearchChanged = { enabled -> handleContactsSearchToggle(enabled) },
                        onWebAnswersChanged = { viewModel.setWebAnswersEnabled(it) },
                        onOpenUsageAccessSettings = { viewModel.openUsageAccessSettings() },
                        onUsageLimitHoursChanged = { hours -> viewModel.setUsageLimitHours(hours) },
                        onSuggestAppsChanged = { viewModel.setSuggestAppsEnabled(it) },
                        onSortByUsageChanged = { viewModel.setSortByUsageEnabled(it) },
                        onWeatherEnabledChanged = { enabled -> handleWeatherToggle(enabled) },
                        onWeatherUnitsChanged = { viewModel.setWeatherImperial(it) },
                        onMediaCardEnabledChanged = { viewModel.setMediaCardEnabled(it) },
                        onRequestCalendarPermission = { requestCalendarAccess() },
                        onCalendarPreviewChanged = { enabled -> handleCalendarPreviewToggle(enabled) },
                        onCalendarPreviewCountChanged = { viewModel.setCalendarPreviewCount(it) },
                        onCalendarAgendaDaysChanged = { viewModel.setCalendarAgendaDays(it) },
                        onIconPackChanged = { viewModel.setIconPack(it) },
                        onOpenLauncherSettings = { openDefaultLauncherSettings() },
                        onOpenSystemAccessibilitySettings = { openSystemAccessibilitySettings() },
                        onOpenNotificationListenerSettings = { viewModel.openNotificationListenerSettings() },
                        widgetSummaries = remember(state.widgetIds) { viewModel.widgetSummaries() },
                        onWidgetPanelHeightChanged = { viewModel.setWidgetPanelHeight(it) },
                        onAddWidget = { viewModel.openWidgetPicker() },
                        onRemoveWidget = { widgetId -> viewModel.removeWidgetId(widgetId) },
                        onMoveWidget = { widgetId, direction -> viewModel.moveWidget(widgetId, direction) },
                        onResizeWidget = { widgetId, height -> viewModel.resizeWidget(widgetId, height) },
                        onConfigureWidget = { widgetId -> startConfigureWidget(widgetId) }
                    )

                    if (state.showWidgetPicker) {
                        WidgetPickerDialog(
                            widgets = state.availableWidgets,
                            onWidgetSelected = { widgetInfo ->
                                viewModel.dismissWidgetPicker()
                                requestWidgetBind(widgetInfo.providerInfo)
                            },
                            onDismiss = { viewModel.dismissWidgetPicker() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh launcher/notification/usage/calendar access status when returning from system settings
        viewModel.refreshLauncherRoleStatus()
        viewModel.refreshUsageStats()
        viewModel.refreshCalendarEvents()
    }

    private fun handleContactsSearchToggle(enabled: Boolean) {
        if (!enabled) {
            viewModel.setSearchContactsEnabled(false)
            return
        }
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.setSearchContactsEnabled(true)
        } else {
            pendingContactsSearch = true
            contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
        }
    }

    private fun handleWeatherToggle(enabled: Boolean) {
        if (!enabled) {
            viewModel.setWeatherEnabled(false)
            return
        }
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.setWeatherEnabled(true)
            viewModel.refreshWeather()
        } else {
            pendingWeatherEnable = true
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun handleCalendarPreviewToggle(enabled: Boolean) {
        if (!enabled) {
            viewModel.setCalendarPreviewEnabled(false)
            return
        }
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.setCalendarPreviewEnabled(true)
        } else {
            pendingCalendarPreview = true
            calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
        }
    }

    private fun requestCalendarAccess() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
        }
    }

    private fun requestWidgetBind(info: android.appwidget.AppWidgetProviderInfo) {
        val widgetId = viewModel.allocateWidgetId()
        val bound = AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(widgetId, info.provider)
        if (bound) {
            if (info.configure != null) {
                startConfigureWidget(widgetId)
            }
            viewModel.addWidgetId(widgetId)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            widgetBindLauncher.launch(intent)
        }
    }

    private fun startConfigureWidget(widgetId: Int) {
        try {
            val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId) ?: return
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun openSystemAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            // Fallback
        }
    }

    private fun openDefaultLauncherSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (e2: Exception) {
                // Fallback handled silently
            }
        }
    }
}

