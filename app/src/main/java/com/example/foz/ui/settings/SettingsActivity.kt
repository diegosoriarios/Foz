package com.example.foz.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
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
                        onSwipeDownEnabledChanged = { viewModel.setSwipeDownEnabled(it) },
                        onThemeModeChanged = { viewModel.setThemeMode(it) },
                        onUseDynamicColorChanged = { viewModel.setUseDynamicColor(it) },
                        onMonochromeModeChanged = { viewModel.setMonochromeMode(it) },
                        onSuppressMonochromeDialogChanged = { viewModel.setSuppressMonochromeDialog(it) },
                        onUsageLimitsChanged = { viewModel.setUsageLimitsEnabled(it) },
                        onHapticsChanged = { viewModel.setHapticsEnabled(it) },
                        onAdBlockEnabledChanged = { viewModel.setAdBlockEnabled(it) },
                        onIconPackChanged = { viewModel.setIconPack(it) },
                        onOpenLauncherSettings = { openDefaultLauncherSettings() },
                        onOpenSystemAccessibilitySettings = { openSystemAccessibilitySettings() },
                        onOpenNotificationListenerSettings = { viewModel.openNotificationListenerSettings() },
                        widgetSummaries = remember(state.widgetIds) { viewModel.widgetSummaries() },
                        onWidgetPanelHeightChanged = { viewModel.setWidgetPanelHeight(it) },
                        onAddWidget = { viewModel.openWidgetPicker() },
                        onRemoveWidget = { widgetId -> viewModel.removeWidgetId(widgetId) },
                        onMoveWidget = { widgetId, direction -> viewModel.moveWidget(widgetId, direction) },
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

