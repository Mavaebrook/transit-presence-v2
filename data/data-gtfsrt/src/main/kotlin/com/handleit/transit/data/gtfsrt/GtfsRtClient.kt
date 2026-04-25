package com.handleit.transit.data.gtfsrt

import com.handleit.transit.common.TransitConfig
import com.handleit.transit.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class GtfsRtClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val transitDb: com.handleit.transit.data.gtfs.TransitDb,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    private val _vehicles = MutableStateFlow<List<VehiclePosition>>(emptyList())
    val vehicles: StateFlow<List<VehiclePosition>> = _vehicles.asStateFlow()

    private val _feedStatus = MutableStateFlow(FeedStatus.IDLE)
    val feedStatus: StateFlow<FeedStatus> = _feedStatus.asStateFlow()

    private val stopCoordsCache = mutableMapOf<String, LatLng>()

    fun startPolling(intervalMs: Long = TransitConfig.GTFS_RT_POLL_INTERVAL_MS) {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    _feedStatus.value = FeedStatus.CONNECTING
                    val fetched = fetchVehiclePositions(TransitConfig.GTFS_RT_VEHICLE_POSITIONS_URL)
                    _vehicles.value = fetched
                    _feedStatus.value = FeedStatus.LIVE
                } catch (e: Exception) {
                    Timber.e(e, "GtfsRtClient poll failed: ${e.message}")
                    _feedStatus.value = FeedStatus.ERROR
                }
                delay(intervalMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _feedStatus.value = FeedStatus.IDLE
    }

    fun arrivalsForStop(stopId: String, routeId: String): List<BusArrival> {
        val currentVehicles = _vehicles.value
        val cleanStopId = stopId.trim()
        val cleanRouteId = routeId.trim()

        return currentVehicles.asSequence()
            .filter { 
                val vRoute = it.routeId.trim()
                vRoute == cleanRouteId || vRoute == "LINK $cleanRouteId" || vRoute == cleanRouteId.removePrefix("LINK ")
            }
            .mapNotNull { v ->
                val secsToArrival = runBlocking { estimateSecsToArrival(v, cleanStopId) } ?: return@mapNotNull null
                BusArrival(
                    routeId = v.routeId,
                    routeShortName = v.routeId,
                    tripId = v.tripId ?: "",
                    vehicleId = v.vehicleId,
                    headsign = "",
                    secsToArrival = secsToArrival,
                    isRealtime = true,
                )
            }
            .sortedBy { it.secsToArrival }
            .toList()
    }

    private suspend fun estimateSecsToArrival(vehicle: VehiclePosition, stopId: String): Long? {
        val stopPos = stopCoordsCache[stopId] ?: transitDb.getStopById(stopId)?.let {
            val pos = LatLng(it.lat, it.lng)
            stopCoordsCache[stopId] = pos
            pos
        } ?: return null

        val dist = haversine(vehicle.lat, vehicle.lng, stopPos.lat, stopPos.lng)
        
        // Very basic estimate: distance / average speed (10 m/s ~ 22 mph)
        // Plus some overhead for stops
        if (dist > 10000) return null // Too far
        
        val travelSecs = (dist / 10.0).toLong()
        val ageSecs = (System.currentTimeMillis() / 1000) - vehicle.timestamp
        
        return (travelSecs - ageSecs).coerceAtLeast(0)
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    suspend fun fetchVehiclePositions(feedUrl: String): List<VehiclePosition> =
        withContext(Dispatchers.IO) {
            fetchBytes(feedUrl)?.let { GtfsRtParser.parseVehiclePositions(it) } ?: emptyList()
        }

    suspend fun fetchTripUpdates(feedUrl: String): List<TripUpdate> =
        withContext(Dispatchers.IO) {
            fetchBytes(feedUrl)?.let { GtfsRtParser.parseTripUpdates(it) } ?: emptyList()
        }

    private fun fetchBytes(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("GtfsRtClient: HTTP ${response.code} for $url")
                    return null
                }
                response.body?.bytes()
            }
        } catch (e: Exception) {
            Timber.e(e, "GtfsRtClient: fetchBytes failed for $url")
            null
        }
    }
}
