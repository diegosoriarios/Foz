package com.example.foz.data

import com.example.foz.model.DailyForecast
import com.example.foz.model.HourlyForecast
import com.example.foz.model.WeatherModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class WeatherRepository {
    // Open-Meteo is used because it doesn't require an API key
    suspend fun fetchWeather(lat: Double, lon: Double, cityName: String? = null): WeatherModel? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&daily=weathercode,temperature_2m_max,temperature_2m_min&hourly=temperature_2m,weathercode&timezone=auto"
                val connection = URL(url).openConnection()
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                parseOpenMeteoResponse(response, lat, lon, cityName)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseOpenMeteoResponse(json: String, lat: Double, lon: Double, cityName: String?): WeatherModel {
        val root = JSONObject(json)
        val current = root.getJSONObject("current_weather")
        val temp = current.getDouble("temperature")
        val weatherCode = current.getInt("weathercode")
        val windSpeed = current.getDouble("windspeed")

        val dailyForecasts = mutableListOf<DailyForecast>()
        if (root.has("daily")) {
            val daily = root.getJSONObject("daily")
            val times = daily.getJSONArray("time")
            val codes = daily.getJSONArray("weathercode")
            val maxTemps = daily.getJSONArray("temperature_2m_max")
            val minTemps = daily.getJSONArray("temperature_2m_min")

            for (i in 0 until times.length()) {
                val code = codes.getInt(i)
                dailyForecasts.add(
                    DailyForecast(
                        date = times.getString(i),
                        weatherCode = code,
                        maxTemp = maxTemps.getDouble(i),
                        minTemp = minTemps.getDouble(i),
                        condition = mapWeatherCode(code)
                    )
                )
            }
        }

        val hourlyForecasts = mutableListOf<HourlyForecast>()
        if (root.has("hourly")) {
            val hourly = root.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.getJSONArray("weathercode")
            
            val now = LocalDateTime.now()
            val currentHourString = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00"))

            var startIndex = 0
            for (i in 0 until times.length()) {
                if (times.getString(i) >= currentHourString) {
                    startIndex = i
                    break
                }
            }

            for (i in startIndex until (startIndex + 24).coerceAtMost(times.length())) {
                val code = codes.getInt(i)
                hourlyForecasts.add(
                    HourlyForecast(
                        time = times.getString(i),
                        temp = temps.getDouble(i),
                        weatherCode = code,
                        condition = mapWeatherCode(code)
                    )
                )
            }
        }
        
        return WeatherModel(
            temperature = temp,
            condition = mapWeatherCode(weatherCode),
            location = cityName ?: "(${"%.2f".format(lat)}, ${"%.2f".format(lon)})",
            humidity = 0,
            windSpeed = windSpeed,
            dailyForecasts = dailyForecasts,
            hourlyForecasts = hourlyForecasts
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

    fun toJson(weather: WeatherModel): String {
        val json = JSONObject()
        json.put("temperature", weather.temperature)
        json.put("condition", weather.condition)
        json.put("location", weather.location)
        json.put("humidity", weather.humidity)
        json.put("windSpeed", weather.windSpeed)
        json.put("timestamp", weather.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        return json.toString()
    }

    fun fromJson(json: String): WeatherModel? {
        return try {
            val root = JSONObject(json)
            WeatherModel(
                temperature = root.getDouble("temperature"),
                condition = root.getString("condition"),
                location = root.getString("location"),
                humidity = root.getInt("humidity"),
                windSpeed = root.getDouble("windSpeed"),
                timestamp = LocalDateTime.parse(root.getString("timestamp"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        } catch (e: Exception) {
            null
        }
    }
}
