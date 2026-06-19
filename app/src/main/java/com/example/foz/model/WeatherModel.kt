package com.example.foz.model

import java.time.LocalDateTime

data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val maxTemp: Double,
    val minTemp: Double,
    val condition: String
)

data class WeatherModel(
    val temperature: Double,
    val condition: String,
    val location: String,
    val humidity: Int,
    val windSpeed: Double,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val dailyForecasts: List<DailyForecast> = emptyList()
)
