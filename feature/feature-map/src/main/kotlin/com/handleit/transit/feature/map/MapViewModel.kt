package com.handleit.transit.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handleit.transit.common.MapProvider
import com.handleit.transit.common.TransitConfig
import com.handleit.transit.data.gtfs.TransitDb
import com.handleit.transit.data.gtfsrt.GtfsRtClient
import com.handleit.transit.data.location.LocationModule
import com.handleit.transit.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

// ... MapUiState and MapIntent stay the same ...

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationModule: LocationModule,
    private val gtfsRtClient: GtfsRtClient,
    private val transitDb: TransitDb,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun handleIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.PermissionsGranted -> {
                _uiState.update { it.copy(permissionsGranted = intent.granted) }
                if (intent.granted) {
                    loadInitialData()
                    startLocationTracking()
                    startRealtimeUpdates()
                }
            }
            is MapIntent.StopSelected -> _uiState.update { it.copy(selectedStop = intent.stop) }
            is MapIntent.DismissStop -> _uiState.update { it.copy(selectedStop = null) }
            MapIntent.ToggleMapProvider -> {
                val next = if (_uiState.value.mapProvider == MapProvider.GOOGLE) MapProvider.OSM else MapProvider.GOOGLE
                _uiState.update { it.copy(mapProvider = next) }
            }
            else -> {}
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val routes = withContext(Dispatchers.IO) {
                try {
                    transitDb.getAllRoutes()
                } catch (t: Throwable) {
                    Timber.e(t, "Failed to load routes")
                    emptyList()
                }
            }
            _uiState.update { it.copy(availableRoutes = routes) }
        }
    }

    private fun startLocationTracking() {
        viewModelScope.launch {
            // FIX: locationFlow is a function, not a property. 
            // We call it with parens: locationFlow()
            locationModule.locationFlow()
                .distinctUntilChanged { old, new -> 
                    old.latLng.lat == new.latLng.lat && old.latLng.lng == new.latLng.lng 
                }
                .onEach { snapshot ->
                    _uiState.update { it.copy(userLocation = snapshot.latLng) }
                    updateNearbyStops(snapshot.latLng)
                }
                .catch { t -> Timber.e(t, "Location tracking failed") }
                .collect()
        }
    }

    private fun updateNearbyStops(latLng: LatLng) {
        viewModelScope.launch(Dispatchers.IO) {
            val stops = try {
                transitDb.getNearbyStops(latLng.lat, latLng.lng, 1000.0)
            } catch (t: Throwable) {
                Timber.e(t, "Nearby query failed")
                emptyList()
            }
            _uiState.update { it.copy(nearbyStops = stops) }
        }
    }

    private fun startRealtimeUpdates() {
        viewModelScope.launch {
            while (true) {
                try {
                    _uiState.update { it.copy(feedStatus = FeedStatus.CONNECTING) }
                    val vehicles = gtfsRtClient.fetchVehiclePositions()
                    _uiState.update { it.copy(nearbyVehicles = vehicles, feedStatus = FeedStatus.LIVE) }
                } catch (t: Throwable) {
                    Timber.e(t, "GTFS-RT sync failed")
                    _uiState.update { it.copy(feedStatus = FeedStatus.ERROR) }
                }
                delay(30_000)
            }
        }
    }
}
