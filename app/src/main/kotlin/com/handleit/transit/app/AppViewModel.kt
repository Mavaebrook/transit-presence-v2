package com.handleit.transit.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handleit.transit.common.MapProvider
import com.handleit.transit.common.TransitConfig
import com.handleit.transit.data.gtfs.TransitDb
import com.handleit.transit.data.gtfsrt.GtfsRtClient
import com.handleit.transit.data.location.LocationModule
import com.handleit.transit.data.location.SensorFusionEngine
import com.handleit.transit.feature.map.RouteArrival
import com.handleit.transit.fsm.*
import com.handleit.transit.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import javax.inject.Inject

data class AppState(
    val rideState: RideState = RideState.Idle,
    val userLocation: LatLng? = null,
    val nearbyStops: List<Stop> = emptyList(),
    val nearbyVehicles: List<VehiclePosition> = emptyList(),
    val availableRoutes: List<Route> = emptyList(),
    val routesForSelectedStop: List<Route> = emptyList(),
    val upcomingDepartures: List<UpcomingDeparture> = emptyList(),
    val arrivalsForUI: List<RouteArrival> = emptyList(), 
    val isLoadingDepartures: Boolean = false,           
    val departureErrorMessage: String? = null,          
    val feedStatus: FeedStatus = FeedStatus.IDLE,
    val mapProvider: MapProvider = TransitConfig.MAP_PROVIDER_DEFAULT,
    val permissionsGranted: Boolean = false,
    val transitionLog: List<TransitionLog> = emptyList(),
    val selectedStop: Stop? = null,
    
    // Diagnostic GTFS Lab State
    val debugResults: List<UpcomingDeparture> = emptyList(),
    val isDebugLoading: Boolean = false,
    val debugErrorMessage: String? = null
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
    
    // Diagnostic GTFS Lab Intents
    data class RunDebugQuery(
        val stopId: String?,
        val routeNumber: String?,
        val time: String?
    ) : AppIntent()
    object PromoteDebugToUI : AppIntent()
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val locationModule: LocationModule,
    private val fusionEngine: SensorFusionEngine,
    private val gtfsRtClient: GtfsRtClient,
    private val transitDb: TransitDb,
) : ViewModel() {

    private val transitionLogs = mutableListOf<TransitionLog>()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val fsmEngine = RideFsmEngine(onTransition = { log ->
        transitionLogs.add(log)
        if (transitionLogs.size > 100) transitionLogs.removeAt(0)
        _state.update { it.copy(transitionLog = transitionLogs.toList()) }
    })

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

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
            is AppIntent.RunDebugQuery -> executeDebugQuery(intent)
            is AppIntent.PromoteDebugToUI -> promoteDebugToUI()
        }
    }

    private fun executeDebugQuery(intent: AppIntent.RunDebugQuery) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isDebugLoading = true, debugErrorMessage = null) }
            try {
                val results = transitDb.getDebugData(
                    stopId = intent.stopId,
                    routeShortName = intent.routeNumber,
                    startTime = intent.time
                )
                _state.update { it.copy(debugResults = results, isDebugLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "executeDebugQuery failed: ${e.message}")
                _state.update { it.copy(debugErrorMessage = e
                                        
