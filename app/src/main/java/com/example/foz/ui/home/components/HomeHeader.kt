package com.example.foz.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.foz.R
import androidx.compose.ui.res.stringResource
import com.example.foz.model.AppInfo
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.applist.AppIcon
import java.time.format.DateTimeFormatter

@Composable
fun HomeHeader(
    state: LauncherUiState,
    appListState: LazyListState,
    timeFormatter: DateTimeFormatter,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier,
    onLaunchApp: (AppInfo) -> Unit,
    onCloseDrawer: () -> Unit,
    onShowAppNotifications: (String) -> Unit = {},
    onShowWeatherForecast: () -> Unit = {},
    onShowAgenda: () -> Unit = {},
    drawerItems: List<DrawerItem> = emptyList()
) {
    Box(modifier = modifier.height(212.dp)) {
        if (!state.drawerOpen) {
            DefaultHeader(state, timeFormatter, dateFormatter, onShowWeatherForecast, onShowAgenda)
        } else {
            DrawerHeader(
                state = state,
                appListState = appListState,
                drawerItems = drawerItems,
                onLaunchApp = onLaunchApp,
                onCloseDrawer = onCloseDrawer,
                onShowAppNotifications = onShowAppNotifications
            )
        }
    }
}

@Composable
private fun DefaultHeader(
    state: LauncherUiState,
    timeFormatter: DateTimeFormatter,
    dateFormatter: DateTimeFormatter,
    onShowWeatherForecast: () -> Unit,
    onShowAgenda: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(vertical = 20.dp), verticalArrangement = Arrangement.Bottom) {
        TextWithOutline(
            text = state.now.format(timeFormatter),
            style = MaterialTheme.typography.displayLarge,
            mainColor = MaterialTheme.colorScheme.onBackground
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextWithOutline(
                text = state.now.format(dateFormatter),
                style = MaterialTheme.typography.titleMedium,
                mainColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                modifier = Modifier.clickable { onShowAgenda() }
            )
            if (state.weatherEnabled) {
                state.weather?.let { weather ->
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onShowWeatherForecast() }
                    ) {
                        Image(
                            painter = painterResource(id = getWeatherIcon(weather.condition)),
                            contentDescription = weather.condition,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val unit = if (state.weatherImperial) "°F" else "°C"
                        TextWithOutline(
                            text = "${weather.temperature.toInt()}$unit",
                            style = MaterialTheme.typography.titleMedium,
                            mainColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
        if (state.weatherEnabled) {
            state.weather?.let { weather ->
                TextWithOutline(
                    text = weather.location,
                    style = MaterialTheme.typography.titleMedium,
                    mainColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    modifier = Modifier.clickable { onShowWeatherForecast() }
                )
            }
        }
    }
}

private fun getWeatherIcon(condition: String): Int {
    return when (condition) {
        "Clear sky" -> R.drawable.clear_sky
        "Mainly clear" -> R.drawable.mostly_clear
        "Fog" -> R.drawable.fog
        "Drizzle" -> R.drawable.drizzle
        "Rain", "Rain showers" -> R.drawable.rain
        "Snow", "Snow grains", "Snow showers" -> R.drawable.snow
        "Thunderstorm" -> R.drawable.thunderstorm
        "Thunderstorm with hail" -> R.drawable.thunderstorm_rail
        else -> R.drawable.unknown
    }
}

@Composable
private fun DrawerHeader(
    state: LauncherUiState,
    appListState: LazyListState,
    drawerItems: List<DrawerItem>,
    onLaunchApp: (AppInfo) -> Unit,
    onCloseDrawer: () -> Unit,
    onShowAppNotifications: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        val currentLetter by remember(state.filteredApps, drawerItems) {
            derivedStateOf {
                val flatIndex = flatAppIndex(drawerItems, appListState.firstVisibleItemIndex)
                state.filteredApps.getOrNull(flatIndex)?.let { sectionLetterFor(it) }
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
                    text = stringResource(R.string.drawer_section_header, currentLetter ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                appsAbove.forEach { app ->
                    val notifications = state.notificationsByPackage[app.packageName] ?: emptyList()
                    AppListItem(
                        app = app,
                        iconSize = state.appIconSizeDp,
                        onClick = {
                            onLaunchApp(app)
                            onCloseDrawer()
                        },
                        onLongClick = { },
                        notificationCount = notifications.size,
                        onNotificationClick = { onShowAppNotifications(app.packageName) }
                    )
                }
            }
        }
    }
}
