package com.example.runr

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationTracker(
    context: Context,
    private val onLocationUpdated: (Location) -> Unit,
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        LOCATION_UPDATE_INTERVAL_MILLIS,
    )
        .setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL_MILLIS)
        .setMinUpdateDistanceMeters(MIN_LOCATION_UPDATE_DISTANCE_METERS)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let(onLocationUpdated)
        }
    }

    private var isTracking = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (isTracking) return

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper(),
        )
        isTracking = true
    }

    fun stop() {
        if (!isTracking) return

        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTracking = false
    }

    companion object {
        private const val LOCATION_UPDATE_INTERVAL_MILLIS = 1_000L
        private const val FASTEST_LOCATION_UPDATE_INTERVAL_MILLIS = 500L
        private const val MIN_LOCATION_UPDATE_DISTANCE_METERS = 2f
    }
}
