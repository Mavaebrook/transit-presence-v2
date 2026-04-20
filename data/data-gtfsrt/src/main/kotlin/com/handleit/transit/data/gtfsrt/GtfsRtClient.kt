package com.handleit.transit.data.gtfsrt

import com.handleit.transit.core.model.StopTimeUpdate
import com.handleit.transit.core.model.TripUpdate
import com.handleit.transit.core.model.VehiclePosition
import timber.log.Timber

object GtfsRtParser {

    fun parseVehiclePositions(bytes: ByteArray): List<VehiclePosition> {
        val result = mutableListOf<VehiclePosition>()
        try {
            val reader = ProtoReader(bytes)
            while (reader.hasMore()) {
                when (reader.readTag()) {
                    2 -> {
                        val entityBytes = reader.readBytes()
                        parseVehicleEntity(entityBytes)?.let { result.add(it) }
                    }
                    else -> reader.skipField()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "parseVehiclePositions failed: ${e.message}")
        }
        return result
    }

    fun parseTripUpdates(bytes: ByteArray): List<TripUpdate> {
        val result = mutableListOf<TripUpdate>()
        try {
            val reader = ProtoReader(bytes)
            while (reader.hasMore()) {
                when (reader.readTag()) {
                    2 -> {
                        val entityBytes = reader.readBytes()
                        parseTripUpdateEntity(entityBytes)?.let { result.add(it) }
                    }
                    else -> reader.skipField()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "parseTripUpdates failed: ${e.message}")
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

        try {
            val reader = ProtoReader(bytes)
            while (reader.hasMore()) {
                when (reader.readTag()) {
                    4 -> {
                        val vBytes = reader.readBytes()
                        val vReader = ProtoReader(vBytes)
                        while (vReader.hasMore()) {
                            when (vReader.readTag()) {
                                1 -> {
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
                                2 -> {
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
                                5 -> {
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
        } catch (e: Exception) {
            Timber.e(e, "parseVehicleEntity failed: ${e.message}")
            return null
        }

        if (lat == 0.0 && lng == 0.0) return null
        return VehiclePosition(
            vehicleId = vehicleId,
            tripId = tripId,
            routeId = routeId,
            lat = lat,
            lng = lng,
            bearing = bearing,
            speedMps = speed,
            currentStopSequence = stopSeq,
            timestamp = timestamp,
        )
    }

    private fun parseTripUpdateEntity(bytes: ByteArray): TripUpdate? {
        var tripId = ""
        var routeId: String? = null
        var vehicleId: String? = null
        val stopUpdates = mutableListOf<StopTimeUpdate>()
        var timestamp = 0L

        try {
            val reader = ProtoReader(bytes)
            while (reader.hasMore()) {
                when (reader.readTag()) {
                    3 -> {
                        val tuBytes = reader.readBytes()
                        val tuReader = ProtoReader(tuBytes)
                        while (tuReader.hasMore()) {
                            when (tuReader.readTag()) {
                                1 -> {
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
                                3 -> {
                                    val stuBytes = tuReader.readBytes()
                                    parseStopTimeUpdate(stuBytes)?.let { stopUpdates.add(it) }
                                }
                                4 -> {
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
        } catch (e: Exception) {
            Timber.e(e, "parseTripUpdateEntity failed: ${e.message}")
            return null
        }

        if (tripId.isEmpty()) return null
        return TripUpdate(
            tripId = tripId,
            routeId = routeId,
            vehicleId = vehicleId,
            stopTimeUpdates = stopUpdates,
            timestamp = timestamp,
        )
    }

    private fun parseStopTimeUpdate(bytes: ByteArray): StopTimeUpdate? {
        var stopSeq: Int? = null
        var stopId: String? = null
        var arrivalSecs: Long? = null
        var departureSecs: Long? = null

        try {
            val reader = ProtoReader(bytes)
            while (reader.hasMore()) {
                when (reader.readTag()) {
                    1 -> stopSeq = reader.readVarInt().toInt()
                    2 -> {
                        val aBytes = reader.readBytes()
                        val aReader = ProtoReader(aBytes)
                        while (aReader.hasMore()) {
                            when (aReader.readTag()) {
                                2 -> arrivalSecs = aReader.readVarInt()
                                else -> aReader.skipField()
                            }
                        }
                    }
                    3 -> {
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
        } catch (e: Exception) {
            Timber.e(e, "parseStopTimeUpdate failed: ${e.message}")
            return null
        }

        return StopTimeUpdate(
            stopId = stopId,
            stopSequence = stopSeq,
            arrivalEpochSecs = arrivalSecs,
            departureEpochSecs = departureSecs,
        )
    }
}

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
        if (len <= 0) return ByteArray(0)
        val result = bytes.copyOfRange(pos, minOf(pos + len, bytes.size))
        pos += len
        return result
    }

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    fun readFloat(): Float {
        if (pos + 4 > bytes.size) return 0f
        val bits = (bytes[pos].toLong() and 0xFF) or
                ((bytes[pos + 1].toLong() and 0xFF) shl 8) or
                ((bytes[pos + 2].toLong() and 0xFF) shl 16) or
                ((bytes[pos + 3].toLong() and 0xFF) shl 24)
        pos += 4
        return java.lang.Float.intBitsToFloat(bits.toInt())
    }

    fun skipField() {
        try {
            readVarInt()
        } catch (e: Exception) {
            pos = bytes.size
        }
    }
}
