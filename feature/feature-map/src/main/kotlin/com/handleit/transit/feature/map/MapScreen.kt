package com.handleit.transit.feature.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng as GmsLatLng
import com.google.maps.android.compose.*
import com.handleit.transit.common.MapProvider
import com.handleit.transit.model.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(
    state: MapUiState,
    onIntent: (MapIntent) -> Unit,
    onRouteSelected: (Route, Stop, Stop?) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {

        when (state.mapProvider) {
            MapProvider.GOOGLE -> GoogleMapLayer(state) {
                onIntent(MapIntent.StopSelected(it))
            }
            MapProvider.OSM -> OsmMapLayer(state) {
                onIntent(MapIntent.StopSelected(it))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🚌", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "TRANSIT PRESENCE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    FeedStatusDot(state.feedStatus)
                }
            }

            IconButton(onClick = { onIntent(MapIntent.ToggleMapProvider) }) {
                Icon(Icons.Default.Layers, contentDescription = "Toggle map")
            }
        }

        AnimatedVisibility(
            visible = state.selectedStop != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            state.selectedStop?.let { stop ->
                StopSelectionCard(
                    stop = stop,
                    routes = state.availableRoutes,
                    onDismiss = { onIntent(MapIntent.DismissStop) },
                    onRouteSelected = { route ->
                        onRouteSelected(route, stop, null)
                    }
                )
            }
        }
    }
}

// ---------------- GOOGLE MAP ----------------
@Composable
private fun GoogleMapLayer(
    state: MapUiState,
    onStopTapped: (Stop) -> Unit,
) {
    val defaultPos = GmsLatLng(28.5383, -81.3792)

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPos, 14f)
    }

    LaunchedEffect(state.userLocation) {
        state.userLocation?.let {
            cameraState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    GmsLatLng(it.lat, it.lng),
                    15f
                )
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        properties = MapProperties(
            isMyLocationEnabled = state.permissionsGranted
        ),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = true
        )
    ) {

        // ---------------- STOPS ----------------
        state.nearbyStops.forEach { stop ->
            Marker(
                state = MarkerState(GmsLatLng(stop.lat, stop.lng)),
                title = stop.stopName,
                onClick = {
                    onStopTapped(stop)
                    true
                }
            )
        }

        // ---------------- VEHICLES ----------------
        state.nearbyVehicles.forEach { vehicle ->
            MarkerComposable(
                state = MarkerState(GmsLatLng(vehicle.lat, vehicle.lng))
            ) {
                BusMarkerIcon(
                    bearing = vehicle.bearing ?: 0f,
                    routeId = vehicle.routeId
                )
            }
        }
    }
}

