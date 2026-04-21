package com.handleit.transit.model

// ─── Geographic ───────────────────────────────────────────────────────────────

data class LatLng(val lat: Double, val lng: Double)

data class RoutePolyline(val points: List<LatLng>)

// ─── GTFS Static ──────────────────────────────────────────────────────────────

data class Stop(
    val stopId: String,
    val stopName: String,
    val lat: Double,
    val lng: Double,
    val wheelchairBoarding: Int = 0,
)

data class Route(
    val routeId: String,
    val routeShortName: String,
    val routeLongName: String,
    val routeType: Int,
    val routeColor: String = "FFFFFF",
    val routeTextColor: String = "000000",
)
data class UpcomingDeparture(
    val routeId: String,
    val routeShortName: String,
    val routeLongName: String,
    val routeColor: String,
    val routeTextColor: String,
    val headsign: String,
    val directionId: Int,
    val departureTime: String,  // "HH:MM:SS"
    val stopSequence: Int,
    val stopName: String,
)

data class TripStop(
    val stopId: String,
    val stopName: String,
    val lat: Double,
    val lng: Double,
    val arrivalTime: String,    // "HH:MM:SS"
    val departureTime: String,  // "HH:MM:SS"
    val stopSequence: Int,
)
// Extension properties to help MapScreen.kt which expects 'shortName' and 'longName'
val Route.shortName: String get() = routeShortName
val Route.longName: String get() = routeLongName

data class Trip(
    val tripId: String,
    val routeId: String,
    val serviceId: String,
    val tripHeadsign: String = "",
    val directionId: Int = 0,
    val shapeId: String = "",
)

data class StopTime(
    val tripId: String,
    val stopId: String,
    val stopSequence: Int,
    val arrivalTime: String,
    val departureTime: String,
)

data class ShapePoint(
    val shapeId: String,
    val lat: Double,
    val lng: Double,
    val sequence: Int,
)

// ─── GTFS-RT Live ─────────────────────────────────────────────────────────────

/**
 * Maps the live position to a 'Vehicle' name for UI compatibility 
 */
typealias Vehicle = VehiclePosition

data class VehiclePosition(
    val vehicleId: String,
    val tripId: String?,
    val routeId: String, // Made non-nullable to satisfy MapScreen logic
    val lat: Double,
    val lng: Double,
    val bearing: Float?,
    val speedMps: Float?,
    val currentStopSequence: Int?,
    val timestamp: Long,
)

data class TripUpdate(
    val tripId: String,
    val routeId: String?,
    val vehicleId: String?,
    val stopTimeUpdates: List<StopTimeUpdate>,
    val timestamp: Long,
)

data class StopTimeUpdate(
    val stopId: String?,
    val stopSequence: Int?,
    val arrivalEpochSecs: Long?,
    val departureEpochSecs: Long?,
)

// ─── App Domain ───────────────────────────────────────────────────────────────

data class BusArrival(
    val routeId: String,
    val routeShortName: String,
    val tripId: String,
    val vehicleId: String?,
    val headsign: String,
    val secsToArrival: Long,
    val isRealtime: Boolean,
)

data class LocationSnapshot(
    val latLng: LatLng,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearingDeg: Float,
    val timestampMs: Long,
)

data class SignalBundle(
    val location: LocationSnapshot,
    val routeAlignmentScore: Float,   // 0–1
    val gtfsTripConfidence: Float,    // 0–1
    val wifiConfidence: Float,        // 0–1, or -1f if unavailable
    val motionScore: Float,           // 0–1
)

data class FusionResult(
    val onBusConfidence: Float,
    val dominantSignal: String,
    val breakdown: Map<String, Float>,
    val meetsThreshold: Boolean,
)

data class TripCandidate(
    val trip: Trip,
    val route: Route,
    val vehicle: VehiclePosition?,
    val routeAlignmentScore: Float,
    val gtfsConfidence: Float,
    val stopsRemaining: List<StopTime>,
    val nextStop: Stop?,
    val destinationStop: Stop?,
)

enum class FeedStatus { IDLE, CONNECTING, LIVE, ERROR }
