package com.handleit.transit.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handleit.transit.common.MapProvider
import com.handleit.transit.common.TransitConfig
import com.handleit.transit.data.gtfs.RouteDao
import com.handleit.transit.data.gtfs.StopDao
import com.handleit.transit.data.gtfsrt.GtfsRtClient
import com.handleit.transit.data.location.LocationModule
import com.handleit.transit.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val userLocation: LatLng? = null,
    val nearbyStops: List<Stop> = emptyList(),
    val nearbyVehicles: List<VehiclePosition> = emptyList(),
    val selectedStop: Stop? = null,
    val availableRoutes: List<Route> = emptyList(),
    val mapProvider: MapProvider = TransitConfig.MAP_PROVIDER_DEFAULT,
    val feedStatus: FeedStatus = FeedStatus.IDLE,
    val permissionsGranted: Boolean = false,
)

sealed class MapIntent {
    data class StopSelected(val stop: Stop) : MapIntent()
    data class RouteSelected(val route: Route, val destination: Stop?) : MapIntent()
    object DismissStop : MapIntent()
    object ToggleMapProvider : MapIntent()
    data class PermissionsGranted(val granted: Boolean) : MapIntent()
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationModule: LocationModule,
    private val gtfsRtClient: GtfsRtClient,
    private val stopDao: StopDao,
    private val routeDao: RouteDao,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    init {
        observeLocation()
        observeVehicles()
        observeFeedStatus()
        loadRoutes()
    }

    fun dispatch(intent: MapIntent) {
        when (intent) {
            is MapIntent.StopSelected ->
                _state.update { it.copy(selectedStop = intent.stop) }
            is MapIntent.DismissStop ->
                _state.update { it.copy(selectedStop = null) }
            is MapIntent.ToggleMapProvider ->
                _state.update {
                    it.copy(mapProvider = if (it.mapProvider == MapProvider.GOOGLE)
                        MapProvider.OSM else MapProvider.GOOGLE)
                }
            is MapIntent.PermissionsGranted ->
                _state.update { it.copy(permissionsGranted = intent.granted) }
            is MapIntent.RouteSelected -> { /* handled by parent orchestrator */ }
        }
    }

    private fun observeLocation() {
        viewModelScope.launch {
            locationModule.locationFlow().collect { snapshot ->
                _state.update { it.copy(userLocation = snapshot.latLng) }
                refreshNearbyStops(snapshot.latLng)
                filterNearbyVehicles(snapshot.latLng)
            }
        }
    }

    private fun observeVehicles() {
        gtfsRtClient.vehicles
            .onEach { vehicles ->
                val userPos = _state.value.userLocation ?: return@onEach
                val nearby = filterVehiclesNearUser(vehicles, userPos)
                _state.update { it.copy(nearbyVehicles = nearby) }
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

    private suspend fun refreshNearbyStops(pos: LatLng) {
        val stops = stopDao.getNearby(pos.lat, pos.lng, limit = 10)
            .map { it.toModel() }
        _state.update { it.copy(nearbyStops = stops) }
    }

    private fun filterNearbyVehicles(pos: LatLng) {
        val all = gtfsRtClient.vehicles.value
        val nearby = filterVehiclesNearUser(all, pos)
        _state.update { it.copy(nearbyVehicles = nearby) }
    }

    /**
     * Filters vehicles to only those within NEARBY_ROUTES_RADIUS_METERS of the user.
     * This keeps the map uncluttered and prevents tracking the whole region.
     */
    private fun filterVehiclesNearUser(
        vehicles: List<VehiclePosition>,
        userPos: LatLng,
    ): List<VehiclePosition> {
        return vehicles
            .filter { v ->
                val dist = locationModule.haversineMeters(
                    userPos,
                    LatLng(v.lat, v.lng),
                )
                dist <= TransitConfig.NEARBY_ROUTES_RADIUS_METERS
            }
            .take(TransitConfig.MAX_NEARBY_ROUTES_ON_MAP)
    }
}
