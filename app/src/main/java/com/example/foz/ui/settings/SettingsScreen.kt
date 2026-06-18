package com.example.foz.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.foz.model.IconPackInfo
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.applist.AppIcon
import com.example.foz.ui.theme.FozTheme

sealed class SettingsItem {
    data class Header(val title: String) : SettingsItem()
    data class Toggle(val title: String, val value: Boolean, val onChange: (Boolean) -> Unit) : SettingsItem()
    data class Action(val title: String, val description: String? = null, val onClick: () -> Unit) : SettingsItem()
    data class Choice(val title: String, val options: List<String>, val selected: String, val onSelect: (String) -> Unit) : SettingsItem()
    data class Slider(val title: String, val value: Float, val range: ClosedFloatingPointRange<Float>, val steps: Int, val onValueChange: (Float) -> Unit) : SettingsItem()
}

@Composable
fun SettingsScreen(
    state: LauncherUiState,
    onClose: () -> Unit,
    onClockUse24hChanged: (Boolean) -> Unit,
    onIconSizeChanged: (Int) -> Unit,
    onSwipeDownEnabledChanged: (Boolean) -> Unit,
    onThemeModeChanged: (String) -> Unit,
    onUseDynamicColorChanged: (Boolean) -> Unit,
    onMonochromeModeChanged: (Boolean) -> Unit,
    onSuppressMonochromeDialogChanged: (Boolean) -> Unit,
    onUsageLimitsChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onIconPackChanged: (String?) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenSystemAccessibilitySettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit
) {
    var showIconPackModal by remember { mutableStateOf(false) }
    var showThemeModal by remember { mutableStateOf(false) }
    var showMonochromeDialog by remember { mutableStateOf(false) }
    var pendingMonochromeValue by remember { mutableStateOf(false) }

    val handleMonochromeChange = { newValue: Boolean ->
        if (state.suppressMonochromeDialog) {
            onMonochromeModeChanged(newValue)
        } else {
            pendingMonochromeValue = newValue
            showMonochromeDialog = true
        }
    }

    val items = listOfNotNull(
        SettingsItem.Header("Appearance"),
        SettingsItem.Slider(
            title = "Icon size",
            value = state.appIconSizeDp.toFloat(),
            range = 28f..48f,
            steps = 4,
            onValueChange = { onIconSizeChanged(it.toInt()) }
        ),
        SettingsItem.Action(
            title = "Icon pack",
            description = state.availableIconPacks.find { it.packageName == state.iconPackPackageName }?.name ?: "Default Icons",
            onClick = { showIconPackModal = true }
        ),
        SettingsItem.Action(
            title = "Theme",
            description = state.themeMode.replaceFirstChar { it.uppercase() },
            onClick = { showThemeModal = true }
        ),
        SettingsItem.Toggle("Dynamic color (Material You)", state.useDynamicColor, onUseDynamicColorChanged),
        SettingsItem.Toggle("Black and white mode", state.monochromeMode, handleMonochromeChange),

        SettingsItem.Header("Gestures"),
        SettingsItem.Toggle("Swipe down for notifications", state.swipeDownEnabled, onSwipeDownEnabledChanged),

        SettingsItem.Header("System"),
        SettingsItem.Toggle("24-hour clock", state.clockUse24h, onClockUse24hChanged),
        SettingsItem.Action("Change device launcher", onClick = onOpenLauncherSettings),
        SettingsItem.Action(
            title = "Media controls",
            description = if (state.isNotificationListenerEnabled) "Enabled" else "Disabled (Tap to enable)",
            onClick = onOpenNotificationListenerSettings
        ),
        SettingsItem.Toggle("Usage limits", state.usageLimitsEnabled, onUsageLimitsChanged),
        SettingsItem.Toggle("Enable haptics", state.hapticsEnabled, onHapticsChanged)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
            Surface(onClick = onClose, shape = MaterialTheme.shapes.medium) {
                Text(text = "Close", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is SettingsItem.Header -> SettingsHeaderRow(item.title)
                    is SettingsItem.Toggle -> SettingsToggleRow(item.title, item.value, item.onChange)
                    is SettingsItem.Action -> SettingsActionRow(item.title, item.description, item.onClick)
                    is SettingsItem.Choice -> SettingsChoiceRow(item.title, item.options, item.selected, item.onSelect)
                    is SettingsItem.Slider -> SettingsSliderRow(item.title, item.value, item.range, item.steps, item.onValueChange)
                }
            }
        }
    }

    if (showIconPackModal) {
        IconPackSelectionModal(
            packs = state.availableIconPacks,
            selected = state.iconPackPackageName,
            onSelect = {
                onIconPackChanged(it)
                showIconPackModal = false
            },
            onDismiss = { showIconPackModal = false }
        )
    }

    if (showThemeModal) {
        ThemeSelectionModal(
            selected = state.themeMode,
            onSelect = {
                onThemeModeChanged(it)
                showThemeModal = false
            },
            onDismiss = { showThemeModal = false }
        )
    }

    if (showMonochromeDialog) {
        MonochromeInfoDialog(
            onConfirm = { suppress ->
                if (suppress) onSuppressMonochromeDialogChanged(true)
                onMonochromeModeChanged(pendingMonochromeValue)
                showMonochromeDialog = false
            },
            onGoToSettings = { suppress ->
                if (suppress) onSuppressMonochromeDialogChanged(true)
                onMonochromeModeChanged(pendingMonochromeValue)
                onOpenSystemAccessibilitySettings()
                showMonochromeDialog = false
            },
            onDismiss = { showMonochromeDialog = false }
        )
    }
}

