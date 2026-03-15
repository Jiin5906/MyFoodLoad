package com.example.myfoodload.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val LOCATION_TIMEOUT_MS = 15_000L

/**
 * FusedLocationProviderClient 기반 위치 제공자.
 *
 * lastLocation → requestLocationUpdates(maxUpdates=1) 폴백.
 * 15초 타임아웃.
 */
@SuppressLint("MissingPermission")
suspend fun getLocation(context: Context): Location? =
    withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        fusedLocationClient.lastLocation.await()
            ?: suspendCancellableCoroutine<Location?> { cont ->
                val request =
                    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                        .setMaxUpdates(1)
                        .setWaitForAccurateLocation(false)
                        .build()
                val cb = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        fusedLocationClient.removeLocationUpdates(this)
                        if (cont.isActive) cont.resume(result.lastLocation ?: result.locations.firstOrNull())
                    }
                }
                fusedLocationClient.requestLocationUpdates(request, cb, Looper.getMainLooper())
                cont.invokeOnCancellation { fusedLocationClient.removeLocationUpdates(cb) }
            }
    }
