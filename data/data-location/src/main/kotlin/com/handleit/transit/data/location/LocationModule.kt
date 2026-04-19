package com.handleit.transit.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.handleit.transit.common.TransitConfig
import com.handleit.transit.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class LocationModule @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocation: FusedLocationProviderClient,
    private val geofencingClient: GeofencingClient,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val accelBuffer = mutableListOf<Float>()
    private var wifiConfidence = -1f

    private val knownTransitSsids = setOf("lynx", "sunrail", "hart", "psta", "transit")

    // ── Live location stream ──────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun locationFlow(fastMs: Long = 2_000L, slowMs: Long = 5_000L): Flow<LocationSnapshot> =
        callbackFlow {
            if (!hasLocationPermission()) { close(); return@callbackFlow }

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, fastMs)
                .setMinUpdateIntervalMillis(fastMs)
                .setMaxUpdateDelayMillis(slowMs)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        trySend(LocationSnapshot(
                            latLng = LatLng(loc.latitude, loc.longitude),
                            accuracyMeters = loc.accuracy,
                            speedMps = loc.speed,
                            bearingDeg = loc.bearing,
                            timestampMs = loc.time,
                        ))
                    }
                }
            }

            sensorManager.registerListener(this@LocationModule, accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL)

            fusedLocation.requestLocationUpdates(request, callback, context.mainLooper)

            awaitClose {
                fusedLocation.removeLocationUpdates(callback)
                sensorManager.unregisterListener(this@LocationModule)
            }
        }

    // ── SensorEventListener ───────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val mag = sqrt(
                event.values[0].pow(2) +
                event.values[1].pow(2) +
                event.values[2].pow(2)
            )
            if (accelBuffer.size >= 50) accelBuffer.removeAt(0)
            accelBuffer.add(mag)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── Wi-Fi scan ────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun scanWifiConfidence(): Float {
        if (!hasWifiPermission()) return -1f
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return -1f
        @Suppress("DEPRECATION")
        val results = wm.scanResults ?: return 0f
        val matched = results.count { r ->
            knownTransitSsids.any { r.SSID?.contains(it, ignoreCase = true) == true }
        }
        return if (matched > 0) 0.8f else 0f
    }

    // ── Route alignment ───────────────────────────────────────────────────────

    fun computeRouteAlignment(userPos: LatLng, polyline: RoutePolyline): Float {
        if (polyline.points.isEmpty()) return 0f
        var minDist = Double.MAX_VALUE
        for (i in 0 until polyline.points.size - 1) {
            val d = pointToSegmentDist(userPos, polyline.points[i], polyline.points[i + 1])
            if (d < minDist) minDist = d
        }
        return (1.0 - (minDist / 50.0)).coerceIn(0.0, 1.0).toFloat()
    }

    fun haversineMeters(a: LatLng, b: LatLng): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) *
                sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    // ── Motion classification ─────────────────────────────────────────────────

    fun currentMotionScore(): Float {
        if (accelBuffer.size < 5) return 0f
        val samples = accelBuffer.takeLast(20)
        val rms = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size).toFloat()
        val peak = samples.max()
        return when {
            peak > 3.0f -> 0.1f          // walking cadence
            rms in 0.8f..4.0f -> 0.85f  // vehicle vibration range
            else -> 0.3f
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun pointToSegmentDist(p: LatLng, a: LatLng, b: LatLng): Double {
        val abLat = b.lat - a.lat; val abLng = b.lng - a.lng
        val apLat = p.lat - a.lat; val apLng = p.lng - a.lng
        val abDot = abLat * abLat + abLng * abLng
        if (abDot == 0.0) return haversineMeters(p, a)
        val t = ((apLat * abLat + apLng * abLng) / abDot).coerceIn(0.0, 1.0)
        return haversineMeters(p, LatLng(a.lat + t * abLat, a.lng + t * abLng))
    }

    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasWifiPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.NEARBY_WIFI_DEVICES
        else Manifest.permission.ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
}
