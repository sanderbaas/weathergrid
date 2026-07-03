package nl.baasmail.weathergrid.api

import nl.baasmail.weathergrid.data.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double = 51.80,
        @Query("longitude") lon: Double = 4.65,
        @Query("hourly") hourly: String = "temperature_2m",
        @Query("wind_speed_unit") windSpeedUnit: String = "ms",
        @Query("timezone") timezone: String = "Europe/Amsterdam"
    ): WeatherResponse
}
