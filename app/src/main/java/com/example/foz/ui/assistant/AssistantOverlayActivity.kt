package com.example.foz.ui.assistant

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foz.manager.AssistantManager
import com.example.foz.ui.theme.FozTheme

/**
 * Translucent chat window launched by the floating bubble. Hosts the same
 * [AssistantPanel] used in the launcher, driven directly by the
 * [AssistantManager] singleton (no launcher state involved).
 */
class AssistantOverlayActivity : AppCompatActivity() {

    private val assistantManager by lazy { AssistantManager.getInstance(applicationContext) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (assistantManager.state.value.pendingPermission != null) {
            assistantManager.onPermissionResult(permissions.values.all { it })
        } else if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            assistantManager.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assistantManager.ensureModelLoaded()
        setContent {
            FozTheme(
                darkTheme = isSystemInDarkTheme(),
                dynamicColor = false,
                monochromeMode = false
            ) {
                OverlayContent(
                    manager = assistantManager,
                    onRequestPermissions = { permissions ->
                        permissionLauncher.launch(permissions)
                    },
                    onDismiss = {
                        assistantManager.stopListening()
                        assistantManager.stopSpeaking()
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun OverlayContent(
    manager: AssistantManager,
    onRequestPermissions: (Array<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val runtime by manager.state.collectAsState()
    val messages by manager.messages.collectAsState()

    LaunchedEffect(runtime.pendingPermission) {
        val pending = runtime.pendingPermission ?: return@LaunchedEffect
        val resolved = AssistantManager.resolvePermissionName(pending)
        val toRequest = if (resolved == Manifest.permission.WRITE_CALENDAR) {
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        } else {
            arrayOf(resolved)
        }
        onRequestPermissions(toRequest)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            AssistantPanel(
                messages = messages,
                thinking = runtime.thinking,
                modelStatus = runtime.status.name.lowercase(),
                isListening = runtime.isListening,
                partialText = runtime.partialText,
                voiceError = runtime.voiceError,
                partialAnswer = runtime.partialAnswer,
                pendingConfirmation = runtime.pendingConfirmation,
                onConfirmAction = { accepted -> manager.onConfirmationResult(accepted) },
                onStopGeneration = { manager.stopGeneration() },
                onSend = { text -> manager.sendMessage(text) },
                onClearConversation = { manager.clearConversation() },
                onStartVoice = {
                    val micGranted = manager.hasMicPermission()
                    if (micGranted) manager.startListening()
                    else onRequestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO))
                },
                onStopVoice = { manager.stopListening() },
                onDismiss = onDismiss
            )
        }
    }
}
