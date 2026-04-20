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
    private val transitDb: TransitDb,
) : ViewModel() {
    
    // ViewModel implementation logic relies on transitDb.getStopById(), transitDb.getAllRoutes(), etc.

}
