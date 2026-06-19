package com.example.foz.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.provider.Settings
import com.example.foz.R
import com.example.foz.model.DailyForecast
import com.example.foz.model.HourlyForecast
import com.example.foz.model.WeatherModel
import com.example.foz.ui.theme.FozTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun WeatherForecastContent(
    weather: WeatherModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hourlyListState = rememberLazyListState()

    // Check if system haptic feedback is enabled
    val isHapticEnabled = remember(context) {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1
        ) == 1
    }

    // Trigger haptic feedback when the user scrolls through items
    LaunchedEffect(hourlyListState) {
        snapshotFlow { hourlyListState.firstVisibleItemIndex }
            .collect {
                if (isHapticEnabled && hourlyListState.isScrollInProgress) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 32.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Weather Forecast",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            
            Text(
                text = weather.location,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (weather.hourlyForecasts.isNotEmpty()) {
            LazyRow(
                state = hourlyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 2.dp,
                        color = Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(weather.hourlyForecasts) { hourly ->
                    HourlyForecastItem(hourly)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            items(weather.dailyForecasts) { forecast ->
                ForecastRow(forecast = forecast)
                HorizontalDivider(
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun HourlyForecastItem(hourly: HourlyForecast) {
    val time = LocalDateTime.parse(hourly.time)
    val hourString = if (time.hour == LocalDateTime.now().hour && time.toLocalDate() == LocalDate.now()) {
        "Now"
    } else {
        time.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = hourString,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Image(
            painter = painterResource(id = getWeatherIconByCode(hourly.weatherCode)),
            contentDescription = hourly.condition,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = "${hourly.temp.toInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ForecastRow(
    forecast: DailyForecast
) {
    val date = LocalDate.parse(forecast.date)
    val dayName = if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("EEEE"))
    val dateStr = date.format(DateTimeFormatter.ofPattern("MMM d"))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1.5f),
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = getWeatherIconByCode(forecast.weatherCode)),
                contentDescription = forecast.condition,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = forecast.condition,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "${forecast.maxTemp.toInt()}°",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${forecast.minTemp.toInt()}°",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getWeatherIconByCode(code: Int): Int {
    return when (code) {
        0 -> R.drawable.clear_sky
        1, 2, 3 -> R.drawable.mostly_clear
        45, 48 -> R.drawable.fog
        51, 53, 55 -> R.drawable.drizzle
        61, 63, 65 -> R.drawable.rain
        71, 73, 75 -> R.drawable.snow
        77 -> R.drawable.snow
        80, 81, 82 -> R.drawable.rain
        85, 86 -> R.drawable.snow
        95 -> R.drawable.thunderstorm
        96, 99 -> R.drawable.thunderstorm_rail
        else -> R.drawable.unknown
    }
}

@Preview(showBackground = true)
@Composable
fun WeatherForecastPreview() {
    val now = LocalDateTime.now()
    val hourly = List(24) { i ->
        val time = now.plusHours(i.toLong())
        HourlyForecast(
            time = time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            temp = 20.0 + i % 5,
            weatherCode = if (i % 3 == 0) 0 else 1,
            condition = "Clear"
        )
    }
    
    val daily = List(7) { i ->
        val date = LocalDate.now().plusDays(i.toLong())
        DailyForecast(
            date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            weatherCode = i % 2,
            maxTemp = 25.0 + i,
            minTemp = 15.0 + i,
            condition = "Sunny"
        )
    }

    val mockWeather = WeatherModel(
        temperature = 22.0,
        condition = "Clear",
        location = "San Francisco",
        humidity = 60,
        windSpeed = 5.0,
        dailyForecasts = daily,
        hourlyForecasts = hourly
    )

    FozTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            WeatherForecastContent(weather = mockWeather)
        }
    }
}
