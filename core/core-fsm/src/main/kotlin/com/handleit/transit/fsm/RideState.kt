package com.handleit.transit.fsm

import com.handleit.transit.model.*

// ─── States ───────────────────────────────────────────────────────────────────

sealed class RideState {
    object Idle : RideState()

    data class WaitingAtStop(
        val stop: Stop,
        val route: Route,
        val arrivals: List<BusArrival> = emptyList(),
    ) : RideState()

    data class BusApproaching(
        val stop: Stop,
        val route: Route,
        val arrival: BusArrival,
        val secsToArrival: Long,
    ) : RideState()

    data class BoardingWindow(
        val stop: Stop,
        val route: Route,
        val arrival: BusArrival,
        val secsToArrival: Long,
        val level: AlertLevel,
    ) : RideState()

    data class OnBus(
        val trip: TripCandidate,
        val fusionResult: FusionResult,
        val boardedAtMs: Long = System.currentTimeMillis(),
    ) : RideState()

    data class ApproachingExit(
        val trip: TripCandidate,
        val stopsRemaining: Int,
        val destinationStop: Stop,
    ) : RideState()

    data class ExitWindow(
        val trip: TripCandidate,
        val destinationStop: Stop,
        val secsToArrival: Long?,
        val level: AlertLevel,
    ) : RideState()

    data class TripComplete(
        val routeName: String,
        val exitedStop: Stop?,
        val durationMs: Long,
    ) : RideState()
}

enum class AlertLevel { PASSIVE, ACTIVE, STRONG, CRITICAL }

// ─── Events ───────────────────────────────────────────────────────────────────

sealed class RideEvent {
    // Location
    data class EnteredStopGeofence(val stop: Stop) : RideEvent()
    data class ExitedStopGeofence(val stop: Stop) : RideEvent()
    data class LocationUpdated(val snapshot: LocationSnapshot) : RideEvent()

    // GTFS-RT
    data class ArrivalsUpdated(val arrivals: List<BusArrival>) : RideEvent()
    data class EtaThresholdCrossed(
        val arrival: BusArrival,
        val secsToArrival: Long,
        val threshold: EtaThreshold,
    ) : RideEvent()

    // Fusion
    data class FusionUpdated(val result: FusionResult) : RideEvent()
    data class TripMatchUpdated(val candidate: TripCandidate?) : RideEvent()

    // On-bus progress
    data class StopAdvanced(val newNextStop: Stop, val stopsRemaining: Int) : RideEvent()
    data class ApproachingDestination(val stopsRemaining: Int, val destination: Stop) : RideEvent()
    data class ArrivedAtDestination(val stop: Stop) : RideEvent()

    // User actions
    data class RouteSelected(val route: Route, val stop: Stop, val destination: Stop?) : RideEvent()
    object BoardingConfirmed : RideEvent()
    object ExitConfirmed : RideEvent()
    object TripDismissed : RideEvent()
    object Reset : RideEvent()
}

enum class EtaThreshold(val secs: Long) {
    T_5MIN(300L),
    T_2MIN(120L),
    T_90SEC(90L),
    T_30SEC(30L),
}

// ─── Transition log ───────────────────────────────────────────────────────────

data class TransitionLog(
    val fromState: String,
    val toState: String,
    val trigger: String,
    val confidence: Float?,
    val timestampMs: Long = System.currentTimeMillis(),
)
