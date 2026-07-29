package com.example.runr

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.SystemClock
import android.widget.Chronometer
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class RunActivity : AppCompatActivity() {
    private lateinit var elapsedTimeChronometer: Chronometer
    private lateinit var locationStatusText: TextView
    private lateinit var locationCoordinatesText: TextView
    private lateinit var distanceText: TextView
    private lateinit var locationTracker: LocationTracker

    private var lastAcceptedLocation: Location? = null
    private var totalDistanceMeters = 0f

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (hasLocationPermission) {
            startLocationTracking()
        } else {
            locationStatusText.setText(R.string.location_permission_required)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_run)

        elapsedTimeChronometer = findViewById(R.id.elapsedTimeChronometer)
        locationStatusText = findViewById(R.id.locationStatusText)
        locationCoordinatesText = findViewById(R.id.locationCoordinatesText)
        distanceText = findViewById(R.id.distanceText)
        locationTracker = LocationTracker(this, ::onLocationUpdated)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.runRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        restoreRunState(savedInstanceState)

        val chronometerBase = savedInstanceState?.getLong(KEY_CHRONOMETER_BASE)
            ?: SystemClock.elapsedRealtime()
        elapsedTimeChronometer.base = chronometerBase
        elapsedTimeChronometer.start()
        updateDistanceText()
    }

    override fun onStart() {
        super.onStart()

        if (hasLocationPermission()) {
            startLocationTracking()
        } else {
            requestLocationPermission()
        }
    }

    override fun onStop() {
        locationTracker.stop()
        super.onStop()
    }

    override fun onDestroy() {
        elapsedTimeChronometer.stop()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_CHRONOMETER_BASE, elapsedTimeChronometer.base)
        outState.putFloat(KEY_TOTAL_DISTANCE_METERS, totalDistanceMeters)

        lastAcceptedLocation?.let { location ->
            outState.putBoolean(KEY_HAS_LAST_LOCATION, true)
            outState.putDouble(KEY_LAST_LATITUDE, location.latitude)
            outState.putDouble(KEY_LAST_LONGITUDE, location.longitude)
            outState.putFloat(KEY_LAST_ACCURACY, location.accuracy)
            outState.putLong(KEY_LAST_LOCATION_TIME, location.time)
        }
    }

    private fun restoreRunState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return

        totalDistanceMeters = savedInstanceState.getFloat(KEY_TOTAL_DISTANCE_METERS, 0f)
        if (savedInstanceState.getBoolean(KEY_HAS_LAST_LOCATION, false)) {
            lastAcceptedLocation = Location(SAVED_LOCATION_PROVIDER).apply {
                latitude = savedInstanceState.getDouble(KEY_LAST_LATITUDE)
                longitude = savedInstanceState.getDouble(KEY_LAST_LONGITUDE)
                accuracy = savedInstanceState.getFloat(KEY_LAST_ACCURACY)
                time = savedInstanceState.getLong(KEY_LAST_LOCATION_TIME)
            }
            updateLocationText(lastAcceptedLocation)
        }
    }

    private fun startLocationTracking() {
        locationStatusText.setText(R.string.location_waiting)
        locationTracker.start()
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        return fineLocationPermission == PackageManager.PERMISSION_GRANTED ||
            coarseLocationPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun onLocationUpdated(location: Location) {
        if (!location.hasAccuracy() || location.accuracy > MAX_ACCEPTED_ACCURACY_METERS) {
            return
        }

        val previousLocation = lastAcceptedLocation
        if (previousLocation != null) {
            val distanceToPrevious = previousLocation.distanceTo(location)
            if (distanceToPrevious >= MIN_DISTANCE_DELTA_METERS &&
                isPlausibleRunSegment(previousLocation, location)
            ) {
                totalDistanceMeters += distanceToPrevious
            }
        }

        lastAcceptedLocation = location
        updateLocationText(location)
        updateDistanceText()
    }

    private fun isPlausibleRunSegment(previousLocation: Location, location: Location): Boolean {
        val elapsedSeconds = (location.time - previousLocation.time) / 1_000f
        if (elapsedSeconds <= 0f) return false

        val speedMetersPerSecond = previousLocation.distanceTo(location) / elapsedSeconds
        return speedMetersPerSecond <= MAX_PLAUSIBLE_RUNNING_SPEED_METERS_PER_SECOND
    }

    private fun updateLocationText(location: Location?) {
        if (location == null) return

        locationStatusText.text = getString(R.string.location_accuracy, location.accuracy)
        locationCoordinatesText.text = getString(
            R.string.location_coordinates,
            location.latitude,
            location.longitude,
        )
    }

    private fun updateDistanceText() {
        distanceText.text = getString(
            R.string.distance_kilometers,
            totalDistanceMeters / METERS_PER_KILOMETER,
        )
    }

    companion object {
        private const val KEY_CHRONOMETER_BASE = "chronometerBase"
        private const val KEY_TOTAL_DISTANCE_METERS = "totalDistanceMeters"
        private const val KEY_HAS_LAST_LOCATION = "hasLastLocation"
        private const val KEY_LAST_LATITUDE = "lastLatitude"
        private const val KEY_LAST_LONGITUDE = "lastLongitude"
        private const val KEY_LAST_ACCURACY = "lastAccuracy"
        private const val KEY_LAST_LOCATION_TIME = "lastLocationTime"
        private const val SAVED_LOCATION_PROVIDER = "saved"
        private const val MAX_ACCEPTED_ACCURACY_METERS = 50f
        private const val MIN_DISTANCE_DELTA_METERS = 1f
        private const val MAX_PLAUSIBLE_RUNNING_SPEED_METERS_PER_SECOND = 12f
        private const val METERS_PER_KILOMETER = 1_000f
    }
}
