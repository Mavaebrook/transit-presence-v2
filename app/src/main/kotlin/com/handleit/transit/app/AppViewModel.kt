package com.handleit.transit.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handleit.transit.common.MapProvider
import com.handleit.transit.common.TransitConfig
import com.handleit.transit.data.gtfs.TransitDb
import com.handleit.transit.data.gtfsrt.GtfsRtClient
import com.handleit.transit.data.location.LocationModule
import com.handleit.transit.data.location.SensorFusionEngine
import com.handleit.transit.fsm.*
import com.handleit.transit.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AppState(
    val rideState: RideState = RideState.Idle,
    val userLocation: LatLng? = null,
    val nearbyStops: List<Stop> = emptyList(),
    val nearbyVehicles: List<VehiclePosition> = emptyList(),
    val availableRoutes: List<Route> = emptyList(),
    val routesForSelectedStop: List<Route> = emptyList(),
    val upcomingDepartures: List<UpcomingDeparture> = emptyList(),
    val feedStatus: FeedStatus = FeedStatus.IDLE,
    val mapProvider: MapProvider = TransitConfig.MAP_PROVIDER_DEFAULT,
    val permissionsGranted: Boolean = false,
    val transitionLog: List<TransitionLog> = emptyList(),
    val selectedStop: Stop? = null,
)

