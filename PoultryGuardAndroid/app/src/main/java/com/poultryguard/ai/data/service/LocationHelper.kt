package com.poultryguard.ai.data.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

data class GpsLocationResult(
    val latitude: Double,
    val longitude: Double,
    val address: String
)

sealed class LocationFetchError(message: String) : Exception(message) {
    class PermissionDenied(message: String = "Location permission is required to detect GPS coordinates.") : LocationFetchError(message)
    class ServicesDisabled(message: String = "GPS is disabled. Please turn on Location services in your device settings.") : LocationFetchError(message)
    class Unavailable(message: String = "Unable to retrieve device GPS coordinates. Please ensure you have GPS signal.") : LocationFetchError(message)
}

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val finePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarsePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return finePermission || coarsePermission
    }

    fun isLocationServiceEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    suspend fun getCurrentGpsLocation(context: Context): Result<GpsLocationResult> = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) {
            return@withContext Result.failure(LocationFetchError.PermissionDenied())
        }

        if (!isLocationServiceEnabled(context)) {
            return@withContext Result.failure(LocationFetchError.ServicesDisabled())
        }

        val location = fetchFusedLocation(context) ?: fetchFrameworkLocation(context)

        if (location == null) {
            return@withContext Result.failure(LocationFetchError.Unavailable())
        }

        val address = reverseGeocode(context, location.latitude, location.longitude)

        Result.success(
            GpsLocationResult(
                latitude = location.latitude,
                longitude = location.longitude,
                address = address
            )
        )
    }

    private suspend fun fetchFusedLocation(context: Context): Location? {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                            .addOnSuccessListener { loc ->
                                if (loc != null) {
                                    cont.resume(loc)
                                } else {
                                    // Fallback to last known location if getCurrentLocation returned null
                                    fusedClient.lastLocation
                                        .addOnSuccessListener { lastLoc -> cont.resume(lastLoc) }
                                        .addOnFailureListener { cont.resume(null) }
                                }
                            }
                            .addOnFailureListener {
                                cont.resume(null)
                            }
                    } catch (se: SecurityException) {
                        cont.resume(null)
                    }

                    cont.invokeOnCancellation {
                        cts.cancel()
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchFrameworkLocation(context: Context): Location? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null

            var bestLocation: Location? = null
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                bestLocation = when {
                    gpsLoc != null && netLoc != null -> {
                        if (gpsLoc.time > netLoc.time) gpsLoc else netLoc
                    }
                    gpsLoc != null -> gpsLoc
                    else -> netLoc
                }
            }
            bestLocation
        } catch (e: SecurityException) {
            null
        }
    }

    suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) {
                return@withContext formatFallbackCoordinates(latitude, longitude)
            }

            val geocoder = Geocoder(context, Locale.getDefault())

            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(results: MutableList<Address>) {
                            cont.resume(results)
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resume(emptyList())
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)
            }

            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val parts = mutableListOf<String>()

                val street = addr.subThoroughfare?.let { num ->
                    addr.thoroughfare?.let { th -> "$num $th" } ?: num
                } ?: addr.thoroughfare

                if (!street.isNullOrBlank()) parts.add(street)

                val locality = addr.locality ?: addr.subLocality ?: addr.subAdminArea
                if (!locality.isNullOrBlank() && !parts.contains(locality)) parts.add(locality)

                val state = addr.adminArea
                if (!state.isNullOrBlank() && !parts.contains(state)) parts.add(state)

                val country = addr.countryName
                if (!country.isNullOrBlank() && !parts.contains(country)) parts.add(country)

                if (parts.isNotEmpty()) {
                    return@withContext parts.joinToString(", ")
                }

                val fullLine = addr.getAddressLine(0)
                if (!fullLine.isNullOrBlank()) {
                    return@withContext fullLine
                }
            }
        } catch (e: IOException) {
            // Geocoder service timeout or no network
        } catch (e: Exception) {
            // Geocoding failure
        }

        formatFallbackCoordinates(latitude, longitude)
    }

    private fun formatFallbackCoordinates(latitude: Double, longitude: Double): String {
        return String.format(Locale.US, "GPS: %.5f, %.5f", latitude, longitude)
    }
}
