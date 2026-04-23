package com.handleit.transit.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.handleit.transit.feature.debug.DebugScreen
import com.handleit.transit.feature.map.ArrivalSheetContent
import com.handleit.transit.feature.map.MapIntent
import com.handleit.transit.feature.map.MapScreen
import com.handleit.transit.feature.map.MapUiState
import com.handleit.transit.feature.map.RouteArrival
import com.handleit.transit.feature.riding.RidingScreen
import com.handleit.transit.feature.settings.SettingsScreen
import com.handleit.transit.fsm.RideState
import com.handleit.transit.model.Route
import com.handleit.transit.ui.theme.TransitTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object Nav {
    const val MAP      = "map"
    const val RIDING   = "riding"
    const val SETTINGS = "settings"
    const val DEBUG    = "debug"
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

                val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
                val arrivals = remember(state.upcomingDepartures) {
                    val now = LocalTime.now()
                    state.upcomingDepartures.mapNotNull { departure ->
                        val route = Route(
                            routeId = departure.routeId,
                            routeShortName = departure.routeShortName,
                            routeLongName = departure.routeLongName,
                            routeType = 3,
                            routeColor = departure.routeColor,
                            routeTextColor = departure.routeTextColor,
                        )
                        val etaMinutes = try {
                            val depTime = LocalTime.parse(
                                departure.departureTime.let { t ->
                                    val parts = t.split(":")
                                    val h = parts[0].toInt() % 24
                                    "%02d:%s:%s".format(h, parts[1], parts[2])
                                },
                                timeFormatter
                            )
                            val diff = java.time.Duration.between(now, depTime).toMinutes()
                            if (diff < 0) return@mapNotNull null
                            diff.toInt()
                        } catch (e: Exception) {
                            null
                        }
                        RouteArrival(
                            route = route,
                            headsign = departure.headsign.ifBlank { departure.routeLongName },
                            nearestStopName = departure.stopName,
                            etaMinutes = etaMinutes,
                            isRealtime = false,
                            directionId = departure.directionId,
                        )
                    }.distinctBy { "${it.route.routeId}-${it.directionId}" }
                        .sortedBy { it.etaMinutes ?: Int.MAX_VALUE }
                }

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 200.dp,
                    sheetContainerColor = Color(0xFF1A0A2E),
                    sheetContentColor = Color.White,
                    sheetDragHandle = {
                        Surface(
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .size(width = 36.dp, height = 4.dp),
                            shape = RoundedCornerShape(2.dp),
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

            composable(Nav.DEBUG) {
                DebugScreen(
                    state = state,
                    onIntent = onIntent,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
