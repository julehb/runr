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
import androidx.appcompat.widget.AppCompatButton
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
    private lateinit var pauseRunButton: AppCompatButton
    private lateinit var resetRunButton: AppCompatButton
    private lateinit var locationStatusText: TextView
    private lateinit var locationCoordinatesText: TextView
    private lateinit var distanceText: TextView
    private lateinit var paceText: TextView
    private lateinit var locationTracker: LocationTracker
    private lateinit var mapView: MapView
    private lateinit var routeLine: Polyline
    private lateinit var currentLocationMarker: Marker

    private var lastAcceptedLocation: Location? = null
    private var lastMovementBearingDegrees = 0f
    private var isTimerRunning = false
    private var pausedAtElapsedRealtime = 0L
    private var shouldAnchorNextLocation = false
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

            if (!isTimerRunning) {
                simulatedRunHandler.postDelayed(this, SIMULATED_RUN_UPDATE_INTERVAL_MILLIS)
                return
            }

            val point = SIMULATED_RUN_POINTS[simulatedRunPointIndex]
            onLocationUpdated(
                Location(SIMULATED_LOCATION_PROVIDER).apply {
                    latitude = point.latitude
                    longitude = point.longitude
                    accuracy = SIMULATED_LOCATION_ACCURACY_METERS
                    time = System.currentTimeMillis()
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
        pauseRunButton = findViewById(R.id.pauseRunButton)
        resetRunButton = findViewById(R.id.resetRunButton)
        locationStatusText = findViewById(R.id.locationStatusText)
        locationCoordinatesText = findViewById(R.id.locationCoordinatesText)
        distanceText = findViewById(R.id.distanceText)
        paceText = findViewById(R.id.paceText)
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
        isTimerRunning = savedInstanceState?.getBoolean(KEY_IS_TIMER_RUNNING)
            ?: IS_TIMER_ENABLED_FOR_DEVELOPMENT
        pausedAtElapsedRealtime = savedInstanceState?.getLong(KEY_PAUSED_AT_ELAPSED_REALTIME)
            ?: SystemClock.elapsedRealtime()
        shouldAnchorNextLocation = savedInstanceState?.getBoolean(KEY_SHOULD_ANCHOR_NEXT_LOCATION)
            ?: false
        if (isTimerRunning) {
            startTimer()
        } else {
            elapsedTimeChronometer.stop()
            updatePauseButtonText()
        }
        pauseRunButton.setOnClickListener { toggleTimer() }
        resetRunButton.setOnClickListener { resetRun() }
        updateDistanceText()
        updatePaceText()
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
        outState.putBoolean(KEY_IS_TIMER_RUNNING, isTimerRunning)
        outState.putLong(KEY_PAUSED_AT_ELAPSED_REALTIME, pausedAtElapsedRealtime)
        outState.putBoolean(KEY_SHOULD_ANCHOR_NEXT_LOCATION, shouldAnchorNextLocation)
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

    private fun toggleTimer() {
        if (isTimerRunning) {
            pauseTimer()
        } else {
            resumeTimer()
        }
        updatePaceText()
    }

    private fun startTimer() {
        elapsedTimeChronometer.start()
        isTimerRunning = true
        updatePauseButtonText()
    }

    private fun pauseTimer() {
        pausedAtElapsedRealtime = SystemClock.elapsedRealtime()
        elapsedTimeChronometer.stop()
        isTimerRunning = false
        shouldAnchorNextLocation = true
        updatePauseButtonText()
    }

    private fun resumeTimer() {
        val pausedDurationMillis = SystemClock.elapsedRealtime() - pausedAtElapsedRealtime
        elapsedTimeChronometer.base += pausedDurationMillis
        startTimer()
    }

    private fun updatePauseButtonText() {
        pauseRunButton.setText(if (isTimerRunning) R.string.pause_run else R.string.resume_run)
    }

    private fun resetRun() {
        val resetTime = SystemClock.elapsedRealtime()
        elapsedTimeChronometer.base = resetTime
        pausedAtElapsedRealtime = resetTime
        totalDistanceMeters = 0f
        shouldAnchorNextLocation = true

        routePoints.clear()
        routeLine.setPoints(routePoints)
        resetSimulatedRunIfFinished()

        lastAcceptedLocation?.let { location ->
            updateMap(location, lastMovementBearingDegrees, shouldAddRoutePoint = false)
        } ?: mapView.invalidate()

        if (isTimerRunning) {
            elapsedTimeChronometer.start()
        } else {
            elapsedTimeChronometer.stop()
        }
        updateDistanceText()
        updatePaceText()
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

        if (!isTimerRunning) {
            return
        }

        if (shouldAnchorNextLocation) {
            anchorLocationAfterPause(location)
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
        updatePaceText()
        updateMap(location, lastMovementBearingDegrees)
    }

    private fun anchorLocationAfterPause(location: Location) {
        lastAcceptedLocation = location
        shouldAnchorNextLocation = false
        updateLocationText(location)
        updateMap(location, lastMovementBearingDegrees, shouldAddRoutePoint = routePoints.isEmpty())
    }

    private fun resetSimulatedRunIfFinished() {
        if (IS_RUN_SIMULATION_ENABLED_FOR_DEVELOPMENT &&
            simulatedRunPointIndex >= SIMULATED_RUN_POINTS.size
        ) {
            simulatedRunPointIndex = 0
            if (!isSimulatedRunActive) {
                startSimulatedRun()
            }
        }
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

    private fun updatePaceText() {
        if (totalDistanceMeters <= 0f) {
            paceText.setText(R.string.pace_placeholder)
            return
        }

        val elapsedSeconds = (
            getElapsedRunTimeMillis() / MILLIS_PER_SECOND
            ).coerceAtLeast(1L)
        val paceSecondsPerKilometer = (
            elapsedSeconds / (totalDistanceMeters / METERS_PER_KILOMETER)
            ).toInt()
        val paceMinutes = paceSecondsPerKilometer / SECONDS_PER_MINUTE
        val paceSeconds = paceSecondsPerKilometer % SECONDS_PER_MINUTE
        paceText.text = getString(R.string.pace_minutes_per_kilometer, paceMinutes, paceSeconds)
    }

    private fun updateMap(
        location: Location?,
        movementBearingDegrees: Float,
        shouldAddRoutePoint: Boolean = true,
    ) {
        if (location == null) return

        val geoPoint = GeoPoint(location.latitude, location.longitude)
        if (shouldAddRoutePoint && routePoints.lastOrNull() != geoPoint) {
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

    private fun getElapsedRunTimeMillis(): Long {
        val elapsedRealtime = if (isTimerRunning) {
            SystemClock.elapsedRealtime()
        } else {
            pausedAtElapsedRealtime
        }
        return elapsedRealtime - elapsedTimeChronometer.base
    }

    companion object {
        private const val KEY_CHRONOMETER_BASE = "chronometerBase"
        private const val KEY_IS_TIMER_RUNNING = "isTimerRunning"
        private const val KEY_PAUSED_AT_ELAPSED_REALTIME = "pausedAtElapsedRealtime"
        private const val KEY_SHOULD_ANCHOR_NEXT_LOCATION = "shouldAnchorNextLocation"
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
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60
        private const val IS_TIMER_ENABLED_FOR_DEVELOPMENT = true
        private const val IS_RUN_SIMULATION_ENABLED_FOR_DEVELOPMENT = true
        private const val SIMULATED_LOCATION_PROVIDER = "simulated"
        private const val SIMULATED_LOCATION_ACCURACY_METERS = 8f
        private const val SIMULATED_RUN_UPDATE_INTERVAL_MILLIS = 1_000L
        private const val SIMULATED_RUN_DURATION_MILLIS = 5 * 60 * MILLIS_PER_SECOND
        private const val MIN_MAP_ZOOM = 3.0
        private const val MAX_MAP_ZOOM = 20.0
        private const val DEFAULT_MAP_ZOOM = 16.0
        private const val ROUTE_LINE_WIDTH = 8f
        private val DEFAULT_MAP_CENTER = GeoPoint(52.52, 13.405)
        private val SIMULATED_ROUTE_CORNERS = listOf(
            GeoPoint(52.519102, 13.402786),
            GeoPoint(52.519102, 13.407214),
            GeoPoint(52.520898, 13.407214),
            GeoPoint(52.520898, 13.402786),
            GeoPoint(52.519102, 13.402786),
        )
        private val SIMULATED_RUN_POINTS = buildSimulatedRunPoints()
        private val OPEN_STREET_MAP_TILE_SOURCE = XYTileSource(
            "OpenStreetMap",
            0,
            19,
            256,
            ".png",
            arrayOf("https://tile.openstreetmap.org/"),
            "OpenStreetMap contributors",
        )

        private fun buildSimulatedRunPoints(): List<GeoPoint> {
            val segmentDistances = SIMULATED_ROUTE_CORNERS.zipWithNext { start, end ->
                distanceBetween(start, end)
            }
            val routeDistanceMeters = segmentDistances.sum()
            val stepCount = (SIMULATED_RUN_DURATION_MILLIS / SIMULATED_RUN_UPDATE_INTERVAL_MILLIS).toInt()

            return (0..stepCount).map { step ->
                val targetDistanceMeters = routeDistanceMeters * step / stepCount
                interpolateRoutePoint(segmentDistances, targetDistanceMeters)
            }
        }

        private fun interpolateRoutePoint(
            segmentDistances: List<Float>,
            targetDistanceMeters: Float,
        ): GeoPoint {
            var remainingDistanceMeters = targetDistanceMeters
            SIMULATED_ROUTE_CORNERS.zipWithNext().forEachIndexed { index, (start, end) ->
                val segmentDistanceMeters = segmentDistances[index]
                if (remainingDistanceMeters <= segmentDistanceMeters) {
                    val progress = remainingDistanceMeters / segmentDistanceMeters
                    return GeoPoint(
                        start.latitude + (end.latitude - start.latitude) * progress,
                        start.longitude + (end.longitude - start.longitude) * progress,
                    )
                }
                remainingDistanceMeters -= segmentDistanceMeters
            }

            return SIMULATED_ROUTE_CORNERS.last()
        }

        private fun distanceBetween(start: GeoPoint, end: GeoPoint): Float {
            val result = FloatArray(1)
            Location.distanceBetween(
                start.latitude,
                start.longitude,
                end.latitude,
                end.longitude,
                result,
            )
            return result[0]
        }
    }
}
