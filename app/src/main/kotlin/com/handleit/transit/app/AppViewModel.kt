package com.handleit.transit.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handleit.transit.common.MapProvider
import com.handleit.transit.common.TransitConfig
import com.handleit.transit.data.gtfs.RouteDao
import com.handleit.transit.data.gtfsrt.GtfsRtClient
import com.handleit.transit.data.location.LocationModule
import com.handleit.transit.data.location.SensorFusionEngine
import com.handleit.transit.fsm.*
import com.handleit.transit.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppState(
    val rideState: RideState = RideState.Idle,
    val userLocation: LatLng? = null,
    val nearbyStops: List<Stop> = emptyList(),
    val nearbyVehicles: List<VehiclePosition> = emptyList(),
    val availableRoutes: List<Route> = emptyList(),
    val feedStatus: FeedStatus = FeedStatus.IDLE,
    val mapProvider: MapProvider = TransitConfig.MAP_PROVIDER_DEFAULT,
    val permissionsGranted: Boolean = false,
    val transitionLog: List<TransitionLog> = emptyList(),
    val selectedStop: Stop? = null,
)

sealed class AppIntent {
    data class RouteSelected(val route: Route, val stop: Stop, val destination: Stop?) : AppIntent()
    object ConfirmBoarding : AppIntent()
    object ConfirmExit : AppIntent()
    object DismissTrip : AppIntent()
    object Reset : AppIntent()
    object ToggleMapProvider : AppIntent()
    data class StopSelected(val stop: Stop?) : AppIntent()
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val locationModule: LocationModule,
    private val fusionEngine: SensorFusionEngine,
    private val gtfsRtClient: GtfsRtClient,
    private val routeDao: RouteDao,
) : ViewModel() {

    private val transitionLogs = mutableListOf<TransitionLog>()

    private val fsmEngine = RideFsmEngine(onTransition = { log ->
        transitionLogs.add(log)
        if (transitionLogs.size > 100) transitionLogs.removeAt(0)
        _state.update { it.copy(transitionLog = transitionLogs.toList()) }
    })

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        observeFsmState()
        observeLocation()
        observeVehicles()
        observeFeedStatus()
        loadRoutes()
        gtfsRtClient.startPolling()
    }

    fun onPermissionsResult(granted: Boolean) {
        _state.update { it.copy(permissionsGranted = granted) }
    }

    fun dispatch(intent: AppIntent) {
        when (intent) {
            is AppIntent.RouteSelected ->
                fsmEngine.process(RideEvent.RouteSelected(intent.route, intent.stop, intent.destination))
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
                    it.copy(mapProvider = if (it.mapProvider == MapProvider.GOOGLE)
                        MapProvider.OSM else MapProvider.GOOGLE)
                }
            is AppIntent.StopSelected ->
                _state.update { it.copy(selectedStop = intent.stop) }
        }
    }

    private fun observeFsmState() {
        fsmEngine.state
            .onEach { rideState -> _state.update { it.copy(rideState = rideState) } }
            .launchIn(viewModelScope)
    }

    private fun observeLocation() {
        viewModelScope.launch {
            locationModule.locationFlow().collect { snapshot ->
                _state.update { it.copy(userLocation = snapshot.latLng) }
                fsmEngine.process(RideEvent.LocationUpdated(snapshot))
                runFusionIfNeeded(snapshot)
                checkEtaThresholds()
            }
        }
    }

    private fun observeVehicles() {
        gtfsRtClient.vehicles
            .onEach { vehicles ->
                val userPos = _state.value.userLocation ?: return@onEach
                val nearby = vehicles.filter { v ->
                    locationModule.haversineMeters(userPos, LatLng(v.lat, v.lng)) <=
                            TransitConfig.NEARBY_ROUTES_RADIUS_METERS
                }.take(TransitConfig.MAX_NEARBY_ROUTES_ON_MAP)
                _state.update { it.copy(nearbyVehicles = nearby) }
                updateArrivals()
            }
            .launchIn(viewModelScope)
    }

    private fun observeFeedStatus() {
        gtfsRtClient.feedStatus
            .onEach { status -> _state.update { it.copy(feedStatus = status) } }
            .launchIn(viewModelScope)
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            routeDao.observeAll().collect { entities ->
                _state.update { it.copy(availableRoutes = entities.map { e -> e.toModel() }) }
            }
        }
    }

    private fun runFusionIfNeeded(snapshot: LocationSnapshot) {
        val rideState = _state.value.rideState
        if (rideState !is RideState.BoardingWindow && rideState !is RideState.OnBus) return

        val bundle = SignalBundle(
            location = snapshot,
            routeAlignmentScore = 0.7f,
            gtfsTripConfidence = estimateGtfsConfidence(rideState),
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

    private fun estimateGtfsConfidence(state: RideState): Float {
        val userPos = _state.value.userLocation ?: return 0f
        val vehicles = gtfsRtClient.vehicles.value
        if (vehicles.isEmpty()) return 0f
        val closestDist = vehicles.minOf { v ->
            locationModule.haversineMeters(userPos, LatLng(v.lat, v.lng))
        }
        return when {
            closestDist < 100  -> 0.95f
            closestDist < 300  -> 0.80f
            closestDist < 600  -> 0.60f
            else               -> 0.20f
        }
    }

    private fun buildTripCandidate(state: RideState.BoardingWindow): TripCandidate? {
        val vehicle = gtfsRtClient.vehicles.value
            .minByOrNull { v ->
                locationModule.haversineMeters(
                    _state.value.userLocation ?: return null,
                    LatLng(v.lat, v.lng),
                )
            } ?: return null
        return TripCandidate(
            trip = Trip(vehicle.tripId ?: "", state.route.routeId, ""),
            route = state.route,
            vehicle = vehicle,
            routeAlignmentScore = 0.8f,
            gtfsConfidence = estimateGtfsConfidence(state),
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
        val stopId = when (rideState) {
            is RideState.WaitingAtStop  -> rideState.stop.stopId
            is RideState.BusApproaching -> rideState.stop.stopId
            is RideState.BoardingWindow -> rideState.stop.stopId
            else -> return
        }
        val routeId = when (rideState) {
            is RideState.WaitingAtStop  -> rideState.route.routeId
            is RideState.BusApproaching -> rideState.route.routeId
            is RideState.BoardingWindow -> rideState.route.routeId
            else -> return
        }
        val arrivals = gtfsRtClient.arrivalsForStop(stopId, routeId)
        val soonest = arrivals.firstOrNull() ?: return

        val threshold = when {
            soonest.secsToArrival <= TransitConfig.ETA_T_BOARD_SECS  -> EtaThreshold.T_30SEC
            soonest.secsToArrival <= TransitConfig.ETA_T_STRONG_SECS -> EtaThreshold.T_90SEC
            soonest.secsToArrival <= TransitConfig.ETA_T_ACTIVE_SECS -> EtaThreshold.T_2MIN
            soonest.secsToArrival <= TransitConfig.ETA_T_PASSIVE_SECS -> EtaThreshold.T_5MIN
            else -> return
        }
        fsmEngine.process(RideEvent.EtaThresholdCrossed(soonest, soonest.secsToArrival, threshold))
    }
}
