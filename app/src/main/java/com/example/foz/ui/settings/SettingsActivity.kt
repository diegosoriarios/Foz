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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsState()
            SettingsScreen(
                state = state,
                onClose = { finish() },
                onClockUse24hChanged = { viewModel.setClockUse24h(it) },
                onIconSizeChanged = { viewModel.setAppIconSizeDp(it) },
                onSwipeUpEnabledChanged = { viewModel.setSwipeUpEnabled(it) },
                onSwipeDownEnabledChanged = { viewModel.setSwipeDownEnabled(it) },
                onThemeModeChanged = { viewModel.setThemeMode(it) },
                onShowNotificationsChanged = { viewModel.setShowNotifications(it) },
                onUsageLimitsChanged = { viewModel.setUsageLimitsEnabled(it) },
                onHapticsChanged = { viewModel.setHapticsEnabled(it) },
                onIconPackChanged = { viewModel.setIconPack(it) },
                onOpenLauncherSettings = { openDefaultLauncherSettings() },
                onOpenWidgetPicker = { openWidgetPicker() }
            )
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

    private fun openWidgetPicker() {
        // Here we could start a widget picker or something else.
        // Actually, Foz is already allocating widgets in the swipe up panel.
        // So this might just finish SettingsActivity and tell LauncherViewModel to open swipe up panel.
        viewModel.openSwipeUpPanel()
        finish()
    }
}

