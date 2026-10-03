package com.pothole.khadda.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

/**
 * LocationTracker manages GPS and network location updates.
 * Matches UML Class Diagram: LocationService.
 */
class LocationTracker(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var currentLocation: Location? = null
        private set

    var isGPSEnabled: Boolean = false
        private set

    val accuracy: Float get() = currentLocation?.accuracy ?: 0f
    val provider: String get() = currentLocation?.provider ?: "GPS"

    var onLocationChangedListener: ((Location) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(minTimeMs: Long = 1000L, minDistanceMeters: Float = 1.0f): Boolean {
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGPSEnabled) return false

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTimeMs,
                    minDistanceMeters,
                    this
                )
                val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (lastKnown != null) currentLocation = lastKnown
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minTimeMs,
                    minDistanceMeters,
                    this
                )
                if (currentLocation == null) {
                    currentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
            }
            return true
        } catch (e: SecurityException) {
            return false
        }
    }

    fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            // Ignore on cleanup
        }
    }

    fun getLocation(): Location? = currentLocation

    override fun onLocationChanged(location: Location) {
        currentLocation = location
        onLocationChangedListener?.invoke(location)
    }

    @Deprecated("Deprecated in Android API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {
        isGPSEnabled = true
    }

    override fun onProviderDisabled(provider: String) {
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
}
