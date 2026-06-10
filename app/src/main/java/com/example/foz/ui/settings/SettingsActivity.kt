package com.example.foz.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.foz.ui.LauncherViewModel
import com.example.foz.ui.home.SettingsScreen

class SettingsActivity : AppCompatActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen(
                state = viewModel.uiState.value,
                onClose = { finish() },
                onClockUse24hChanged = { viewModel.setClockUse24h(it) },
                onIconSizeChanged = { viewModel.setAppIconSizeDp(it) },
                onSwipeUpEnabledChanged = { viewModel.setSwipeUpEnabled(it) },
                onSwipeDownEnabledChanged = { viewModel.setSwipeDownEnabled(it) },
                onThemeModeChanged = { viewModel.setThemeMode(it) }
            )
        }
    }
}
