package com.handleit.transit.fsm

import com.handleit.transit.common.TransitConfig
import com.handleit.transit.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RideFsmEngine
 *
 * Pure Kotlin state machine. No Android dependencies, no Hilt.
 * All transitions use block bodies (not expression bodies) to avoid
 * Kotlin's "return not allowed in expression body" restriction.
 *
 * Usage:
 *   val engine = RideFsmEngine()
 *   engine.process(RideEvent.EnteredStopGeofence(stop))
 *   engine.state.collect { state -> ... }
 */
class RideFsmEngine(
    private val onTransition: ((TransitionLog) -> Unit)? = null,
) {
    private val _state = MutableStateFlow<RideState>(RideState.Idle)
    val state: StateFlow<RideState> = _state.asStateFlow()

    val current: RideState get() = _state.value

    fun process(event: RideEvent) {
        val from = current
        val to = transition(from, event) ?: return
        if (to == from) return

        onTransition?.invoke(
            TransitionLog(
                fromState = from::class.simpleName ?: "?",
                toState = to::class.simpleName ?: "?",
                trigger = event::class.simpleName ?: "?",
                confidence = (event as? RideEvent.FusionUpdated)?.result?.onBusConfidence,
            )
        )
        _state.value = to
    }

    fun reset() { _state.value = RideState.Idle }

    // ── Transition table ──────────────────────────────────────────────────────
    // Block body function — labeled returns are valid here.

    private fun transition(state: RideState, event: RideEvent): RideState? {
        return when (state) {

            // ── IDLE ──────────────────────────────────────────────────────────
            is RideState.Idle -> when (event) {
                is RideEvent.RouteSelected -> RideState.WaitingAtStop(
                    stop = event.stop,
                    route = event.route,
                    tripId = event.tripId,
                )
                is RideEvent.Reset -> RideState.Idle
                else -> null
            }

            // ── WAITING_AT_STOP ───────────────────────────────────────────────
            is RideState.WaitingAtStop -> when (event) {
                is RideEvent.ArrivalsUpdated ->
                    state.copy(
                        arrivals = event.arrivals,
                        tripId = state.tripId ?: event.arrivals.firstOrNull()?.tripId
                    )

                is RideEvent.RemainingStopsUpdated ->
                    state.copy(remainingStops = event.stops)

                is RideEvent.EtaThresholdCrossed -> when (event.threshold) {
                    EtaThreshold.T_5MIN, EtaThreshold.T_2MIN ->
                        RideState.BusApproaching(
                            stop = state.stop,
                            route = state.route,
                            arrival = event.arrival,
                            secsToArrival = event.secsToArrival,
                        )
                    EtaThreshold.T_90SEC, EtaThreshold.T_30SEC ->
                        RideState.BoardingWindow(
                            stop = state.stop,
                            route = state.route,
                            arrival = event.arrival,
                            secsToArrival = event.secsToArrival,
                            level = if (event.threshold == EtaThreshold.T_30SEC)
                                AlertLevel.CRITICAL else AlertLevel.STRONG,
                        )
                }

                is RideEvent.ExitedStopGeofence -> RideState.Idle
                is RideEvent.Reset -> RideState.Idle
                else -> null
            }

            // ── BUS_APPROACHING ───────────────────────────────────────────────
            is RideState.BusApproaching -> when (event) {
                is RideEvent.EtaThresholdCrossed -> when (event.threshold) {
                    EtaThreshold.T_90SEC, EtaThreshold.T_30SEC ->
                        RideState.BoardingWindow(
                            stop = state.stop,
                            route = state.route,
                            arrival = state.arrival,
                            secsToArrival = event.secsToArrival,
                            level = if (event.threshold == EtaThreshold.T_30SEC)
                                AlertLevel.CRITICAL else AlertLevel.STRONG,
                        )
                    else -> state.copy(secsToArrival = event.secsToArrival)
                }

                is RideEvent.TripMatchUpdated -> {
                    val candidate = event.candidate ?: return null
                    if (candidate.gtfsConfidence >= TransitConfig.ON_BUS_CONFIDENCE_THRESHOLD) {
                        buildOnBusState(candidate)
                    } else null
                }

                is RideEvent.ArrivalsUpdated -> {
                    val updated = event.arrivals.firstOrNull { it.tripId == state.arrival.tripId }
                    if (updated != null) state.copy(secsToArrival = updated.secsToArrival) else null
                }

                is RideEvent.ExitedStopGeofence -> RideState.Idle
                is RideEvent.Reset -> RideState.Idle
                else -> null
            }

            // ── BOARDING_WINDOW ───────────────────────────────────────────────
            is RideState.BoardingWindow -> when (event) {
                is RideEvent.TripMatchUpdated -> {
                    val candidate = event.candidate ?: return null
                    if (candidate.gtfsConfidence >= TransitConfig.ON_BUS_CONFIDENCE_THRESHOLD ||
                        candidate.routeAlignmentScore >= 0.80f
                    ) {
                        buildOnBusState(candidate)
                    } else null
                }

                is RideEvent.FusionUpdated -> {
                    if (event.result.meetsThreshold) null // wait for TripMatchUpdated
                    else null
                }

                is RideEvent.BoardingConfirmed -> {
                    // User manually confirmed boarding — accept at full confidence
                    null // Orchestrator handles building candidate from context
                }

                is RideEvent.EtaThresholdCrossed ->
                    state.copy(
                        secsToArrival = event.secsToArrival,
                        level = if (event.threshold == EtaThreshold.T_30SEC)
                            AlertLevel.CRITICAL else state.level,
                    )

                is RideEvent.ArrivalsUpdated -> {
                    // Bus departed without boarding
                    val stillComing = event.arrivals.any {
                        it.tripId == state.arrival.tripId && it.secsToArrival > -60
                    }
                    if (!stillComing) {
                        RideState.WaitingAtStop(
                            stop = state.stop,
                            route = state.route,
                            tripId = state.arrival.tripId,
                            arrivals = event.arrivals,
                        )
                    } else null
                }

                is RideEvent.Reset -> RideState.Idle
                else -> null
            }

            // ── ON_BUS ────────────────────────────────────────────────────────
            is RideState.OnBus -> when (event) {
                is RideEvent.TripMatchUpdated ->
                    state.copy(trip = event.candidate ?: state.trip)

                is RideEvent.StopAdvanced ->
                    state.copy(trip = state.trip.copy(nextStop = event.newNextStop))

                is RideEvent.ApproachingDestination -> {
                    val dest = state.trip.destinationStop ?: return null
                    RideState.ApproachingExit(
                        trip = state.trip,
                        stopsRemaining = event.stopsRemaining,
                        destinationStop = dest,
                    )
                }

                is RideEvent.ExitConfirmed,
                is RideEvent.ArrivedAtDestination ->
                    buildTripComplete(state.trip, state.boardedAtMs)

                is RideEvent.Reset -> RideState.Idle
                else -> null
            }

            // ── APPROACHING_EXIT ──────────────────────────────────────────────
            is RideState.ApproachingExit -> when (event) {
                is RideEvent.StopAdvanced ->
                    state.copy(stopsRemaining = state.stopsRemaining - 1)

                is RideEvent.ApproachingDestination -> {
                    if (event.stopsRemaining <= TransitConfig.EXIT_ALERT_STOPS_BEFORE) {
                        RideState.ExitWindow(
                            trip = state.trip,
                            destinationStop = state.destinationStop,
                            secsToArrival = null,
                            level = AlertLevel.STRONG,
                        )
                    } else state.copy(stopsRemaining = event.stopsRemaining)
                }

                is RideEvent.ExitConfirmed,
                is RideEvent.ArrivedAtDestination ->
                    buildTripComplete(state.trip, null)

                is RideEvent.Reset -> RideState.Idle
                else -> null
            }

            // ── EXIT_WINDOW ───────────────────────────────────────────────────
            is RideState.ExitWindow -> when (event) {
                is RideEvent.ExitConfirmed,
                is RideEvent.ArrivedAtDestination ->
                    buildTripComplete(state.trip, null)

                is RideEvent.EtaThresholdCrossed ->
                    state.copy(secsToArrival = event.secsToArrival, level = AlertLevel.CRITICAL)

                is RideEvent.Reset -> RideState.Idle
                else -> null
            }

            // ── TRIP_COMPLETE ─────────────────────────────────────────────────
            is RideState.TripComplete -> when (event) {
                is RideEvent.TripDismissed,
                is RideEvent.Reset -> RideState.Idle
                else -> null
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildOnBusState(candidate: TripCandidate): RideState.OnBus =
        RideState.OnBus(
            trip = candidate,
            fusionResult = FusionResult(
                onBusConfidence = candidate.gtfsConfidence,
                dominantSignal = "gtfs_trip_match",
                breakdown = mapOf("gtfs_trip" to candidate.gtfsConfidence),
                meetsThreshold = true,
            ),
        )

    private fun buildTripComplete(trip: TripCandidate, boardedAtMs: Long?): RideState.TripComplete =
        RideState.TripComplete(
            routeName = trip.route.routeShortName,
            exitedStop = trip.destinationStop,
            durationMs = if (boardedAtMs != null) System.currentTimeMillis() - boardedAtMs else 0L,
        )
}
