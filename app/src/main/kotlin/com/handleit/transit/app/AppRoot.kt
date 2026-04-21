package com.handleit.transit.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.handleit.transit.feature.map.ArrivalSheetContent
import com.handleit.transit.feature.map.MapIntent
import com.handleit.transit.feature.map.MapScreen
import com.handleit.transit.feature.map.MapUiState
import com.handleit.transit.feature.map.RouteArrival
import com.handleit.transit.feature.riding.RidingScreen
import com.handleit.transit.feature.settings.SettingsScreen
import com.handleit.transit.fsm.RideState
import com.handleit.transit.ui.theme.TransitTheme

object Nav {
    const val MAP      = "map"
    const val RIDING   = "riding"
    const val SETTINGS = "settings"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(state: AppState, onIntent: (AppIntent) -> Unit) {
    TransitTheme {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = Nav.MAP) {

            composable(Nav.MAP) {
                val scaffoldState = rememberBottomSheetScaffoldState(
                    bottomSheetState = rememberStandardBottomSheetState(
                        initialValue = SheetValue.PartiallyExpanded,
                        skipHideable = true,
                    )
                )

                // Build arrival list from AppState
                // Placeholder until getUpcomingDepartures is wired —
                // shows routes for nearby stops with no ETA yet
                val arrivals = remember(state.nearbyVehicles, state.nearbyStops) {
                    state.nearbyVehicles.mapNotNull { vehicle ->
                        val route = state.availableRoutes
                            .firstOrNull { it.routeId == vehicle.routeId }
                            ?: return@mapNotNull null
                        val stop = state.nearbyStops.firstOrNull()
                        RouteArrival(
                            route = route,
                            headsign = route.routeLongName,
                            nearestStopName = stop?.stopName ?: "",
                            etaMinutes = null,
                            isRealtime = false,
                            directionId = 0,
                        )
                    }.distinctBy { it.route.routeId }
                }

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 200.dp,
                    sheetContainerColor = Color(0xFF1A0A2E),
                    sheetContentColor = Color.White,
                    sheetDragHandle = {
                        // Drag handle pill
                        Surface(
                            modifier = androidx.compose.ui.Modifier
                                .padding(vertical = 8.dp)
                                .size(width = 36.dp, height = 4.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                            color = Color.White.copy(alpha = 0.3f),
                        ) {}
                    },
                    sheetContent = {
                        ArrivalSheetContent(
                            arrivals = arrivals,
                            onRouteClicked = { arrival ->
                                onIntent(
                                    AppIntent.RouteSelected(
                                        route = arrival.route,
                                        stop = state.nearbyStops.firstOrNull()
                                            ?: return@ArrivalSheetContent,
                                        destination = null,
                                    )
                                )
                                navController.navigate(Nav.RIDING)
                            }
                        )
                    },
                ) { paddingValues ->
                    MapScreen(
                        state = MapUiState(
                            userLocation       = state.userLocation,
                            nearbyStops        = state.nearbyStops,
                            nearbyVehicles     = state.nearbyVehicles,
                            selectedStop       = state.selectedStop,
                            availableRoutes    = state.routesForSelectedStop,
                            mapProvider        = state.mapProvider,
                            feedStatus         = state.feedStatus,
                            permissionsGranted = state.permissionsGranted,
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        onIntent = { intent ->
                            when (intent) {
                                is MapIntent.ToggleMapProvider ->
                                    onIntent(AppIntent.ToggleMapProvider)
                                is MapIntent.StopSelected ->
                                    onIntent(AppIntent.StopSelected(intent.stop))
                                is MapIntent.DismissStop ->
                                    onIntent(AppIntent.StopSelected(null))
                                else -> {}
                            }
                        },
                        onRouteSelected = { route, stop, dest ->
                            onIntent(AppIntent.RouteSelected(route, stop, dest))
                            navController.navigate(Nav.RIDING)
                        },
                    )
                }
            }

            composable(Nav.RIDING) {
                if (state.rideState is RideState.Idle) {
                    navController.popBackStack()
                } else {
                    RidingScreen(
                        state             = state.rideState,
                        onConfirmBoarding = { onIntent(AppIntent.ConfirmBoarding) },
                        onConfirmExit     = { onIntent(AppIntent.ConfirmExit) },
                        onDismissTrip     = {
                            onIntent(AppIntent.DismissTrip)
                            navController.popBackStack()
                        },
                        onReset           = {
                            onIntent(AppIntent.Reset)
                            navController.popBackStack()
                        },
                    )
                }
            }

            composable(Nav.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
