package com.handleit.transit.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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

/**
 * MapScreen
 *
 * Updated with provider-gate logic and clean dependency resolution.
 */
@Composable
fun MapScreen(
    state: MapUiState,
    onIntent: (MapIntent) -> Unit,
    onRouteSelected: (Route, Stop, Stop?) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // ── Map layer ─────────────────────────────────────────────────────────
        when (state.mapProvider) {
            MapProvider.GOOGLE -> GoogleMapLayer(state = state, onStopTapped = {
                onIntent(MapIntent.StopSelected(it))
            })
            MapProvider.OSM -> OsmMapLayer(state = state, onStopTapped = {
                onIntent(MapIntent.StopSelected(it))
            })
        }

        // ── Top controls ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🚌", fontSize = 16.sp)
                    Text(
                        "TRANSIT PRESENCE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    FeedStatusDot(status = state.feedStatus)
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                IconButton(
                    onClick = { onIntent(MapIntent.ToggleMapProvider) },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = "Toggle map provider (${state.mapProvider.name})",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (state.nearbyVehicles.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 68.dp, end = 12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.DirectionsBus, null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary)
                    Text(
                        "${state.nearbyVehicles.size} nearby",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = if (state.selectedStop != null) 220.dp else 12.dp),
        ) {
            Text(
                if (state.mapProvider == MapProvider.GOOGLE) "Google Maps"
                else "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
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
                    onRouteSelected = { route -> onRouteSelected(route, stop, null) },
                )
            }
        }

        if (state.nearbyStops.isEmpty() && state.selectedStop == null) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Scanning for nearby stops...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ─── Google Maps Layer ────────────────────────────────────────────────────────

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
        state.userLocation?.let { loc ->
            cameraState.animate(
                CameraUpdateFactory.newLatLngZoom(GmsLatLng(loc.lat, loc.lng), 15f),
                durationMs = 800,
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        properties = MapProperties(
            isMyLocationEnabled = state.permissionsGranted,
            mapType = MapType.NORMAL,
        ),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = true,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
        ),
    ) {
        state.nearbyStops.forEach { stop ->
            Marker(
                state = MarkerState(GmsLatLng(stop.lat, stop.lng)),
                title = stop.stopName,
                snippet = "Tap to select route",
                onClick = { onStopTapped(stop); true },
            )
        }

        state.nearbyVehicles.forEach { vehicle ->
            val bearing = vehicle.bearing ?: 0f
            MarkerComposable(
                state = MarkerState(GmsLatLng(vehicle.lat, vehicle.lng)),
                title = "Bus ${vehicle.vehicleId} — Route ${vehicle.routeId}", // Fix: Redundant Elvis removed (Line 263)
                snippet = vehicle.tripId ?: "",
            ) {
                // Fix: Composable Mismatch check (Line 266)
                if (state.mapProvider == MapProvider.GOOGLE) {
                    BusMarkerIcon(bearing = bearing, routeId = vehicle.routeId)
                }
            }
        }
    }
}

// ─── OpenStreetMap Layer ──────────────────────────────────────────────────────

@Composable
private fun OsmMapLayer(
    state: MapUiState,
    onStopTapped: (Stop) -> Unit,
) {
    // Fix: Line 279 (Unused Variable) removed

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

            state.userLocation?.let { loc ->
                mapView.controller.animateTo(GeoPoint(loc.lat, loc.lng))
            }

            state.nearbyStops.forEach { stop ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(stop.lat, stop.lng)
                    title = stop.stopName
                    subDescription = "Tap to select route"
                    setOnMarkerClickListener { _, _ ->
                        onStopTapped(stop)
                        true
                    }
                }
                mapView.overlays.add(marker)
            }

            state.nearbyVehicles.forEach { vehicle ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(vehicle.lat, vehicle.lng)
                    title = "Bus ${vehicle.vehicleId}" // Fix: Redundant Elvis removed (Line 314 equivalent)
                    subDescription = "Route ${vehicle.routeId}" // Fix: .orEmpty() removed (Line 315 equivalent)
                    rotation = -(vehicle.bearing ?: 0f)
                }
                mapView.overlays.add(marker)
            }

            mapView.invalidate()
        }
    )
}

@Composable
private fun BusMarkerIcon(bearing: Float, routeId: String?) {
    Box(contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Default.DirectionsBus,
                contentDescription = "Bus route $routeId",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(4.dp)
                    .rotate(bearing),
            )
        }
    }
}

@Composable
private fun StopSelectionCard(
    stop: Stop,
    routes: List<Route>,
    onDismiss: () -> Unit,
    onRouteSelected: (Route) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "SELECT ROUTE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stop.stopName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(12.dp))
            routes.take(5).forEach { route ->
                OutlinedButton(
                    onClick = { onRouteSelected(route) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            route.routeShortName,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            route.routeLongName,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedStatusDot(status: FeedStatus) {
    val color = when (status) {
        FeedStatus.LIVE       -> androidx.compose.ui.graphics.Color(0xFF00C853)
        FeedStatus.CONNECTING -> androidx.compose.ui.graphics.Color(0xFFFFD600)
        FeedStatus.ERROR      -> androidx.compose.ui.graphics.Color(0xFFFF3366)
        FeedStatus.IDLE       -> androidx.compose.ui.graphics.Color(0xFF9E9E9E)
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}
