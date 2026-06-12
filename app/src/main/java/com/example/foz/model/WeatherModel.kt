package com.example.foz.model

import java.time.LocalDateTime

data class WeatherModel(
    val temperature: Double,
    val condition: String,
    val location: String,
    val humidity: Int,
    val windSpeed: Double,
    val timestamp: LocalDateTime = LocalDateTime.now()
)