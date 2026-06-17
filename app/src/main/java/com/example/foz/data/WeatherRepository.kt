package com.example.foz.data

import com.example.foz.model.WeatherModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class WeatherRepository {
    // Open-Meteo is used because it doesn't require an API key
    suspend fun fetchWeather(lat: Double, lon: Double): WeatherModel? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
                val connection = URL(url).openConnection()
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                parseOpenMeteoResponse(response, lat, lon)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseOpenMeteoResponse(json: String, lat: Double, lon: Double): WeatherModel {
        val root = JSONObject(json)
        val current = root.getJSONObject("current_weather")
        val temp = current.getDouble("temperature")
        val weatherCode = current.getInt("weathercode")
        val windSpeed = current.getDouble("windspeed")
        
        return WeatherModel(
            temperature = temp,
            condition = mapWeatherCode(weatherCode),
            location = "(${"%.2f".format(lat)}, ${"%.2f".format(lon)})",
            humidity = 0, // Open-Meteo current_weather doesn't include humidity by default
            windSpeed = windSpeed
        )
    }

    private fun mapWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Clear sky"
            1, 2, 3 -> "Mainly clear"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            77 -> "Snow grains"
            80, 81, 82 -> "Rain showers"
            85, 86 -> "Snow showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with hail"
            else -> "Unknown"
        }
    }

    fun getMockWeather(): WeatherModel {
        return WeatherModel(
            temperature = 22.5,
            condition = "Clear",
            location = "New York",
            humidity = 65,
            windSpeed = 3.5
        )
    }
}
