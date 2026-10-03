package com.example.foz.ui.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.foz.ui.LauncherViewModel

class AssistantSettingsActivity : AppCompatActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    private val vpnApprovalLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.setAdBlockEnabled(result.resultCode == RESULT_OK)
    }

    private val assistantModelPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setAssistantModel(uri)
        }
    }

    private fun permissionLauncher() = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshAssistantPermissions()
    }

    private val micPermissionLauncher by lazy { permissionLauncher() }
    private val calendarReadPermissionLauncher by lazy { permissionLauncher() }
    private val calendarWritePermissionLauncher by lazy { permissionLauncher() }
    private val contactsPermissionLauncher by lazy { permissionLauncher() }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAssistantPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    AssistantSettingsScreen(
                        state = state,
                        onClose = { finish() },
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
                        onContactsClick = {
                            if (!state.contactsReadGranted) {
                                contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                            }
                        },
                        assistantFreeRamBytes = viewModel.assistantRamInfo().first,
                        assistantTotalRamBytes = viewModel.assistantRamInfo().second
                    )
                }
            }
        }
    }
}