sealed class AppIntent {
    data class RouteSelected(
        val route: Route,
        val stop: Stop,
        val destination: Stop?,
    ) : AppIntent()
    object ConfirmBoarding : AppIntent()
    object ConfirmExit : AppIntent()
    object DismissTrip : AppIntent()
    object Reset : AppIntent()
    object ToggleMapProvider : AppIntent()
    data class StopSelected(val stop: Stop?) : AppIntent()
    data class PromoteDepartures(val departures: List<UpcomingDeparture>) : AppIntent()
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val locationModule: LocationModule,
    private val fusionEngine: SensorFusionEngine,
    private val gtfsRtClient: GtfsRtClient,
    private val transitDb: TransitDb,
) : ViewModel() {

    private val transitionLogs = mutableListOf<TransitionLog>()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val fsmEngine = RideFsmEngine(onTransition = { log ->
        transitionLogs.add(log)
        if (transitionLogs.size > 100) transitionLogs.removeAt(0)
        _state.update { it.copy(transitionLog = transitionLogs.toList()) }
    })

    init {
        observeFsmState()
        observeVehicles()
        observeFeedStatus()
        gtfsRtClient.startPolling()
    }

    fun onPermissionsResult(granted: Boolean) {
        _state.update { it.copy(permissionsGranted = granted) }
        if (granted) {
            observeLocation()
            loadRoutes()
        }
    }

    fun dispatch(intent: AppIntent) {
        when (intent) {
            is AppIntent.RouteSelected ->
                fsmEngine.process(
                    RideEvent.RouteSelected(intent.route, intent.stop, intent.destination)
                )
            is AppIntent.ConfirmBoarding ->
                fsmEngine.process(RideEvent.BoardingConfirmed)
            is AppIntent.ConfirmExit ->
                fsmEngine.process(RideEvent.ExitConfirmed)
            is AppIntent.DismissTrip ->
                fsmEngine.process(RideEvent.TripDismissed)
            is AppIntent.Reset ->
                fsmEngine.process(RideEvent.Reset)
            is AppIntent.ToggleMapProvider ->
                _state.update {
                    it.copy(
                        mapProvider = if (it.mapProvider == MapProvider.GOOGLE)
                            MapProvider.OSM else MapProvider.GOOGLE
                    )
                }
            is AppIntent.StopSelected -> {
                _state.update { it.copy(selectedStop = intent.stop) }
                intent.stop?.let { stop -> loadRoutesForStop(stop) }
                    ?: _state.update { it.copy(routesForSelectedStop = emptyList()) }
            }
            is AppIntent.PromoteDepartures -> {
                // Diagnostic lab pushes results directly into upcomingDepartures
                _state.update { it.copy(upcomingDepartures = intent.departures) }
            }
        }
    }

    private fun observeFsmState() {
        fsmEngine.state
            .onEach { rideState -> _state.update { it.copy(rideState = rideState) } }
            .launchIn(viewModelScope)
    }

    private fun observeLocation() {
        viewModelScope.launch {
            try {
                locationModule.locationFlow().collect { snapshot ->
                    _state.update { it.copy(userLocation = snapshot.latLng) }
                    fsmEngine.process(RideEvent.LocationUpdated(snapshot))
                    refreshNearbyStops(snapshot.latLng)
                    runFusionIfNeeded(snapshot)
                    checkEtaThresholds()
                }
            } catch (e: Exception) {
                Timber.e(e, "Location flow error: ${e.message}")
            }
        }
    }

    private fun observeVehicles() {
        viewModelScope.launch {
            try {
                gtfsRtClient.vehicles.collect { vehicles ->
                    _state.update { it.copy(nearbyVehicles = vehicles) }
                    updateArrivals()
                }
            } catch (e: Exception) {
                Timber.e(e, "Vehicles flow error: ${e.message}")
            }
        }
    }

    private fun observeFeedStatus() {
        gtfsRtClient.feedStatus
            .onEach { status -> _state.update { it.copy(feedStatus = status) } }
            .launchIn(viewModelScope)
    }

    private fun loadRoutes() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val routes = transitDb.getAllRoutes()
                _state.update { it.copy(availableRoutes = routes) }
            } catch (e: Exception) {
                Timber.e(e, "loadRoutes failed: ${e.message}")
            }
        }
    }

    private fun loadRoutesForStop(stop: Stop) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val routes = transitDb.getRoutesForStop(stop.stopId)
                _state.update { it.copy(routesForSelectedStop = routes) }
            } catch (e: Exception) {
                Timber.e(e, "loadRoutesForStop failed: ${e.message}")
                _state.update { it.copy(routesForSelectedStop = _state.value.availableRoutes) }
            }
        }
    }

    private suspend fun refreshNearbyStops(pos: LatLng) {
        try {
            val stops = transitDb.getNearbyStops(pos.lat, pos.lng, limit = 500)
            _state.update { it.copy(nearbyStops = stops) }
            loadUpcomingDepartures(pos)
        } catch (e: Exception) {
            Timber.e(e, "refreshNearbyStops failed: ${e.message}")
        }
    }

    private fun loadUpcomingDepartures(pos: LatLng) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = LocalTime.now().format(timeFormatter)
                val departures = transitDb.getUpcomingDeparturesNearby(
                    lat = pos.lat,
                    lng = pos.lng,
                    afterTime = now,
                    radiusStops = 10,
                )
                _state.update { it.copy(upcomingDepartures = departures) }
            } catch (e: Exception) {
                Timber.e(e, "loadUpcomingDepartures failed: ${e.message}")
            }
        }
    }

    private fun runFusionIfNeeded(snapshot: LocationSnapshot) {
        val rideState = _state.value.rideState
        if (rideState !is RideState.BoardingWindow &&
            rideState !is RideState.OnBus
        ) return

        val bundle = SignalBundle(
            location = snapshot,
            routeAlignmentScore = 0.7f,
            gtfsTripConfidence = estimateGtfsConfidence(),
            wifiConfidence = locationModule.scanWifiConfidence(),
            motionScore = locationModule.currentMotionScore(),
        )
        val result = fusionEngine.compute(bundle)
        fsmEngine.process(RideEvent.FusionUpdated(result))

        if (result.meetsThreshold && rideState is RideState.BoardingWindow) {
            val candidate = buildTripCandidate(rideState) ?: return
            fsmEngine.process(RideEvent.TripMatchUpdated(candidate))
        }
    }

    private fun estimateGtfsConfidence(): Float {
        val userPos = _state.value.userLocation ?: return 0f
        val vehicles = gtfsRtClient.vehicles.value
        if (vehicles.isEmpty()) return 0f
        val closestDist = vehicles.minOf { v ->
            locationModule.haversineMeters(userPos, LatLng(v.lat, v.lng))
        }
        return when {
            closestDist < 100 -> 0.95f
            closestDist < 300 -> 0.80f
            closestDist < 600 -> 0.60f
            else              -> 0.20f
        }
    }

    private fun buildTripCandidate(state: RideState.BoardingWindow): TripCandidate? {
        val userPos = _state.value.userLocation ?: return null
        val vehicle = gtfsRtClient.vehicles.value
            .minByOrNull { v ->
                locationModule.haversineMeters(userPos, LatLng(v.lat, v.lng))
            } ?: return null
        return TripCandidate(
            trip = Trip(vehicle.tripId ?: "", state.route.routeId, ""),
            route = state.route,
            vehicle = vehicle,
            routeAlignmentScore = 0.8f,
            gtfsConfidence = estimateGtfsConfidence(),
            stopsRemaining = emptyList(),
            nextStop = null,
            destinationStop = null,
        )
    }

    private fun updateArrivals() {
        val rideState = _state.value.rideState
        if (rideState !is RideState.WaitingAtStop) return
        val arrivals = gtfsRtClient.arrivalsForStop(
            stopId = rideState.stop.stopId,
            routeId = rideState.route.routeId,
        )
        fsmEngine.process(RideEvent.ArrivalsUpdated(arrivals))
        checkEtaThresholds()
    }

    private fun checkEtaThresholds() {
        val rideState = _state.value.rideState
        val (stopId, routeId) = when (rideState) {
            is RideState.WaitingAtStop  ->
                rideState.stop.stopId to rideState.route.routeId
            is RideState.BusApproaching ->
                rideState.stop.stopId to rideState.route.routeId
            is RideState.BoardingWindow ->
                rideState.stop.stopId to rideState.route.routeId
            else -> return
        }
        val arrivals = gtfsRtClient.arrivalsForStop(stopId, routeId)
        val soonest = arrivals.firstOrNull() ?: return
        val threshold = when {
            soonest.secsToArrival <= TransitConfig.ETA_T_BOARD_SECS   -> EtaThreshold.T_30SEC
            soonest.secsToArrival <= TransitConfig.ETA_T_STRONG_SECS  -> EtaThreshold.T_90SEC
            soonest.secsToArrival <= TransitConfig.ETA_T_ACTIVE_SECS  -> EtaThreshold.T_2MIN
            soonest.secsToArrival <= TransitConfig.ETA_T_PASSIVE_SECS -> EtaThreshold.T_5MIN
            else -> return
        }
        fsmEngine.process(RideEvent.EtaThresholdCrossed(soonest, soonest.secsToArrival, threshold))
    }
}
