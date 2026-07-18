package nl.baasmail.weathergrid.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    val hourly: HourlyData,
    val current: CurrentData? = null,
    val daily: DailyData? = null
)

@Serializable
data class DailyData(
    val time: List<String>,
    val sunrise: List<String>,
    val sunset: List<String>,
    @SerialName("precipitation_probability_max")
    val precipitationProbabilityMax: List<Int>
)

@Serializable
data class CurrentData(
    val time: String,
    @SerialName("temperature_2m")
    val temperature: Double,
    @SerialName("apparent_temperature")
    val apparentTemperature: Double,
    @SerialName("relative_humidity_2m")
    val humidity: Int,
    @SerialName("uv_index")
    val uvIndex: Double,
    @SerialName("weather_code")
    val weatherCode: Int,
    @SerialName("is_day")
    val isDay: Int,
    @SerialName("wind_speed_10m")
    val windSpeed: Double,
    @SerialName("wind_direction_10m")
    val windDirection: Int
)

@Serializable
data class HourlyData(
    val time: List<String>,
    @SerialName("temperature_2m")
    val temperatures: List<Double>,
    @SerialName("weather_code")
    val weatherCodes: List<Int>,
    @SerialName("is_day")
    val isDay: List<Int>,
    @SerialName("wind_speed_10m")
    val windSpeeds: List<Double>,
    @SerialName("wind_direction_10m")
    val windDirections: List<Int>,
    val precipitation: List<Double>,
    val rain: List<Double>,
    val snowfall: List<Double>,
    @SerialName("precipitation_probability")
    val precipitationProbability: List<Int>,
    @SerialName("relative_humidity_2m")
    val humidities: List<Int>,
    @SerialName("uv_index")
    val uvIndex: List<Double>,
)
