package nl.baasmail.weathergrid

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.Html
import android.view.View
import android.widget.RemoteViews
import nl.baasmail.weathergrid.data.WeatherResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object WeatherManager {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun refreshWidget(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("lat_$appWidgetId", 51.80f).toDouble()
        val lon = prefs.getFloat("lon_$appWidgetId", 4.65f).toDouble()
        val place = prefs.getString("place_$appWidgetId", context.getString(R.string.weather_forecast_default)) ?: context.getString(R.string.weather_forecast_default)
        
        val tempUnit = prefs.getString("temp_unit_$appWidgetId", "celsius") ?: "celsius"
        val windUnit = prefs.getString("wind_unit_$appWidgetId", "beaufort") ?: "beaufort"
        val precipUnit = prefs.getString("precip_unit_$appWidgetId", "mm") ?: "mm"

        val weatherData = fetchWeatherData(lat, lon, tempUnit, windUnit, precipUnit)
        if (weatherData != null) {
            val lastUpdateStr = LocalDateTime.now().toString()
            val weatherJson = json.encodeToString(weatherData)
            
            prefs.edit().apply {
                putString("cache_data_$appWidgetId", weatherJson)
                putString("cache_time_$appWidgetId", lastUpdateStr)
                apply()
            }
            updateWidgetUi(context, appWidgetId, weatherData, lat, lon, place, lastUpdateStr)
        }
    }

    fun redrawWidget(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("lat_$appWidgetId", 51.80f).toDouble()
        val lon = prefs.getFloat("lon_$appWidgetId", 4.65f).toDouble()
        val place = prefs.getString("place_$appWidgetId", context.getString(R.string.weather_forecast_default)) ?: context.getString(R.string.weather_forecast_default)
        val lastUpdateStr = prefs.getString("cache_time_$appWidgetId", context.getString(R.string.last_update_fallback)) ?: context.getString(R.string.last_update_fallback)
        val weatherJson = prefs.getString("cache_data_$appWidgetId", null)

        val weatherData = if (weatherJson != null) {
            try { json.decodeFromString<WeatherData>(weatherJson) } catch (e: Exception) { null }
        } else null

        updateWidgetUi(context, appWidgetId, weatherData, lat, lon, place, lastUpdateStr)
    }

    private fun fetchWeatherData(lat: Double, lon: Double, tempUnit: String = "celsius", windUnit: String = "beaufort", precipUnit: String = "mm"): WeatherData? {
        return try {
            val apiWindUnit = if (windUnit == "beaufort") "ms" else windUnit
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,relative_humidity_2m,uv_index,weather_code,is_day,wind_speed_10m,wind_direction_10m&hourly=temperature_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m,precipitation,rain,snowfall,precipitation_probability,relative_humidity_2m,uv_index&daily=sunrise,sunset,precipitation_probability_max&wind_speed_unit=$apiWindUnit&temperature_unit=$tempUnit&precipitation_unit=$precipUnit&timezone=auto"
            val responseText = URL(url).readText()
            val response = json.decodeFromString<WeatherResponse>(responseText)

            val currentData = response.current?.let {
                CurrentData(
                    temperature = it.temperature,
                    apparentTemperature = it.apparentTemperature,
                    humidity = it.humidity,
                    uvIndex = it.uvIndex,
                    weatherCode = it.weatherCode,
                    isDay = it.isDay,
                    windSpeed = it.windSpeed,
                    windDirection = it.windDirection
                )
            }

            val chunkedTemps = response.hourly.temperatures.chunked(24)
            val chunkedCodes = response.hourly.weatherCodes.chunked(24)
            val chunkedIsDay = response.hourly.isDay.chunked(24)
            val chunkedSpeeds = response.hourly.windSpeeds.chunked(24)
            val chunkedDirs = response.hourly.windDirections.chunked(24)
            val chunkedRain = response.hourly.rain.chunked(24)
            val chunkedSnow = response.hourly.snowfall.chunked(24)
            val chunkedHumidities = response.hourly.humidities.chunked(24)
            val chunkedUv = response.hourly.uvIndex.chunked(24)
            val chunkedTimes = response.hourly.time.chunked(24)

            val dayNameFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
            val dateFormatter = DateTimeFormatter.ofPattern("dd-MM", Locale.getDefault())

            val days = chunkedTemps.indices.map { i ->
                val times = chunkedTimes[i]
                val ldt = try { LocalDateTime.parse(times.first()) } catch (e: Exception) { null }
                
                val dayLabel = if (ldt != null) {
                    val name = ldt.format(dayNameFormatter).lowercase().replace(".", "")
                    val date = ldt.format(dateFormatter)
                    "$name\n$date"
                } else ""

                DayData(
                    label = dayLabel,
                    temps = chunkedTemps[i].filterIndexed { idx, _ -> idx % 3 == 0 }.take(8),
                    codes = chunkedCodes[i].filterIndexed { idx, _ -> idx % 3 == 0 }.take(8),
                    isDay = chunkedIsDay[i].filterIndexed { idx, _ -> idx % 3 == 0 }.take(8),
                    speeds = chunkedSpeeds[i].filterIndexed { idx, _ -> idx % 3 == 0 }.take(8),
                    dirs = chunkedDirs[i].filterIndexed { idx, _ -> idx % 3 == 0 }.take(8),
                    rain = chunkedRain[i].filterIndexed { idx, _ -> idx % 3 == 0 }.take(8),
                    snow = chunkedSnow[i].filterIndexed { idx, _ -> idx % 3 == 0 }.take(8),
                    humidities = chunkedHumidities[i].filterIndexed { idx, _ -> idx % 3 == 0 }.take(8),
                    uvIndices = chunkedUv[i].filterIndexed { idx, _ -> idx % 3 == 0 }.take(8)
                )
            }

            val times = if (chunkedTimes.isNotEmpty()) {
                chunkedTimes[0].filterIndexed { i, _ -> i % 3 == 0 }.take(8).map {
                    try { LocalDateTime.parse(it).format(DateTimeFormatter.ofPattern("H:mm")) } catch (e: Exception) { "" }
                }
            } else listOf()

            val sunriseRaw = response.daily?.sunrise?.firstOrNull()
            val sunsetRaw = response.daily?.sunset?.firstOrNull()
            val precipProbMax = response.daily?.precipitationProbabilityMax?.firstOrNull()

            WeatherData(days, times, currentData, sunriseRaw, sunsetRaw, precipProbMax)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun updateWidgetUi(context: Context, appWidgetId: Int, data: WeatherData?, lat: Double, lon: Double, place: String, updateTimeStr: String) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val rv = RemoteViews(context.packageName, R.layout.weather_widget)
        
        val cycleIntent = Intent(context, WeatherWidget::class.java).apply {
            action = "nl.baasmail.weathergrid.CYCLE_MODE"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val cyclePI = PendingIntent.getBroadcast(
            context, appWidgetId, cycleIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.widget_root, cyclePI)

        val refreshIntent = Intent(context, WeatherWidget::class.java).apply {
            action = "nl.baasmail.weathergrid.REFRESH"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPI = PendingIntent.getBroadcast(
            context, appWidgetId, refreshIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.btn_refresh, refreshPI)

        val settingsIntent = Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val settingsPI = PendingIntent.getActivity(
            context, appWidgetId, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.btn_settings, settingsPI)

        rv.setTextViewText(R.id.widget_title, place)

        val prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getInt("display_mode_$appWidgetId", 0)
        
        val windUnit = prefs.getString("wind_unit_$appWidgetId", "beaufort") ?: "beaufort"
        val precipUnit = prefs.getString("precip_unit_$appWidgetId", "mm") ?: "mm"

        if (data?.current != null) {
            val c = data.current
            
            val dataHtml = when (mode) {
                1 -> { // Sunrise/Sunset mode
                    val sunrise = formatTime(context, data.sunrise)
                    val sunset = formatTime(context, data.sunset)
                    "<font color='#FFFF88'>↑$sunrise</font>&nbsp;&nbsp;&nbsp;<font color='#FFCC88'>↓$sunset</font>"
                }
                2 -> { // Wind & Humidity mode
                    val windStr = formatWind(context, c.windSpeed, c.windDirection, windUnit, showUnit = true)
                    val rhColor = getHumidityColor(c.humidity)
                    "<font color='#FFFFFF'>$windStr</font>&nbsp;&nbsp;&nbsp;<font color='$rhColor'>RH ${c.humidity}%</font>"
                }
                else -> { // Default: Temp, UV, Precip Prob
                    val tempColor = getTempColor(c.temperature)
                    val uvColor = getUvColor(c.uvIndex)
                    val precipProb = data.precipProbMax ?: 0
                    val tempInt = c.temperature.toInt()
                    val apparentInt = c.apparentTemperature.toInt()
                    val tempDisplay = if (tempInt != apparentInt) "$tempInt° ($apparentInt°)" else "$tempInt°"
                    val probText = context.getString(R.string.precip_prob_format, precipProb)
                    "<font color='$tempColor'>$tempDisplay</font>&nbsp;&nbsp;&nbsp;<font color='$uvColor'>UV ${c.uvIndex.toInt()}</font>&nbsp;&nbsp;&nbsp;<font color='#88CCFF'>$probText</font>"
                }
            }
            
            rv.setTextViewText(R.id.widget_current_data, Html.fromHtml(dataHtml, Html.FROM_HTML_MODE_LEGACY))
            rv.setViewVisibility(R.id.widget_current_data, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.widget_current_data, View.GONE)
        }

        if (data != null) {
            val latStr = String.format(Locale.US, "%.3f", lat)
            val lonStr = String.format(Locale.US, "%.3f", lon)
            val formattedUpdate = formatTime(context, updateTimeStr, includeSeconds = true)
            rv.setTextViewText(R.id.widget_metadata, context.getString(R.string.widget_metadata_format, latStr, lonStr, formattedUpdate))
            
            rv.removeAllViews(R.id.rows_container)
            
            val tempIds = arrayOf(R.id.temp1, R.id.temp2, R.id.temp3, R.id.temp4, R.id.temp5, R.id.temp6, R.id.temp7, R.id.temp8)
            val iconIds = arrayOf(R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4, R.id.icon5, R.id.icon6, R.id.icon7, R.id.icon8)
            val windIds = arrayOf(R.id.wind1, R.id.wind2, R.id.wind3, R.id.wind4, R.id.wind5, R.id.wind6, R.id.wind7, R.id.wind8)
            val precipIds = arrayOf(R.id.precip1, R.id.precip2, R.id.precip3, R.id.precip4, R.id.precip5, R.id.precip6, R.id.precip7, R.id.precip8)

            val headerRv = RemoteViews(context.packageName, R.layout.weather_header_row)
            val hidePast = prefs.getBoolean("hide_past_$appWidgetId", false)

            val currentHour = LocalDateTime.now().hour
            val currentSlotIndex = currentHour / 3

            for (i in 0 until 8) {
                if (i < data.times.size) {
                    headerRv.setTextViewText(tempIds[i], data.times[i])
                    headerRv.setTextColor(tempIds[i], Color.WHITE)
                    headerRv.setViewVisibility(tempIds[i], View.VISIBLE)
                } else {
                    headerRv.setViewVisibility(tempIds[i], View.GONE)
                }
            }
            rv.addView(R.id.rows_container, headerRv)

            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val availableForDays = if (minHeight > 0) minHeight - 45 - 20 else 200
            val maxDayRows = if (availableForDays > 0) (availableForDays / 80) else 1
            val daysToShow = data.days.take(maxOf(1, maxDayRows))

            for ((index, day) in daysToShow.withIndex()) {
                val dayRv = RemoteViews(context.packageName, R.layout.weather_row)
                dayRv.setTextViewText(R.id.day_label, day.label)
                dayRv.setTextColor(R.id.day_label, Color.WHITE)
                
                if (index % 2 == 0) {
                    dayRv.setInt(R.id.row_container, "setBackgroundResource", R.drawable.bg_weather_row_light)
                }

                for (i in 0 until 8) {
                    val shouldHide = index == 0 && hidePast && i < currentSlotIndex
                    if (i < day.temps.size && !shouldHide) {
                        val isCurrentSlot = (index == 0 && i == currentSlotIndex && data.current != null)
                        
                        val temp = if (isCurrentSlot) data.current!!.temperature else day.temps[i]
                        val weatherCode = if (isCurrentSlot) data.current!!.weatherCode else day.codes[i]
                        val isDay = if (isCurrentSlot) data.current!!.isDay == 1 else day.isDay[i] == 1
                        val windSpeed = if (isCurrentSlot) data.current!!.windSpeed else day.speeds[i]
                        val windDir = if (isCurrentSlot) data.current!!.windDirection else day.dirs[i]
                        val humidity = if (isCurrentSlot) data.current!!.humidity else day.humidities[i]
                        val uvIndex = if (isCurrentSlot) data.current!!.uvIndex else day.uvIndices[i]

                        dayRv.setTextViewText(tempIds[i], "${temp.toInt()}°")
                        dayRv.setTextColor(tempIds[i], if (temp > 0) 0xFFFF5555.toInt() else 0xFF5555FF.toInt())
                        dayRv.setViewVisibility(tempIds[i], View.VISIBLE)
                        
                        dayRv.setImageViewResource(iconIds[i], getWeatherIcon(weatherCode, isDay))
                        dayRv.setViewVisibility(iconIds[i], View.VISIBLE)

                        dayRv.setTextViewText(windIds[i], formatWind(context, windSpeed, windDir, windUnit, showUnit = false))
                        dayRv.setTextColor(windIds[i], Color.WHITE)
                        dayRv.setViewVisibility(windIds[i], View.VISIBLE)

                        val dynamicText = when (mode) {
                            1 -> "UV ${uvIndex.toInt()}"
                            2 -> "RH $humidity%"
                            else -> {
                                val rainVal = day.rain[i]
                                val snowVal = day.snow[i]
                                if (snowVal > 0 || rainVal > 0) {
                                    if (snowVal > 0) {
                                        val snowUnit = if (precipUnit == "inch") "in" else "cm"
                                        String.format(Locale.getDefault(), "%.1f", snowVal) + snowUnit
                                    } else {
                                        val unitLabel = if (precipUnit == "inch") "in" else "mm"
                                        String.format(Locale.getDefault(), "%.1f", rainVal) + unitLabel
                                    }
                                } else ""
                            }
                        }

                        if (dynamicText.isNotEmpty()) {
                            dayRv.setTextViewText(precipIds[i], Html.fromHtml(dynamicText, Html.FROM_HTML_MODE_LEGACY))
                            val textColor = when(mode) {
                                1 -> Color.parseColor(getUvColor(uvIndex))
                                2 -> Color.parseColor(getHumidityColor(humidity))
                                else -> 0xFF88CCFF.toInt()
                            }
                            dayRv.setTextColor(precipIds[i], textColor)
                            dayRv.setViewVisibility(precipIds[i], View.VISIBLE)
                        } else {
                            dayRv.setViewVisibility(precipIds[i], View.INVISIBLE)
                        }
                    } else {
                        dayRv.setViewVisibility(tempIds[i], if (shouldHide) View.INVISIBLE else View.GONE)
                        dayRv.setViewVisibility(iconIds[i], if (shouldHide) View.INVISIBLE else View.GONE)
                        dayRv.setViewVisibility(windIds[i], if (shouldHide) View.INVISIBLE else View.GONE)
                        dayRv.setViewVisibility(precipIds[i], if (shouldHide) View.INVISIBLE else View.GONE)
                    }
                }
                rv.addView(R.id.rows_container, dayRv)
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, rv)
    }

    private fun getTempColor(temp: Double): String {
        return if (temp > 0) "#FF5555" else "#5555FF"
    }

    private fun getUvColor(uv: Double): String {
        return when {
            uv <= 2 -> "#55FF55"
            uv <= 5 -> "#FFFF00"
            uv <= 7 -> "#FF8800"
            else -> "#FF0000"
        }
    }

    private fun getHumidityColor(hum: Int): String {
        return when {
            hum <= 30 || hum >= 70 -> "#FFFF00"
            else -> "#FFFFFF"
        }
    }

    private fun formatTime(context: Context, isoString: String?, includeSeconds: Boolean = false): String {
        if (isoString == null) return "--:--"
        return try {
            val ldt = LocalDateTime.parse(isoString)
            val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
            val pattern = if (is24Hour) {
                if (includeSeconds) "HH:mm:ss" else "HH:mm"
            } else {
                if (includeSeconds) "h:mm:ss a" else "h:mm a"
            }
            ldt.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        } catch (e: Exception) {
            // Fallback for old "HH:mm:ss" format in cache
            isoString
        }
    }

    private fun formatWind(context: Context, speed: Double, direction: Int, unit: String, showUnit: Boolean = true): String {
        val dirStr = degreeToDirection(context, direction)
        val valueStr = when (unit) {
            "beaufort" -> msToBeaufort(speed).toString()
            "ms" -> String.format(Locale.US, "%.1f", speed)
            "kmh", "mph", "kn" -> speed.toInt().toString()
            else -> msToBeaufort(speed).toString()
        }
        
        if (!showUnit) return "$dirStr $valueStr"
        
        val unitLabel = when (unit) {
            "beaufort" -> " Bft"
            "ms" -> " m/s"
            "kmh" -> " km/h"
            "mph" -> " mph"
            "kn" -> " kn"
            else -> " Bft"
        }
        return "$dirStr $valueStr$unitLabel"
    }

    private fun unitToBeaufort(value: Double, unit: String): Int {
        val ms = when (unit) {
            "kmh" -> value / 3.6
            "mph" -> value * 0.44704
            "kn" -> value * 0.514444
            else -> value
        }
        return msToBeaufort(ms)
    }

    private fun msToBeaufort(ms: Double): Int {
        return when {
            ms < 0.3 -> 0; ms < 1.6 -> 1; ms < 3.4 -> 2; ms < 5.5 -> 3; ms < 8.0 -> 4; ms < 10.8 -> 5
            ms < 13.9 -> 6; ms < 17.2 -> 7; ms < 20.8 -> 8; ms < 24.5 -> 9; ms < 28.5 -> 10; ms < 32.7 -> 11; else -> 12
        }
    }

    private fun degreeToDirection(context: Context, degree: Int): String {
        val directions = arrayOf(
            context.getString(R.string.direction_n),
            context.getString(R.string.direction_ne),
            context.getString(R.string.direction_e),
            context.getString(R.string.direction_se),
            context.getString(R.string.direction_s),
            context.getString(R.string.direction_sw),
            context.getString(R.string.direction_w),
            context.getString(R.string.direction_nw)
        )
        return directions[((degree + 22.5) % 360 / 45).toInt()]
    }

    private fun getWeatherIcon(code: Int, isDay: Boolean): Int {
        return when (code) {
            0 -> if (isDay) R.drawable.ic_meteo_1_day else R.drawable.ic_meteo_1_night
            1 -> if (isDay) R.drawable.ic_meteo_2_day else R.drawable.ic_meteo_2_night
            2 -> if (isDay) R.drawable.ic_meteo_3_day else R.drawable.ic_meteo_3_night
            3 -> R.drawable.ic_meteo_4
            45, 48 -> R.drawable.ic_meteo_15
            51, 53, 55, 61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_meteo_5
            else -> if (isDay) R.drawable.ic_meteo_1_day else R.drawable.ic_meteo_1_night
        }
    }

    @kotlinx.serialization.Serializable
    data class WeatherData(val days: List<DayData>, val times: List<String>, val current: CurrentData? = null, val sunrise: String? = null, val sunset: String? = null, val precipProbMax: Int? = null)

    @kotlinx.serialization.Serializable
    data class CurrentData(val temperature: Double, val apparentTemperature: Double, val humidity: Int, val uvIndex: Double, val weatherCode: Int, val isDay: Int, val windSpeed: Double, val windDirection: Int)

    @kotlinx.serialization.Serializable
    data class DayData(val label: String, val temps: List<Double>, val codes: List<Int>, val isDay: List<Int>, val speeds: List<Double>, val dirs: List<Int>, val rain: List<Double>, val snow: List<Double>, val humidities: List<Int>, val uvIndices: List<Double>)
}
