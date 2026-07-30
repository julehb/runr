package com.example.runr

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Chronometer
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class RunActivity : AppCompatActivity() {
    private lateinit var elapsedTimeChronometer: Chronometer
    private lateinit var locationStatusText: TextView
    private lateinit var locationCoordinatesText: TextView
    private lateinit var distanceText: TextView
    private lateinit var locationTracker: LocationTracker
    private lateinit var mapView: MapView
    private lateinit var routeLine: Polyline
    private lateinit var currentLocationMarker: Marker

    private var lastAcceptedLocation: Location? = null
    private var lastMovementBearingDegrees = 0f
    private var totalDistanceMeters = 0f
    private val routePoints = mutableListOf<GeoPoint>()
    private val simulatedRunHandler = Handler(Looper.getMainLooper())
    private var simulatedRunPointIndex = 0
    private var isSimulatedRunActive = false

    private val simulatedRunStep = object : Runnable {
        override fun run() {
            if (!isSimulatedRunActive || simulatedRunPointIndex >= SIMULATED_RUN_POINTS.size) {
                isSimulatedRunActive = false
                return
            }

            val point = SIMULATED_RUN_POINTS[simulatedRunPointIndex]
            onLocationUpdated(
                Location(SIMULATED_LOCATION_PROVIDER).apply {
                    latitude = point.latitude
                    longitude = point.longitude
                    accuracy = SIMULATED_LOCATION_ACCURACY_METERS
                    time = System.currentTimeMillis() +
                        (simulatedRunPointIndex * SIMULATED_LOCATION_TIME_STEP_MILLIS)
                },
            )
            simulatedRunPointIndex += 1
            simulatedRunHandler.postDelayed(this, SIMULATED_RUN_UPDATE_INTERVAL_MILLIS)
        }
    }

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
        mapView = findViewById(R.id.runMapView)
        locationTracker = LocationTracker(this, ::onLocationUpdated)
        setupMap()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.runRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        restoreRunState(savedInstanceState)

        val chronometerBase = savedInstanceState?.getLong(KEY_CHRONOMETER_BASE)
            ?: SystemClock.elapsedRealtime()
        elapsedTimeChronometer.base = chronometerBase
        if (IS_TIMER_ENABLED_FOR_DEVELOPMENT) {
            elapsedTimeChronometer.start()
        }
        updateDistanceText()
    }

    override fun onStart() {
        super.onStart()

        if (IS_RUN_SIMULATION_ENABLED_FOR_DEVELOPMENT) {
            startSimulatedRun()
            return
        }

        if (hasLocationPermission()) {
            startLocationTracking()
        } else {
            requestLocationPermission()
        }
    }

    override fun onStop() {
        stopSimulatedRun()
        locationTracker.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        elapsedTimeChronometer.stop()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_CHRONOMETER_BASE, elapsedTimeChronometer.base)
        outState.putFloat(KEY_TOTAL_DISTANCE_METERS, totalDistanceMeters)
        outState.putFloat(KEY_LAST_MOVEMENT_BEARING_DEGREES, lastMovementBearingDegrees)
        outState.putDoubleArray(KEY_ROUTE_LATITUDES, routePoints.map { it.latitude }.toDoubleArray())
        outState.putDoubleArray(KEY_ROUTE_LONGITUDES, routePoints.map { it.longitude }.toDoubleArray())

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
        lastMovementBearingDegrees = savedInstanceState.getFloat(KEY_LAST_MOVEMENT_BEARING_DEGREES, 0f)
        restoreRoutePoints(savedInstanceState)
        if (savedInstanceState.getBoolean(KEY_HAS_LAST_LOCATION, false)) {
            lastAcceptedLocation = Location(SAVED_LOCATION_PROVIDER).apply {
                latitude = savedInstanceState.getDouble(KEY_LAST_LATITUDE)
                longitude = savedInstanceState.getDouble(KEY_LAST_LONGITUDE)
                accuracy = savedInstanceState.getFloat(KEY_LAST_ACCURACY)
                time = savedInstanceState.getLong(KEY_LAST_LOCATION_TIME)
            }
            updateLocationText(lastAcceptedLocation)
            updateMap(lastAcceptedLocation, lastMovementBearingDegrees)
        }
    }

    private fun setupMap() {
        mapView.setTileSource(OPEN_STREET_MAP_TILE_SOURCE)
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = MIN_MAP_ZOOM
        mapView.maxZoomLevel = MAX_MAP_ZOOM
        mapView.controller.setZoom(DEFAULT_MAP_ZOOM)
        mapView.controller.setCenter(DEFAULT_MAP_CENTER)

        routeLine = Polyline().apply {
            outlinePaint.color = ContextCompat.getColor(this@RunActivity, R.color.run_route_line)
            outlinePaint.strokeWidth = ROUTE_LINE_WIDTH
            setPoints(routePoints)
        }

        currentLocationMarker = Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(this@RunActivity, R.drawable.current_location_triangle)
            title = getString(R.string.current_location_marker_title)
        }

        mapView.overlays.add(routeLine)
        mapView.overlays.add(currentLocationMarker)
        mapView.invalidate()
    }

    private fun restoreRoutePoints(savedInstanceState: Bundle) {
        val latitudes = savedInstanceState.getDoubleArray(KEY_ROUTE_LATITUDES) ?: return
        val longitudes = savedInstanceState.getDoubleArray(KEY_ROUTE_LONGITUDES) ?: return
        val pointCount = minOf(latitudes.size, longitudes.size)

        routePoints.clear()
        for (index in 0 until pointCount) {
            routePoints.add(GeoPoint(latitudes[index], longitudes[index]))
        }
        routeLine.setPoints(routePoints)
    }

    private fun startLocationTracking() {
        locationStatusText.setText(R.string.location_waiting)
        locationTracker.start()
    }

    private fun startSimulatedRun() {
        if (isSimulatedRunActive) return

        locationStatusText.setText(R.string.simulated_run_active)
        isSimulatedRunActive = true
        simulatedRunHandler.post(simulatedRunStep)
    }

    private fun stopSimulatedRun() {
        isSimulatedRunActive = false
        simulatedRunHandler.removeCallbacks(simulatedRunStep)
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
        val movementBearingDegrees = previousLocation?.bearingTo(location)
            ?: if (location.hasBearing()) location.bearing else lastMovementBearingDegrees

        if (previousLocation != null) {
            val distanceToPrevious = previousLocation.distanceTo(location)
            if (distanceToPrevious >= MIN_DISTANCE_DELTA_METERS &&
                isPlausibleRunSegment(previousLocation, location)
            ) {
                totalDistanceMeters += distanceToPrevious
                lastMovementBearingDegrees = movementBearingDegrees
            }
        } else {
            lastMovementBearingDegrees = movementBearingDegrees
        }

        lastAcceptedLocation = location
        updateLocationText(location)
        updateDistanceText()
        updateMap(location, lastMovementBearingDegrees)
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

    private fun updateMap(location: Location?, movementBearingDegrees: Float) {
        if (location == null) return

        val geoPoint = GeoPoint(location.latitude, location.longitude)
        if (routePoints.lastOrNull() != geoPoint) {
            routePoints.add(geoPoint)
            routeLine.setPoints(routePoints)
        }

        currentLocationMarker.position = geoPoint
        currentLocationMarker.rotation = toMarkerRotation(movementBearingDegrees)
        mapView.controller.animateTo(geoPoint)
        mapView.invalidate()
    }

    private fun toMarkerRotation(movementBearingDegrees: Float): Float {
        return -movementBearingDegrees
    }

    companion object {
        private const val KEY_CHRONOMETER_BASE = "chronometerBase"
        private const val KEY_TOTAL_DISTANCE_METERS = "totalDistanceMeters"
        private const val KEY_LAST_MOVEMENT_BEARING_DEGREES = "lastMovementBearingDegrees"
        private const val KEY_ROUTE_LATITUDES = "routeLatitudes"
        private const val KEY_ROUTE_LONGITUDES = "routeLongitudes"
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
        private const val IS_TIMER_ENABLED_FOR_DEVELOPMENT = false
        private const val IS_RUN_SIMULATION_ENABLED_FOR_DEVELOPMENT = true
        private const val SIMULATED_LOCATION_PROVIDER = "simulated"
        private const val SIMULATED_LOCATION_ACCURACY_METERS = 8f
        private const val SIMULATED_LOCATION_TIME_STEP_MILLIS = 3_000L
        private const val SIMULATED_RUN_UPDATE_INTERVAL_MILLIS = 750L
        private const val MIN_MAP_ZOOM = 3.0
        private const val MAX_MAP_ZOOM = 20.0
        private const val DEFAULT_MAP_ZOOM = 16.0
        private const val ROUTE_LINE_WIDTH = 8f
        private val DEFAULT_MAP_CENTER = GeoPoint(52.52, 13.405)
        private val SIMULATED_RUN_POINTS = listOf(
            GeoPoint(52.52000, 13.40500),
            GeoPoint(52.52012, 13.40518),
            GeoPoint(52.52025, 13.40534),
            GeoPoint(52.52039, 13.40548),
            GeoPoint(52.52054, 13.40561),
            GeoPoint(52.52070, 13.40570),
            GeoPoint(52.52087, 13.40576),
            GeoPoint(52.52104, 13.40578),
            GeoPoint(52.52121, 13.40575),
            GeoPoint(52.52136, 13.40567),
            GeoPoint(52.52149, 13.40554),
            GeoPoint(52.52159, 13.40537),
            GeoPoint(52.52166, 13.40517),
            GeoPoint(52.52169, 13.40495),
            GeoPoint(52.52168, 13.40473),
            GeoPoint(52.52163, 13.40452),
            GeoPoint(52.52154, 13.40434),
            GeoPoint(52.52141, 13.40420),
            GeoPoint(52.52126, 13.40410),
            GeoPoint(52.52109, 13.40405),
            GeoPoint(52.52092, 13.40404),
            GeoPoint(52.52075, 13.40408),
            GeoPoint(52.52059, 13.40417),
            GeoPoint(52.52045, 13.40431),
            GeoPoint(52.52034, 13.40448),
            GeoPoint(52.52025, 13.40467),
            GeoPoint(52.52018, 13.40487),
            GeoPoint(52.52010, 13.40500),
            GeoPoint(52.52000, 13.40500),
        )
        private val OPEN_STREET_MAP_TILE_SOURCE = XYTileSource(
            "OpenStreetMap",
            0,
            19,
            256,
            ".png",
            arrayOf("https://tile.openstreetmap.org/"),
            "OpenStreetMap contributors",
        )
    }
}
