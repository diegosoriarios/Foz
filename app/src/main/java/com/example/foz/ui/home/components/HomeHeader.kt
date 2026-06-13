package com.example.foz.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.foz.model.AppInfo
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.applist.AppIcon
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeHeader(
    state: LauncherUiState,
    appListState: LazyListState,
    timeFormatter: DateTimeFormatter,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier,
    onLaunchApp: (AppInfo) -> Unit,
    onCloseDrawer: () -> Unit,
) {
    Box(modifier = modifier.height(228.dp)) {
        if (!state.drawerOpen) {
            DefaultHeader(state, timeFormatter, dateFormatter)
        } else {
            DrawerHeader(state, appListState, onLaunchApp = { onLaunchApp }, onCloseDrawer = { onCloseDrawer })
        }
    }
}

@Composable
private fun DefaultHeader(
    state: LauncherUiState,
    timeFormatter: DateTimeFormatter,
    dateFormatter: DateTimeFormatter
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        TextWithOutline(
            text = state.now.format(timeFormatter),
            style = MaterialTheme.typography.displayLarge,
            mainColor = MaterialTheme.colorScheme.onBackground
        )
        TextWithOutline(
            text = state.now.format(dateFormatter),
            style = MaterialTheme.typography.titleMedium,
            mainColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        state.weather?.let { weather ->
            TextWithOutline(
                text = "${weather.temperature}°C • ${weather.condition}",
                style = MaterialTheme.typography.bodyMedium,
                mainColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            TextWithOutline(
                text = "${weather.location} • ${weather.humidity}% humidity",
                style = MaterialTheme.typography.bodySmall,
                mainColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun TextWithOutline(
    text: String,
    style: TextStyle,
    mainColor: Color,
    outlineColor: Color = Color.Black.copy(alpha = 0.5f)
) {
    Box {
        // Simple outline effect using shadows for better performance than multiple Text layers
        Text(
            text = text,
            style = style.copy(
                shadow = Shadow(
                    color = outlineColor,
                    offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                    blurRadius = 4f
                )
            ),
            color = mainColor
        )
    }
}

@Composable
private fun DrawerHeader(
    state: LauncherUiState,
    appListState: LazyListState,
    onLaunchApp: (AppInfo) -> Unit,
    onCloseDrawer: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        val currentLetter by remember(state.filteredApps) {
            derivedStateOf {
                state.filteredApps.getOrNull(appListState.firstVisibleItemIndex)
                    ?.name?.firstOrNull()?.uppercaseChar()
            }
        }

        val appsAbove = remember(currentLetter, state.sectionIndexes, state.filteredApps) {
            val index = currentLetter?.let { state.sectionIndexes[it] } ?: 0
            if (index > 0) {
                state.filteredApps.subList((index - 3).coerceAtLeast(0), index)
            } else {
                emptyList()
            }
        }

        if (appsAbove.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Before $currentLetter",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                appsAbove.forEach { app ->
                    AppListItem(
                        app = app,
                        iconSize = state.appIconSizeDp,
                        onClick = {
                            onLaunchApp(app)
                            onCloseDrawer()
                        },
                        onLongClick = { }
                    )
                }
            }
        }
    }
}
