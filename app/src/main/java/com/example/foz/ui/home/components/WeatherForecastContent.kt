package com.example.foz.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.foz.R
import com.example.foz.model.WeatherModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun WeatherForecastContent(
    weather: WeatherModel,
    modifier: Modifier = Modifier
) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(weather.hourlyForecasts) { hourly ->
                    HourlyForecastItem(hourly)
                }
            }
        }

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
private fun HourlyForecastItem(hourly: com.example.foz.model.HourlyForecast) {
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    forecast: com.example.foz.model.DailyForecast
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
