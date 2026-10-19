package com.example.foz.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.model.IconPackInfo
import com.example.foz.model.WidgetSummary
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.applist.AppIcon
import com.example.foz.ui.home.components.WidgetActionDialog
import com.example.foz.ui.theme.FozTheme

sealed class SettingsItem {
    data class Header(val title: String) : SettingsItem()
    data class Toggle(val title: String, val value: Boolean, val onChange: (Boolean) -> Unit) : SettingsItem()
    data class Action(val title: String, val description: String? = null, val onClick: () -> Unit) : SettingsItem()
    data class Navigation(val title: String, val description: String? = null, val onClick: () -> Unit) : SettingsItem()
    data class Choice(val title: String, val options: List<String>, val selected: String, val onSelect: (String) -> Unit) : SettingsItem()
    data class Slider(val title: String, val value: Float, val range: ClosedFloatingPointRange<Float>, val steps: Int, val valueLabel: String, val onValueChange: (Float) -> Unit) : SettingsItem()
}

private enum class SettingsPage { Main, Widgets, Calendar, UsageLimit }

@Composable
fun SettingsScreen(
    state: LauncherUiState,
    onClose: () -> Unit,
    onClockUse24hChanged: (Boolean) -> Unit,
    onIconSizeChanged: (Int) -> Unit,
    onSwipeUpEnabledChanged: (Boolean) -> Unit,
    onSwipeDownEnabledChanged: (Boolean) -> Unit,
    onThemeModeChanged: (String) -> Unit,
    onUseDynamicColorChanged: (Boolean) -> Unit,
    onMonochromeModeChanged: (Boolean) -> Unit,
    onSuppressMonochromeDialogChanged: (Boolean) -> Unit,
    onUsageLimitsChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onAdBlockEnabledChanged: (Boolean) -> Unit,
    onQuickRepliesChanged: (Boolean) -> Unit,
    onNotificationSummaryChanged: (Boolean) -> Unit,
    onContactsSearchChanged: (Boolean) -> Unit,
    onWebAnswersChanged: (Boolean) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onUsageLimitHoursChanged: (Int) -> Unit,
    onSuggestAppsChanged: (Boolean) -> Unit,
    onSortByUsageChanged: (Boolean) -> Unit,
    onWeatherEnabledChanged: (Boolean) -> Unit,
    onWeatherUnitsChanged: (Boolean) -> Unit,
    onMediaCardEnabledChanged: (Boolean) -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onCalendarPreviewChanged: (Boolean) -> Unit,
    onCalendarPreviewCountChanged: (Int) -> Unit,
    onCalendarAgendaDaysChanged: (Int) -> Unit,
    onIconPackChanged: (String?) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenSystemAccessibilitySettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    widgetSummaries: List<WidgetSummary>,
    onWidgetPanelHeightChanged: (Int) -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onResizeWidget: (Int, Int) -> Unit,
    onConfigureWidget: (Int) -> Unit
) {
    var showIconPackModal by remember { mutableStateOf(false) }
    var showThemeModal by remember { mutableStateOf(false) }
    var showMonochromeDialog by remember { mutableStateOf(false) }
    var pendingMonochromeValue by remember { mutableStateOf(false) }
    var actionWidgetId by remember { mutableStateOf<Int?>(null) }
    var page by remember { mutableStateOf(SettingsPage.Main) }

    val handleMonochromeChange = { newValue: Boolean ->
        if (state.suppressMonochromeDialog) {
            onMonochromeModeChanged(newValue)
        } else {
            pendingMonochromeValue = newValue
            showMonochromeDialog = true
        }
    }

    val listState = rememberLazyListState()
    BackHandler(enabled = page != SettingsPage.Main) { page = SettingsPage.Main }
    LaunchedEffect(page) { listState.scrollToItem(0) }

    val usageLimitDescription = if (state.usageLimitsEnabled) {
        stringResource(R.string.settings_usage_hours_format, state.usageLimitHours)
    } else {
        stringResource(R.string.settings_disabled_hint)
    }
    val calendarDescription = if (state.calendarPreviewEnabled) {
        stringResource(R.string.settings_enabled)
    } else {
        stringResource(R.string.settings_disabled_hint)
    }
    val widgetsDescription = if (widgetSummaries.isEmpty()) {
        stringResource(R.string.settings_no_widgets)
    } else {
        stringResource(R.string.settings_manage_widget)
    }

    val mainItems = listOfNotNull(
        SettingsItem.Header(stringResource(R.string.settings_header_appearance)),
        SettingsItem.Slider(
            title = stringResource(R.string.settings_icon_size),
            value = state.appIconSizeDp.toFloat(),
            range = 28f..48f,
            steps = 4,
            valueLabel = stringResource(R.string.settings_icon_size_format, state.appIconSizeDp),
            onValueChange = { onIconSizeChanged(it.toInt()) }
        ),
        SettingsItem.Action(
            title = stringResource(R.string.settings_icon_pack),
            description = state.availableIconPacks.find { it.packageName == state.iconPackPackageName }?.name ?: stringResource(R.string.settings_icon_pack_default),
            onClick = { showIconPackModal = true }
        ),
        SettingsItem.Action(
            title = stringResource(R.string.settings_theme),
            description = state.themeMode.replaceFirstChar { it.uppercase() },
            onClick = { showThemeModal = true }
        ),
        SettingsItem.Toggle(stringResource(R.string.settings_dynamic_color), state.useDynamicColor, onUseDynamicColorChanged),
        SettingsItem.Toggle(stringResource(R.string.settings_monochrome_mode), state.monochromeMode, handleMonochromeChange),

        SettingsItem.Header(stringResource(R.string.settings_header_gestures)),
        SettingsItem.Toggle(stringResource(R.string.settings_swipe_up_search), state.swipeUpEnabled, onSwipeUpEnabledChanged),
        SettingsItem.Toggle(stringResource(R.string.settings_swipe_down_notifications), state.swipeDownEnabled, onSwipeDownEnabledChanged),

        SettingsItem.Header(stringResource(R.string.settings_header_notifications)),
        SettingsItem.Action(
            title = stringResource(R.string.settings_notification_access),
            description = if (state.isNotificationListenerEnabled) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled_hint),
            onClick = onOpenNotificationListenerSettings
        ),
        SettingsItem.Toggle(stringResource(R.string.settings_quick_replies), state.quickRepliesEnabled, onQuickRepliesChanged),
        SettingsItem.Toggle(stringResource(R.string.settings_notification_summary), state.notificationSummaryEnabled, onNotificationSummaryChanged),

        SettingsItem.Header(stringResource(R.string.settings_header_search)),
        SettingsItem.Toggle(stringResource(R.string.settings_search_contacts), state.searchContactsEnabled, onContactsSearchChanged),
        SettingsItem.Toggle(stringResource(R.string.settings_web_answers), state.webAnswersEnabled, onWebAnswersChanged),

        SettingsItem.Header(stringResource(R.string.settings_header_home_screen)),
        SettingsItem.Toggle(stringResource(R.string.settings_weather), state.weatherEnabled, onWeatherEnabledChanged),
        SettingsItem.Choice(
            title = stringResource(R.string.settings_weather_units),
            options = listOf("°C", "°F"),
            selected = if (state.weatherImperial) "°F" else "°C",
            onSelect = { option -> onWeatherUnitsChanged(option == "°F") }
        ),
        SettingsItem.Toggle(stringResource(R.string.settings_media_card), state.mediaCardEnabled, onMediaCardEnabledChanged),

        SettingsItem.Header(stringResource(R.string.settings_header_app_usage)),
        SettingsItem.Navigation(
            title = stringResource(R.string.settings_usage_limits),
            description = usageLimitDescription,
            onClick = { page = SettingsPage.UsageLimit }
        ),
        SettingsItem.Toggle(stringResource(R.string.settings_suggest_apps), state.suggestAppsEnabled, onSuggestAppsChanged),
        SettingsItem.Toggle(stringResource(R.string.settings_sort_by_usage), state.sortByUsageEnabled, onSortByUsageChanged),

        SettingsItem.Navigation(
            title = stringResource(R.string.settings_header_calendar),
            description = calendarDescription,
            onClick = { page = SettingsPage.Calendar }
        ),
        SettingsItem.Navigation(
            title = stringResource(R.string.settings_header_widgets),
            description = widgetsDescription,
            onClick = { page = SettingsPage.Widgets }
        ),

        SettingsItem.Header(stringResource(R.string.settings_header_system)),
        SettingsItem.Toggle(stringResource(R.string.settings_clock_24h), state.clockUse24h, onClockUse24hChanged),
        SettingsItem.Action(stringResource(R.string.settings_change_launcher), onClick = onOpenLauncherSettings),
        SettingsItem.Toggle(stringResource(R.string.settings_haptics), state.hapticsEnabled, onHapticsChanged),
        SettingsItem.Toggle(stringResource(R.string.settings_ad_block), state.adBlockEnabled, onAdBlockEnabledChanged)
    )

    val items = when (page) {
        SettingsPage.Main -> mainItems
        SettingsPage.UsageLimit -> listOf(
            SettingsItem.Action(
                title = stringResource(R.string.settings_usage_access),
                description = if (state.isUsageStatsPermissionGranted) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled_hint),
                onClick = onOpenUsageAccessSettings
            ),
            SettingsItem.Toggle(stringResource(R.string.settings_usage_limits), state.usageLimitsEnabled, onUsageLimitsChanged),
            SettingsItem.Slider(
                title = stringResource(R.string.settings_usage_limit_daily),
                value = state.usageLimitHours.toFloat(),
                range = 1f..12f,
                steps = 10,
                valueLabel = stringResource(R.string.settings_usage_hours_format, state.usageLimitHours),
                onValueChange = { onUsageLimitHoursChanged(it.toInt()) }
            )
        )
        SettingsPage.Calendar -> listOf(
            SettingsItem.Action(
                title = stringResource(R.string.settings_calendar_access),
                description = if (state.isCalendarPermissionGranted) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled_hint),
                onClick = onRequestCalendarPermission
            ),
            SettingsItem.Toggle(stringResource(R.string.settings_calendar_preview), state.calendarPreviewEnabled, onCalendarPreviewChanged),
            SettingsItem.Choice(
                title = stringResource(R.string.settings_calendar_preview_count),
                options = listOf("1", "2", "3", "4", "5"),
                selected = state.calendarPreviewCount.toString(),
                onSelect = { option -> onCalendarPreviewCountChanged(option.toIntOrNull() ?: 3) }
            ),
            SettingsItem.Choice(
                title = stringResource(R.string.settings_calendar_agenda_days),
                options = listOf("3", "7", "14"),
                selected = state.calendarAgendaDays.toString(),
                onSelect = { option -> onCalendarAgendaDaysChanged(option.toIntOrNull() ?: 7) }
            )
        )
        SettingsPage.Widgets -> buildList {
            add(
                SettingsItem.Slider(
                    title = stringResource(R.string.settings_widget_panel_height),
                    value = state.widgetPanelHeightDp.toFloat(),
                    range = 100f..300f,
                    steps = 5,
                    valueLabel = stringResource(R.string.settings_icon_size_format, state.widgetPanelHeightDp),
                    onValueChange = { onWidgetPanelHeightChanged(it.toInt()) }
                )
            )
            add(
                SettingsItem.Action(
                    title = stringResource(R.string.settings_add_widget),
                    description = if (widgetSummaries.isEmpty()) stringResource(R.string.settings_no_widgets) else null,
                    onClick = onAddWidget
                )
            )
            widgetSummaries.forEach { summary ->
                add(
                    SettingsItem.Action(
                        title = summary.label,
                        description = stringResource(R.string.settings_manage_widget),
                        onClick = { actionWidgetId = summary.widgetId }
                    )
                )
            }
        }
    }

    val pageTitle = when (page) {
        SettingsPage.Main -> stringResource(R.string.settings_title)
        SettingsPage.Widgets -> stringResource(R.string.settings_header_widgets)
        SettingsPage.Calendar -> stringResource(R.string.settings_header_calendar)
        SettingsPage.UsageLimit -> stringResource(R.string.settings_usage_limits)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page != SettingsPage.Main) {
                IconButton(onClick = { page = SettingsPage.Main }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back)
                    )
                }
            }
            Text(
                text = pageTitle,
                style = if (page == SettingsPage.Main) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Surface(onClick = onClose, shape = MaterialTheme.shapes.medium) {
                Text(text = stringResource(R.string.settings_close), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is SettingsItem.Header -> SettingsHeaderRow(item.title)
                    is SettingsItem.Toggle -> SettingsToggleRow(item.title, item.value, item.onChange)
                    is SettingsItem.Action -> SettingsActionRow(item.title, item.description, item.onClick)
                    is SettingsItem.Navigation -> SettingsNavigationRow(item.title, item.description, item.onClick)
                    is SettingsItem.Choice -> SettingsChoiceRow(item.title, item.options, item.selected, item.onSelect)
                    is SettingsItem.Slider -> SettingsSliderRow(item.title, item.value, item.range, item.steps, item.valueLabel, item.onValueChange)
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

    actionWidgetId?.let { widgetId ->
        val summary = widgetSummaries.find { it.widgetId == widgetId }
        WidgetActionDialog(
            onRemove = {
                onRemoveWidget(widgetId)
                actionWidgetId = null
            },
            onMoveUp = {
                onMoveWidget(widgetId, -1)
                actionWidgetId = null
            },
            onMoveDown = {
                onMoveWidget(widgetId, 1)
                actionWidgetId = null
            },
            onResize = { newHeight ->
                onResizeWidget(widgetId, newHeight)
                actionWidgetId = null
            },
            onConfigure = summary?.canConfigure?.let {
                {
                    onConfigureWidget(widgetId)
                    actionWidgetId = null
                }
            },
            onDismiss = { actionWidgetId = null }
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MonochromeInfoDialog(
    onConfirm: (Boolean) -> Unit,
    onGoToSettings: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    com.example.foz.ui.components.FozBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.monochrome_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = stringResource(R.string.monochrome_dialog_desc),
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
                    text = stringResource(R.string.monochrome_dialog_dont_show),
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
                    Text(stringResource(R.string.dialog_cancel))
                }
                Spacer(modifier = Modifier.size(8.dp))
                androidx.compose.material3.TextButton(onClick = { onConfirm(dontShowAgain) }) {
                    Text(stringResource(R.string.monochrome_dialog_ignore))
                }
                Spacer(modifier = Modifier.size(8.dp))
                androidx.compose.material3.Button(onClick = { onGoToSettings(dontShowAgain) }) {
                    Text(stringResource(R.string.monochrome_dialog_settings))
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelectionModal(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    com.example.foz.ui.components.FozBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.theme_select_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val options = listOf("system", "light", "dark")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    val isSelected = selected == option
                    val label = when(option) {
                        "system" -> stringResource(R.string.theme_system)
                        "light" -> stringResource(R.string.theme_light)
                        "dark" -> stringResource(R.string.theme_dark)
                        else -> option
                    }
                    Surface(
                        onClick = { onSelect(option) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = label,
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
                    text = stringResource(R.string.dialog_cancel),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun IconPackSelectionModal(
    packs: List<IconPackInfo>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    com.example.foz.ui.components.FozBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_icon_pack),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    IconPackOptionRow(
                        name = stringResource(R.string.settings_icon_pack_default),
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
                    text = stringResource(R.string.dialog_cancel),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
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
    valueLabel: String,
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
                    text = valueLabel,
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
private fun SettingsNavigationRow(title: String, description: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.labelMedium)
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                onSwipeUpEnabledChanged = {},
                onSwipeDownEnabledChanged = {},
                onThemeModeChanged = {},
                onUseDynamicColorChanged = {},
                onMonochromeModeChanged = {},
                onSuppressMonochromeDialogChanged = {},
                onUsageLimitsChanged = {},
                onHapticsChanged = {},
                onAdBlockEnabledChanged = {},
                onQuickRepliesChanged = {},
                onNotificationSummaryChanged = {},
                onContactsSearchChanged = {},
                onWebAnswersChanged = {},
                onOpenUsageAccessSettings = {},
                onUsageLimitHoursChanged = {},
                onSuggestAppsChanged = {},
                onSortByUsageChanged = {},
                onWeatherEnabledChanged = {},
                onWeatherUnitsChanged = {},
                onMediaCardEnabledChanged = {},
                onRequestCalendarPermission = {},
                onCalendarPreviewChanged = {},
                onCalendarPreviewCountChanged = {},
                onCalendarAgendaDaysChanged = {},
                onIconPackChanged = {},
                onOpenLauncherSettings = {},
                onOpenSystemAccessibilitySettings = {},
                onOpenNotificationListenerSettings = {},
                widgetSummaries = emptyList(),
                onWidgetPanelHeightChanged = {},
                onAddWidget = {},
                onRemoveWidget = {},
                onMoveWidget = { _, _ -> },
                onResizeWidget = { _, _ -> },
                onConfigureWidget = {}
            )
        }
    }
}
