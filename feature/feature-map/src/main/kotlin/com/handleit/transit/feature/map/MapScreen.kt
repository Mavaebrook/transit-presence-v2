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
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng as GmsLatLng
import com.google.maps.android.compose.*

import com.handleit.transit.common.MapProvider
import com.handleit.transit.model.*
import com.handleit.transit.model.FeedStatus
import com.handleit.transit.model.Route
import com.handleit.transit.model.Stop
import com.handleit.transit.model.Vehicle

import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

// ---------------- ROOT SCREEN ----------------

@Composable
fun MapScreen(
    state: MapUiState,
    onIntent: (MapIntent) -> Unit,
    onRouteSelected: (Route, Stop, Stop?) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {

        when (state.mapProvider) {
            MapProvider.GOOGLE -> GoogleMapLayer(
                state = state,
                onStopTapped = { stop ->
                    onIntent(MapIntent.StopSelected(stop))
                }
            )

            MapProvider.OSM -> OsmMapLayer(
                state = state,
                onStopTapped = { stop ->
                    onIntent(MapIntent.StopSelected(stop))
                }
            )
        }

        // ---------------- TOP BAR ----------------
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
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
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

            IconButton(
                onClick = { onIntent(MapIntent.ToggleMapProvider) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Toggle map")
            }
        }

        // ---------------- BOTTOM SHEET ----------------
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

// ---------------- GOOGLE MAP LAYER ----------------

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
        properties = MapProperties(isMyLocationEnabled = state.permissionsGranted),
        uiSettings = MapUiSettings(myLocationButtonEnabled = true)
    ) {
        // STOPS
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

        // VEHICLES
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

// ---------------- OSM MAP LAYER ----------------

@Composable
private fun OsmMapLayer(
    state: MapUiState,
    onStopTapped: (Stop) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(28.5383, -81.3792))
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            state.userLocation?.let {
                mapView.controller.animateTo(GeoPoint(it.lat, it.lng))
            }

            state.nearbyStops.forEach { stop ->
                val marker = org.osmdroid.views.overlay.Marker(mapView).apply {
                    position = GeoPoint(stop.lat, stop.lng)
                    title = stop.stopName
                    setOnMarkerClickListener { _, _ ->
                        onStopTapped(stop)
                        true
                    }
                }
                mapView.overlays.add(marker)
            }

            state.nearbyVehicles.forEach { v ->
                val marker = org.osmdroid.views.overlay.Marker(mapView).apply {
                    position = GeoPoint(v.lat, v.lng)
                    title = "Bus ${v.vehicleId}"
                    rotation = -(v.bearing ?: 0f)
                }
                mapView.overlays.add(marker)
            }
            mapView.invalidate()
        }
    )
}

// ---------------- UI HELPERS & COMPONENTS ----------------

@Composable
fun StopSelectionCard(
    stop: Stop,
    routes: List<Route>,
    onDismiss: () -> Unit,
    onRouteSelected: (Route) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stop.stopName, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            
            Text("Available Routes:", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            
            routes.forEach { route ->
                OutlinedButton(
                    onClick = { onRouteSelected(route) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("Route ${route.shortName}: ${route.longName}")
                }
            }
        }
    }
}

@Composable
fun BusMarkerIcon(bearing: Float, routeId: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .rotate(bearing)
    ) {
        Icon(
            imageVector = Icons.Default.DirectionsBus,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun FeedStatusDot(status: FeedStatus) {
    val color = when (status) {
        FeedStatus.LIVE -> Color.Green
        FeedStatus.CONNECTING -> Color.Yellow
        FeedStatus.ERROR -> Color.Red
        FeedStatus.IDLE -> Color.Gray
    }

    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}
