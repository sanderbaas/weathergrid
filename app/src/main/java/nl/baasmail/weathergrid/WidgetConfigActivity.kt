package nl.baasmail.weathergrid

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var searchCity: AutoCompleteTextView
    private lateinit var editPlaceName: EditText
    private lateinit var editLat: EditText
    private lateinit var editLon: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        setResult(RESULT_CANCELED)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        searchCity = findViewById(R.id.search_city)
        editPlaceName = findViewById(R.id.edit_place_name)
        editLat = findViewById(R.id.edit_lat)
        editLon = findViewById(R.id.edit_lon)
        val btnGetLocation = findViewById<Button>(R.id.btn_get_location)
        val btnCancel = findViewById<Button>(R.id.btn_cancel)
        val btnSave = findViewById<Button>(R.id.btn_save)

        val prefs = getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE)
        editPlaceName.setText(prefs.getString("place_$appWidgetId", getString(R.string.new_location_default)))
        editLat.setText(String.format(Locale.US, "%.3f", prefs.getFloat("lat_$appWidgetId", 51.80f)))
        editLon.setText(String.format(Locale.US, "%.3f", prefs.getFloat("lon_$appWidgetId", 4.65f)))

        setupGoogleAutocomplete()

        btnGetLocation.setOnClickListener {
            getLocation()
        }

        btnCancel.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveAndFinish()
        }
    }

    private fun setupGoogleAutocomplete() {
        val adapter = GoogleCityAdapter(this)
        searchCity.setAdapter(adapter)
        
        searchCity.setOnItemClickListener { parent, _, position, _ ->
            val address = parent.getItemAtPosition(position) as Address
            val name = address.locality ?: address.featureName ?: address.getAddressLine(0)
            fillFields(name, address.latitude, address.longitude)
            searchCity.setText("")
            searchCity.clearFocus()
        }
    }

    private fun fillFields(name: String, lat: Double, lon: Double) {
        editPlaceName.setText(name)
        editLat.setText(String.format(Locale.US, "%.3f", lat))
        editLon.setText(String.format(Locale.US, "%.3f", lon))
    }

    private class GoogleCityAdapter(context: Context) : 
        ArrayAdapter<Address>(context, android.R.layout.simple_dropdown_item_1line) {
        
        private val geocoder = Geocoder(context)
        private var results: List<Address> = emptyList()

        override fun getCount(): Int = results.size
        override fun getItem(position: Int): Address? = results.getOrNull(position)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            val addr = getItem(position)
            if (addr != null) {
                val parts = mutableListOf<String>()
                for (i in 0..addr.maxAddressLineIndex) {
                    parts.add(addr.getAddressLine(i))
                }
                view.text = parts.joinToString(", ")
            }
            return view
        }

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val filterResults = FilterResults()
                    if (constraint != null && constraint.length >= 3) {
                        try {
                            @Suppress("DEPRECATION")
                            val list = geocoder.getFromLocationName(constraint.toString(), 5)
                            if (list != null) {
                                filterResults.values = list
                                filterResults.count = list.size
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    return filterResults
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    this@GoogleCityAdapter.results = results?.values as? List<Address> ?: emptyList()
                    if (this@GoogleCityAdapter.results.isNotEmpty()) {
                        notifyDataSetChanged()
                    } else {
                        notifyDataSetInvalidated()
                    }
                }

                override fun convertResultToString(resultValue: Any?): CharSequence {
                    val addr = resultValue as? Address
                    return addr?.locality ?: addr?.featureName ?: ""
                }
            }
        }
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            null
        }

        if (location != null) {
            lifecycleScope.launch {
                val geocoder = Geocoder(this@WidgetConfigActivity)
                val addresses = withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                val name = if (!addresses.isNullOrEmpty()) {
                    addresses[0].locality ?: addresses[0].featureName ?: getString(R.string.my_location_name)
                } else {
                    getString(R.string.current_location_name)
                }

                fillFields(name, location.latitude, location.longitude)
                Toast.makeText(this@WidgetConfigActivity, getString(R.string.location_retrieved_toast, name), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.location_error_toast, Toast.LENGTH_LONG).show()
        }
    }

    private fun saveAndFinish() {
        val place = editPlaceName.text.toString()
        val lat = editLat.text.toString().toFloatOrNull() ?: 51.80f
        val lon = editLon.text.toString().toFloatOrNull() ?: 4.65f

        val prefs = getSharedPreferences("weather_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("place_$appWidgetId", place)
            putFloat("lat_$appWidgetId", lat)
            putFloat("lon_$appWidgetId", lon)
            apply()
        }

        val intentUpdate = Intent(this, WeatherWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        sendBroadcast(intentUpdate)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLocation()
        }
    }
}
