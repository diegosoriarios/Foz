package com.example.foz.data

import com.example.foz.model.WeatherModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.*

class WeatherRepository {
    private val apiKey = "YOUR_API_KEY_HERE" // TODO: Replace with your actual API key
    private val units = "metric" // Use "metric" for Celsius, "imperial" for Fahrenheit

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherModel? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$apiKey&units=$units"
                val connection = URL(url).openConnection()
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Parse the JSON response (simplified parsing - in production use JSON parsing library)
                parseWeatherResponse(response)
            } catch (e: Exception) {
                // In production, handle errors properly and return null or error state
                null
            }
        }
    }

    private fun parseWeatherResponse(json: String): WeatherModel {
        // Simplified JSON parsing - in production use proper JSON library like Gson/Moshi
        // This is a basic implementation that extracts key fields from OpenWeatherMap API response
        val temp = extractValue(json, "temp").toDoubleOrNull() ?: 0.0
        val condition = extractValue(json, "main").replace("\"", "")
        val location = extractValue(json, "name").replace("\"", "")
        val humidity = extractValue(json, "humidity").toIntOrNull() ?: 0
        val windSpeed = extractValue(json, "speed").toDoubleOrNull() ?: 0.0
        
        return WeatherModel(
            temperature = temp,
            condition = condition,
            location = location,
            humidity = humidity,
            windSpeed = windSpeed
        )
    }

    private fun extractValue(json: String, key: String): String {
        val regex = """"$key"\s*:\s*([^,}]+)""".toRegex()
        val match = regex.find(json) ?: return ""
        return match.groupValues[1].replace("\"", "")
    }

    // Mock data for testing when API is not available
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