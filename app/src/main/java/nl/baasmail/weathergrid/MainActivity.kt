package nl.baasmail.weathergrid

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noWidgetsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Explicitly hide the action bar if it exists
        supportActionBar?.hide()

        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recycler_widgets)
        noWidgetsText = findViewById(R.id.text_no_widgets)
        val btnAddWidget = findViewById<View>(R.id.btn_add_widget)

        recyclerView.layoutManager = LinearLayoutManager(this)

        btnAddWidget.setOnClickListener {
            checkLocationAndAddWidget()
        }
    }

    private fun checkLocationAndAddWidget() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = try {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                } catch (e: Exception) { null }
                
                val cityName = addresses?.firstOrNull()?.locality ?: getString(R.string.current_location_name)
                requestPinWidget(location.latitude, location.longitude, cityName)
            } else {
                // Fallback naar standaard als GPS uit staat of geen fix heeft
                requestPinWidget(51.80, 4.65, "Rotterdam")
            }
        }.addOnFailureListener {
            requestPinWidget(51.80, 4.65, "Rotterdam")
        }
    }

    private fun requestPinWidget(lat: Double, lon: Double, name: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val myProvider = ComponentName(this, WeatherWidget::class.java)

            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                // Callback intent die de data bevat
                val successCallback = Intent(this, WeatherWidget::class.java).apply {
                    action = "nl.baasmail.weathergrid.WIDGET_PINNED"
                    putExtra("auto_lat", lat.toFloat())
                    putExtra("auto_lon", lon.toFloat())
                    putExtra("auto_name", name)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    this, 0, successCallback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                appWidgetManager.requestPinAppWidget(myProvider, null, pendingIntent)
                Toast.makeText(this, R.string.pin_widget_toast, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.pin_unsupported_error, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, R.string.manual_add_error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkLocationAndAddWidget()
        }
    }

    override fun onResume() {
        super.onResume()
        updateWidgetList()
    }

    private fun updateWidgetList() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, WeatherWidget::class.java)
        val allAppWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        val prefs = getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE)
        
        val configuredIds = allAppWidgetIds.filter { id ->
            prefs.contains("place_$id")
        }

        if (configuredIds.isEmpty()) {
            noWidgetsText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noWidgetsText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = WidgetAdapter(this, configuredIds)
        }
    }

    private class WidgetAdapter(
        private val context: Context,
        private val widgetIds: List<Int>
    ) : RecyclerView.Adapter<WidgetViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.item_widget_list, parent, false)
            return WidgetViewHolder(view)
        }

        override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
            val widgetId = widgetIds[position]
            val prefs = context.getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE)
            val place = prefs.getString("place_$widgetId", context.getString(R.string.default_location_name)) ?: context.getString(R.string.default_location_name)
            val lat = prefs.getFloat("lat_$widgetId", 51.80f)
            val lon = prefs.getFloat("lon_$widgetId", 4.65f)

            holder.textPlace.text = place
            holder.textCoords.text = context.getString(R.string.coord_format).format(lat, lon)

            holder.itemView.setOnClickListener {
                val intent = Intent(context, WidgetConfigActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }

        override fun getItemCount(): Int = widgetIds.size
    }

    private class WidgetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textPlace: TextView = view.findViewById(R.id.text_place)
        val textCoords: TextView = view.findViewById(R.id.text_coords)
    }
}
