package com.handleit.transit.data.gtfsrt

import com.handleit.transit.common.TransitConfig
import com.handleit.transit.model.BusArrival
import com.handleit.transit.model.FeedStatus
import com.handleit.transit.model.TripUpdate
import com.handleit.transit.model.VehiclePosition
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GtfsRtClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null

    private val _vehicles = MutableStateFlow<List<VehiclePosition>>(emptyList())
    val vehicles: StateFlow<List<VehiclePosition>> = _vehicles.asStateFlow()

    private val _feedStatus = MutableStateFlow(FeedStatus.IDLE)
    val feedStatus: StateFlow<FeedStatus> = _feedStatus.asStateFlow()

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
        val vehicles = _vehicles.value
        Timber.d("arrivalsForStop: checking $routeId at $stopId. Found ${vehicles.size} vehicles total.")
        
        return vehicles.asSequence()
            .filter { 
                val match = it.routeId == routeId || it.routeId == "LINK $routeId"
                if (it.routeId.contains(routeId)) {
                    Timber.v("arrivalsForStop: Partial match? vehicleRoute=${it.routeId} target=$routeId")
                }
                match
            }
            .mapNotNull { v ->
                val secsToArrival = estimateSecsToArrival(v, stopId) ?: return@mapNotNull null
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

    private fun estimateSecsToArrival(vehicle: VehiclePosition, stopId: String): Long? {
        // Placeholder — real implementation would cross-reference TripUpdates
        // For now returns a synthetic estimate based on timestamp staleness
        val ageSeconds = (System.currentTimeMillis() / 1000) - vehicle.timestamp
        return if (ageSeconds < 300) (60 - ageSeconds).coerceAtLeast(0) else null
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
