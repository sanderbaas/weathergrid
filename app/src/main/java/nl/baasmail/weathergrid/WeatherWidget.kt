package nl.baasmail.weathergrid

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.*

class WeatherWidget : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WeatherUpdateWorker.schedule(context)
        for (appWidgetId in appWidgetIds) {
            refreshData(context, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        refreshData(context, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        
        if (action == "nl.baasmail.weathergrid.WIDGET_PINNED") {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val lat = intent.getFloatExtra("auto_lat", 51.80f)
                val lon = intent.getFloatExtra("auto_lon", 4.65f)
                val name = intent.getStringExtra("auto_name") ?: "Locatie"

                val prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putFloat("lat_$appWidgetId", lat)
                    putFloat("lon_$appWidgetId", lon)
                    putString("place_$appWidgetId", name)
                    apply()
                }

                refreshData(context, appWidgetId)
                WeatherUpdateWorker.schedule(context)
            }
        }

        if (action == "nl.baasmail.weathergrid.REFRESH") {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                refreshData(context, appWidgetId)
            }
        }

        if (action == "nl.baasmail.weathergrid.CYCLE_MODE") {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                cycleDisplayMode(context, appWidgetId)
            }
        }
    }

    private fun cycleDisplayMode(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE)
        val currentMode = prefs.getInt("display_mode_$appWidgetId", 0)
        val nextMode = (currentMode + 1) % 3 // 0: Precip, 1: UV, 2: Humidity
        
        prefs.edit().putInt("display_mode_$appWidgetId", nextMode).apply()
        
        // Gebruik redrawWidget in plaats van refreshData om herladen van data 
        // en het bijwerken van de tijdstempel te voorkomen.
        WeatherManager.redrawWidget(context, appWidgetId)
    }

    private fun refreshData(context: Context, appWidgetId: Int) {
        scope.launch {
            WeatherManager.refreshWidget(context, appWidgetId)
        }
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        job.cancel()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            for (appWidgetId in appWidgetIds) {
                remove("place_$appWidgetId")
                remove("lat_$appWidgetId")
                remove("lon_$appWidgetId")
                remove("display_mode_$appWidgetId")
            }
            apply()
        }
        super.onDeleted(context, appWidgetIds)
    }
}
