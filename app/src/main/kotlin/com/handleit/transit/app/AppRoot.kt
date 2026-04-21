package com.handleit.transit.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.handleit.transit.feature.map.MapIntent
import com.handleit.transit.feature.map.MapScreen
import com.handleit.transit.feature.map.MapUiState
import com.handleit.transit.feature.riding.RidingScreen
import com.handleit.transit.feature.settings.SettingsScreen
import com.handleit.transit.fsm.RideState
import com.handleit.transit.ui.theme.TransitTheme

object Nav {
    const val MAP      = "map"
    const val RIDING   = "riding"
    const val SETTINGS = "settings"
}

@Composable
fun AppRoot(state: AppState, onIntent: (AppIntent) -> Unit) {
    TransitTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = Nav.MAP) {

                composable(Nav.MAP) {
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
}
