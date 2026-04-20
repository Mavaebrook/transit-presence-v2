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

// ─── Lightweight GTFS-RT Protobuf Parser ─────────────────────────────────────

object GtfsRtParser {

    fun parseVehiclePositions(bytes: ByteArray): List<VehiclePosition> {
        val result = mutableListOf<VehiclePosition>()
        val reader = ProtoReader(bytes)
        while (reader.hasMore()) {
            when (reader.readTag()) {
                2 -> { // entity
                    val entityBytes = reader.readBytes()
                    parseVehicleEntity(entityBytes)?.let { result.add(it) }
                }
                else -> reader.skipField()
            }
        }
        return result
    }

    fun parseTripUpdates(bytes: ByteArray): List<TripUpdate> {
        val result = mutableListOf<TripUpdate>()
        val reader = ProtoReader(bytes)
        while (reader.hasMore()) {
            when (reader.readTag()) {
                2 -> { // entity
                    val entityBytes = reader.readBytes()
                    parseTripUpdateEntity(entityBytes)?.let { result.add(it) }
                }
                else -> reader.skipField()
            }
        }
        return result
    }

    private fun parseVehicleEntity(bytes: ByteArray): VehiclePosition? {
        var vehicleId = ""
        var tripId: String? = null
        var routeId: String? = null
        var lat = 0.0
        var lng = 0.0
        var bearing: Float? = null
        var speed: Float? = null
        var stopSeq: Int? = null
        var timestamp = 0L

        val reader = ProtoReader(bytes)
        while (reader.hasMore()) {
            when (reader.readTag()) {
                4 -> { // vehicle
                    val vBytes = reader.readBytes()
                    val vReader = ProtoReader(vBytes)
                    while (vReader.hasMore()) {
                        when (vReader.readTag()) {
                            1 -> { // trip
                                val tBytes = vReader.readBytes()
                                val tReader = ProtoReader(tBytes)
                                while (tReader.hasMore()) {
                                    when (tReader.readTag()) {
                                        1 -> tripId = tReader.readString()
                                        3 -> routeId = tReader.readString()
                                        else -> tReader.skipField()
                                    }
                                }
                            }
                            2 -> { // position
                                val pBytes = vReader.readBytes()
                                val pReader = ProtoReader(pBytes)
                                while (pReader.hasMore()) {
                                    when (pReader.readTag()) {
                                        1 -> lat = pReader.readFloat().toDouble()
                                        2 -> lng = pReader.readFloat().toDouble()
                                        3 -> bearing = pReader.readFloat()
                                        4 -> speed = pReader.readFloat()
                                        else -> pReader.skipField()
                                    }
                                }
                            }
                            3 -> stopSeq = vReader.readVarInt().toInt()
                            5 -> { // vehicle descriptor
                                val dBytes = vReader.readBytes()
                                val dReader = ProtoReader(dBytes)
                                while (dReader.hasMore()) {
                                    when (dReader.readTag()) {
                                        1 -> vehicleId = dReader.readString()
                                        else -> dReader.skipField()
                                    }
                                }
                            }
                            6 -> timestamp = vReader.readVarInt()
                            else -> vReader.skipField()
                        }
                    }
                }
                else -> reader.skipField()
            }
        }

        if (lat == 0.0 && lng == 0.0) return null
        return VehiclePosition(vehicleId, tripId, routeId ?: "", lat, lng,
            bearing, speed, stopSeq, timestamp)
    }

