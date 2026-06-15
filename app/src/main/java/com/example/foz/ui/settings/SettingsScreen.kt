package com.example.foz.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.foz.model.IconPackInfo
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.applist.AppIcon
import com.example.foz.ui.theme.FozTheme

sealed class SettingsItem {
    data class Toggle(val title: String, val value: Boolean, val onChange: (Boolean) -> Unit) : SettingsItem()
    data class Action(val title: String, val description: String? = null, val onClick: () -> Unit) : SettingsItem()
    data class Choice(val title: String, val options: List<String>, val selected: String, val onSelect: (String) -> Unit) : SettingsItem()
    data class NumberChoice(val title: String, val description: String, val options: List<Int>, val selected: Int, val onSelect: (Int) -> Unit) : SettingsItem()
    data class IconPackChoice(val title: String, val packs: List<IconPackInfo>, val selected: String?, val onSelect: (String?) -> Unit) : SettingsItem()
}

@Composable
fun SettingsScreen(
    state: LauncherUiState,
    onClose: () -> Unit,
    onClockUse24hChanged: (Boolean) -> Unit,
    onIconSizeChanged: (Int) -> Unit,
    onSwipeUpEnabledChanged: (Boolean) -> Unit,
    onSwipeDownEnabledChanged: (Boolean) -> Unit,
    onThemeModeChanged: (String) -> Unit,
    onShowNotificationsChanged: (Boolean) -> Unit,
    onUsageLimitsChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onIconPackChanged: (String?) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenWidgetPicker: () -> Unit
) {
    val items = listOf(
        SettingsItem.Toggle("24-hour clock", state.clockUse24h, onClockUse24hChanged),
        SettingsItem.NumberChoice("Icon size", "${state.appIconSizeDp} dp", listOf(28, 32, 36, 40, 44, 48), state.appIconSizeDp, onIconSizeChanged),
        SettingsItem.IconPackChoice("Icon pack", state.availableIconPacks, state.iconPackPackageName, onIconPackChanged),
        SettingsItem.Toggle("Swipe up gesture", state.swipeUpEnabled, onSwipeUpEnabledChanged),
        SettingsItem.Toggle("Swipe down gesture", state.swipeDownEnabled, onSwipeDownEnabledChanged),
        SettingsItem.Choice("Theme", listOf("system", "light", "dark"), state.themeMode, onThemeModeChanged),
        SettingsItem.Action("Change device launcher", onClick = onOpenLauncherSettings),
        SettingsItem.Action("Select widgets", onClick = onOpenWidgetPicker),
        SettingsItem.Toggle("Show notifications", state.showNotifications, onShowNotificationsChanged),
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
                    is SettingsItem.Toggle -> SettingsToggleRow(item.title, item.value, item.onChange)
                    is SettingsItem.Action -> SettingsActionRow(item.title, item.description, item.onClick)
                    is SettingsItem.Choice -> SettingsChoiceRow(item.title, item.options, item.selected, item.onSelect)
                    is SettingsItem.NumberChoice -> SettingsNumberChoiceRow(item.title, item.description, item.options, item.selected, item.onSelect)
                    is SettingsItem.IconPackChoice -> SettingsIconPackRow(item.title, item.packs, item.selected, item.onSelect)
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Switch(checked = value, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SettingsActionRow(title: String, description: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(text = title, style = MaterialTheme.typography.titleMedium)
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
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNumberChoiceRow(title: String, description: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    Surface(onClick = { onSelect(option) }, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = option.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            color = if (selected == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsIconPackRow(
    title: String,
    packs: List<IconPackInfo>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (packs.isEmpty()) 40.dp else 160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        onClick = { onSelect(null) },
                        shape = MaterialTheme.shapes.small,
                        color = if (selected == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Default Icons",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (selected == null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                items(packs) { pack ->
                    val isSelected = selected == pack.packageName
                    Surface(
                        onClick = { onSelect(pack.packageName) },
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppIcon(drawable = pack.icon, contentDescription = pack.name, modifier = Modifier.size(24.dp))
                            Text(
                                text = pack.name,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
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
    FozTheme(darkTheme = isDark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(
                state = state,
                onClose = {},
                onClockUse24hChanged = {},
                onIconSizeChanged = {},
                onSwipeUpEnabledChanged = {},
                onSwipeDownEnabledChanged = {},
                onThemeModeChanged = {},
                onShowNotificationsChanged = {},
                onUsageLimitsChanged = {},
                onHapticsChanged = {},
                onIconPackChanged = {},
                onOpenLauncherSettings = {},
                onOpenWidgetPicker = {}
            )
        }
    }
}
