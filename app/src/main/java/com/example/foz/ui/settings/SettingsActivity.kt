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
        viewModel.refreshIconPacks()
        setContent {
            val state by viewModel.uiState.collectAsState()
            SettingsScreen(
                state = state,
                onClose = { finish() },
                onClockUse24hChanged = { viewModel.setClockUse24h(it) },
                onIconSizeChanged = { viewModel.setAppIconSizeDp(it) },
                onSwipeDownEnabledChanged = { viewModel.setSwipeDownEnabled(it) },
                onThemeModeChanged = { viewModel.setThemeMode(it) },
                onUsageLimitsChanged = { viewModel.setUsageLimitsEnabled(it) },
                onHapticsChanged = { viewModel.setHapticsEnabled(it) },
                onIconPackChanged = { viewModel.setIconPack(it) },
                onOpenLauncherSettings = { openDefaultLauncherSettings() }
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
}