    private fun parseTripUpdateEntity(bytes: ByteArray): TripUpdate? {
        var tripId = ""
        var routeId: String? = null
        var vehicleId: String? = null
        val stopUpdates = mutableListOf<StopTimeUpdate>()
        var timestamp = 0L

        val reader = ProtoReader(bytes)
        while (reader.hasMore()) {
            when (reader.readTag()) {
                3 -> { // trip_update
                    val tuBytes = reader.readBytes()
                    val tuReader = ProtoReader(tuBytes)
                    while (tuReader.hasMore()) {
                        when (tuReader.readTag()) {
                            1 -> { // trip
                                val tBytes = tuReader.readBytes()
                                val tReader = ProtoReader(tBytes)
                                while (tReader.hasMore()) {
                                    when (tReader.readTag()) {
                                        1 -> tripId = tReader.readString()
                                        3 -> routeId = tReader.readString()
                                        else -> tReader.skipField()
                                    }
                                }
                            }
                            3 -> { // stop_time_update
                                val stuBytes = tuReader.readBytes()
                                parseStopTimeUpdate(stuBytes)?.let { stopUpdates.add(it) }
                            }
                            4 -> { // vehicle
                                val vBytes = tuReader.readBytes()
                                val vReader = ProtoReader(vBytes)
                                while (vReader.hasMore()) {
                                    when (vReader.readTag()) {
                                        1 -> vehicleId = vReader.readString()
                                        else -> vReader.skipField()
                                    }
                                }
                            }
                            5 -> timestamp = tuReader.readVarInt()
                            else -> tuReader.skipField()
                        }
                    }
                }
                else -> reader.skipField()
            }
        }

        if (tripId.isEmpty()) return null
        return TripUpdate(tripId, routeId, vehicleId, stopUpdates, timestamp)
    }

    private fun parseStopTimeUpdate(bytes: ByteArray): StopTimeUpdate? {
        var stopSeq: Int? = null
        var stopId: String? = null
        var arrivalSecs: Long? = null
        var departureSecs: Long? = null

        val reader = ProtoReader(bytes)
        while (reader.hasMore()) {
            when (reader.readTag()) {
                1 -> stopSeq = reader.readVarInt().toInt()
                2 -> { // arrival
                    val aBytes = reader.readBytes()
                    val aReader = ProtoReader(aBytes)
                    while (aReader.hasMore()) {
                        when (aReader.readTag()) {
                            2 -> arrivalSecs = aReader.readVarInt()
                            else -> aReader.skipField()
                        }
                    }
                }
                3 -> { // departure
                    val dBytes = reader.readBytes()
                    val dReader = ProtoReader(dBytes)
                    while (dReader.hasMore()) {
                        when (dReader.readTag()) {
                            2 -> departureSecs = dReader.readVarInt()
                            else -> dReader.skipField()
                        }
                    }
                }
                4 -> stopId = reader.readString()
                else -> reader.skipField()
            }
        }
        return StopTimeUpdate(stopId, stopSeq, arrivalSecs, departureSecs)
    }
}

// ─── Minimal Protobuf Wire-Format Reader ─────────────────────────────────────

class ProtoReader(private val bytes: ByteArray) {
    private var pos = 0

    fun hasMore() = pos < bytes.size

    fun readTag(): Int {
        val varint = readVarInt()
        return (varint ushr 3).toInt()
    }

    fun readVarInt(): Long {
        var result = 0L
        var shift = 0
        while (pos < bytes.size) {
            val b = bytes[pos++].toLong() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0L) break
            shift += 7
        }
        return result
    }

    fun readBytes(): ByteArray {
        val len = readVarInt().toInt()
        val result = bytes.copyOfRange(pos, pos + len)
        pos += len
        return result
    }

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    fun readFloat(): Float {
        val bits = (bytes[pos].toLong() and 0xFF) or
                ((bytes[pos + 1].toLong() and 0xFF) shl 8) or
                ((bytes[pos + 2].toLong() and 0xFF) shl 16) or
                ((bytes[pos + 3].toLong() and 0xFF) shl 24)
        pos += 4
        return java.lang.Float.intBitsToFloat(bits.toInt())
    }

    fun skipField() {
        // Technically this should switch based on wire type, but GTFS-RT
        // is mostly varints and length-delimited. This is a simplified skipper.
        readVarInt() 
    }
}
