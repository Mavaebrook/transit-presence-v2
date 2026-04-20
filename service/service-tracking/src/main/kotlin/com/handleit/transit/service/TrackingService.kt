package com.handleit.transit.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.handleit.transit.common.TransitConfig
import com.handleit.transit.data.gtfsrt.GtfsRtClient
import com.handleit.transit.data.location.LocationModule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

/**
 * TrackingService
 *
 * Foreground service that keeps location + GTFS-RT polling alive
 * when the app is backgrounded.
 *
 * CRITICAL FIX from v1: startForeground() is called as the FIRST thing
 * in onStartCommand — before any async work. This prevents the
 * ForegroundServiceDidNotStartInTimeException crash on Android 12+
 * which requires startForeground within 5 seconds.
 */
@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var locationModule: LocationModule
    @Inject lateinit var gtfsRtClient: GtfsRtClient

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollingJob: Job? = null

    companion object {
        const val ACTION_START = "com.handleit.transit.START"
        const val ACTION_STOP  = "com.handleit.transit.STOP"
        const val CHANNEL_ID   = "transit_tracking"
        const val NOTIF_ID     = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // ── CRITICAL: call startForeground IMMEDIATELY ───────────────────────
        // Must happen within 5 seconds of startForegroundService() call.
        // Do this BEFORE starting any coroutines or async work.
        startForeground(NOTIF_ID, buildNotification("Scanning for nearby stops..."))

        // Now safe to start async work
        startTracking()
        return START_STICKY
    }

    private fun startTracking() {
        // GTFS-RT polling loop — runs on interval defined in TransitConfig
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val vehicles = gtfsRtClient.fetchVehiclePositions(
                        TransitConfig.GTFS_RT_VEHICLE_POSITIONS_URL
                    )
                    val trips = gtfsRtClient.fetchTripUpdates(
                        TransitConfig.GTFS_RT_TRIP_UPDATES_URL
                    )
                    Timber.v("GTFS-RT: ${vehicles.size} vehicles, ${trips.size} trip updates")
                } catch (e: Exception) {
                    Timber.e(e, "GTFS-RT poll failed: ${e.message}")
                }
                delay(TransitConfig.GTFS_RT_POLL_INTERVAL_MS)
            }
        }

        // Location updates — keeps stream alive for orchestrator
        scope.launch {
            locationModule.locationFlow().collect { snapshot ->
                Timber.v("Location: ${snapshot.latLng.lat}, ${snapshot.latLng.lng}")
            }
        }

        Timber.i("TrackingService: Started — GTFS-RT polling active")
    }

    fun updateNotification(text: String) {
        if (!hasNotificationPermission()) return
        NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        Timber.i("TrackingService: Destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Transit Presence")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transit Tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Background location and bus tracking" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