@Composable
private fun MonochromeInfoDialog(
    onConfirm: (Boolean) -> Unit,
    onGoToSettings: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
            ) {
                Text(
                    text = "Black and White Mode",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "This will only affect the launcher. If you want to have a full black and white device, you need to enable it in system settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Text(
                        text = "Don't show me anymore",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { dontShowAgain = !dontShowAgain }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    androidx.compose.material3.TextButton(onClick = { onConfirm(dontShowAgain) }) {
                        Text("Ignore")
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    androidx.compose.material3.Button(onClick = { onGoToSettings(dontShowAgain) }) {
                        Text("Settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeaderRow(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun ThemeSelectionModal(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
            ) {
                Text(
                    text = "Select Theme",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val options = listOf("system", "light", "dark")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        val isSelected = selected == option
                        Surface(
                            onClick = { onSelect(option) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = option.replaceFirstChar { it.uppercase() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Cancel",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun IconPackSelectionModal(
    packs: List<IconPackInfo>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
            ) {
                Text(
                    text = "Select Icon Pack",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        IconPackOptionRow(
                            name = "Default Icons",
                            icon = null,
                            isSelected = selected == null,
                            onClick = { onSelect(null) }
                        )
                    }

                    items(packs) { pack ->
                        IconPackOptionRow(
                            name = pack.name,
                            icon = pack.icon,
                            isSelected = selected == pack.packageName,
                            onClick = { onSelect(pack.packageName) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Cancel",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun IconPackOptionRow(
    name: String,
    icon: android.graphics.drawable.Drawable?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                AppIcon(drawable = icon, contentDescription = name, modifier = Modifier.size(32.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small
                        )
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "${value.toInt()} dp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Switch(checked = value, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SettingsActionRow(title: String, description: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.labelMedium)
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SettingsChoiceRow(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    val isSelected = selected == option
                    Surface(
                        onClick = { onSelect(option) },
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = option.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true, name = "System Theme")
@Composable
fun SettingsScreenPreview() {
    SettingsScreenPreviewContent(themeMode = "system")
}

@Preview(showBackground = true, name = "Light Theme")
@Composable
fun SettingsScreenLightPreview() {
    SettingsScreenPreviewContent(themeMode = "light")
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
fun SettingsScreenDarkPreview() {
    SettingsScreenPreviewContent(themeMode = "dark")
}

@Composable
private fun SettingsScreenPreviewContent(themeMode: String) {
    val state = LauncherUiState(themeMode = themeMode)
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    FozTheme(darkTheme = isDark, monochromeMode = state.monochromeMode) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(
                state = state,
                onClose = {},
                onClockUse24hChanged = {},
                onIconSizeChanged = {},
                onSwipeDownEnabledChanged = {},
                onThemeModeChanged = {},
                onUseDynamicColorChanged = {},
                onMonochromeModeChanged = {},
                onSuppressMonochromeDialogChanged = {},
                onUsageLimitsChanged = {},
                onHapticsChanged = {},
                onIconPackChanged = {},
                onOpenLauncherSettings = {},
                onOpenSystemAccessibilitySettings = {},
                onOpenNotificationListenerSettings = {}
            )
        }
    }
}
