package com.handleit.transit.data.gtfsrt

import com.handleit.transit.common.TransitConfig
import com.handleit.transit.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _tripUpdates = MutableStateFlow<List<TripUpdate>>(emptyList())
    val tripUpdates: StateFlow<List<TripUpdate>> = _tripUpdates.asStateFlow()

    private val _feedStatus = MutableStateFlow(FeedStatus.IDLE)
    val feedStatus: StateFlow<FeedStatus> = _feedStatus.asStateFlow()

    private var pollingJob: Job? = null

    /**
     * BRIDGE FUNCTION: Added to satisfy MapViewModel's direct request for vehicles.
     * This performs a manual fetch of the vehicle positions URL.
     */
    suspend fun fetchVehiclePositions(): List<Vehicle> = withContext(Dispatchers.IO) {
        try {
            val bytes = fetchBytes(TransitConfig.GTFS_RT_VEHICLE_POSITIONS_URL)
            val parsed = GtfsRtParser.parseVehiclePositions(bytes)
            _vehicles.value = parsed
            parsed
        } catch (e: Exception) {
            Timber.e(e, "Manual vehicle position fetch failed")
            throw e
        }
    }

    fun startPolling(intervalMs: Long = TransitConfig.GTFS_RT_POLL_INTERVAL_MS) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            _feedStatus.value = FeedStatus.CONNECTING
            while (isActive) {
                fetchAll()
                delay(intervalMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _feedStatus.value = FeedStatus.IDLE
    }

    private suspend fun fetchAll() {
        val vpResult = runCatching { fetchBytes(TransitConfig.GTFS_RT_VEHICLE_POSITIONS_URL) }
        val tuResult = runCatching { fetchBytes(TransitConfig.GTFS_RT_TRIP_UPDATES_URL) }

        vpResult.getOrNull()?.let { bytes ->
            val parsed = GtfsRtParser.parseVehiclePositions(bytes)
            _vehicles.value = parsed
            Timber.v("GTFS-RT: ${parsed.size} vehicle positions")
        } ?: run {
            Timber.w("GTFS-RT: Vehicle positions fetch failed: ${vpResult.exceptionOrNull()?.message}")
            _feedStatus.value = FeedStatus.ERROR
            return
        }

        tuResult.getOrNull()?.let { bytes ->
            val parsed = GtfsRtParser.parseTripUpdates(bytes)
            _tripUpdates.value = parsed
            Timber.v("GTFS-RT: ${parsed.size} trip updates")
        } ?: run {
            Timber.w("GTFS-RT: Trip updates fetch failed: ${tuResult.exceptionOrNull()?.message}")
        }

        _feedStatus.value = FeedStatus.LIVE
    }

    private fun fetchBytes(url: String): ByteArray {
        val request = Request.Builder().url(url).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code} from $url")
            response.body?.bytes() ?: error("Empty body from $url")
        }
    }

    fun arrivalsForStop(
        stopId: String,
        routeId: String?,
        nowSecs: Long = System.currentTimeMillis() / 1000,
    ): List<BusArrival> {
        return tripUpdates.value.mapNotNull { update ->
            if (routeId != null && update.routeId != routeId) return@mapNotNull null
            val stu = update.stopTimeUpdates.firstOrNull { it.stopId == stopId }
                ?: return@mapNotNull null
            val arrivalSecs = stu.arrivalEpochSecs ?: return@mapNotNull null
            val secsAway = arrivalSecs - nowSecs
            if (secsAway < -60) return@mapNotNull null

            BusArrival(
                routeId = update.routeId ?: "",
                routeShortName = update.routeId ?: "",
                tripId = update.tripId,
                vehicleId = update.vehicleId,
                headsign = "",
                secsToArrival = secsAway,
                isRealtime = true,
            )
        }.sortedBy { it.secsToArrival }
    }
}
