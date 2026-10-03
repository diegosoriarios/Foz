package com.example.foz.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.foz.ui.LauncherViewModel

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

    private val assistantModelPicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setAssistantModel(uri)
        }
    }

    private fun permissionLauncher() = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshAssistantPermissions()
    }

    private val micPermissionLauncher by lazy { permissionLauncher() }
    private val calendarReadPermissionLauncher by lazy { permissionLauncher() }
    private val calendarWritePermissionLauncher by lazy { permissionLauncher() }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAssistantPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.refreshIconPacks()
        viewModel.refreshAssistantPermissions()
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
                        onAssistantEnabledChanged = { viewModel.setAssistantEnabled(it) },
                        onAssistantSpeakChanged = { viewModel.setAssistantSpeakResponses(it) },
                        onAssistantVolumeButtonChanged = { viewModel.setAssistantVolumeButton(it) },
                        onAssistantModelPick = {
                            try {
                                assistantModelPicker.launch(arrayOf("*/*"))
                            } catch (_: Exception) {
                            }
                        },
                        onAssistantModelDelete = { viewModel.deleteAssistantModel() },
                        onAssistantOpenDownloadPage = { viewModel.openAssistantDownloadPage() },
                        onMicPermissionClick = {
                            if (!state.micPermissionGranted) {
                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onCalendarReadClick = {
                            if (!state.calendarReadGranted) {
                                calendarReadPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                            }
                        },
                        onCalendarWriteClick = {
                            if (!state.calendarWriteGranted) {
                                calendarWritePermissionLauncher.launch(android.Manifest.permission.WRITE_CALENDAR)
                            }
                        },
                        assistantFreeRamBytes = viewModel.assistantRamInfo().first,
                        assistantTotalRamBytes = viewModel.assistantRamInfo().second
                    )
                }
            }
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

