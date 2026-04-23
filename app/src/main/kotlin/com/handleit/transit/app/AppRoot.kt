package com.handleit.transit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// Logic Imports (Crucial for resolving AppState/AppIntent)
import com.handleit.transit.app.AppState
import com.handleit.transit.app.AppIntent
import com.handleit.transit.app.AppViewModel

// Feature and Model Imports
import com.handleit.transit.feature.map.ArrivalSheetContent
import com.handleit.transit.feature.map.MapIntent
import com.handleit.transit.feature.map.MapScreen
import com.handleit.transit.feature.map.MapUiState
import com.handleit.transit.feature.riding.RidingScreen
import com.handleit.transit.feature.settings.SettingsScreen
import com.handleit.transit.fsm.RideState
import com.handleit.transit.model.UpcomingDeparture
import com.handleit.transit.ui.theme.TransitTheme

// Utility Imports
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.graphics.Color as AndroidColor // Needed for parseColor

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
                val sheetState = rememberStandardBottomSheetState(
                    initialValue = SheetValue.PartiallyExpanded
                )
                val scaffoldState = rememberBottomSheetScaffoldState(
                    bottomSheetState = sheetState
                )

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
                            arrivals = state.arrivalsForUI,
                            isLoading = state.isLoadingDepartures,
                            errorMessage = state.departureErrorMessage,
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
                    Box(modifier = Modifier.fillMaxSize()) {
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

                        FloatingActionButton(
                            onClick = { navController.navigate(Nav.DEBUG) },
                            modifier = Modifier
                                .padding(paddingValues)
                                .padding(16.dp)
                                .align(Alignment.TopEnd),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "Open GTFS Lab")
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    state: AppState,
    onIntent: (AppIntent) -> Unit,
    onBack: () -> Unit
) {
    var stopIdInput by remember { mutableStateOf("") }
    var routeNumInput by remember { mutableStateOf("") }
    var timeInput by remember { 
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GTFS Diagnostic Lab") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Query Builder", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = stopIdInput, onValueChange = { stopIdInput = it }, label = { Text("Stop ID") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = routeNumInput, onValueChange = { routeNumInput = it }, label = { Text("Route #") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = timeInput, onValueChange = { timeInput = it }, label = { Text("Start Time (HH:mm:ss)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { 
                            onIntent(AppIntent.RunDebugQuery(
                                stopId = stopIdInput.ifBlank { null },
                                routeNumber = routeNumInput.ifBlank { null },
                                time = timeInput.ifBlank { "00:00:00" }
                            )) 
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Execute Diagnostic Query")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.debugResults.isNotEmpty()) {
                Button(
                    onClick = { onIntent(AppIntent.PromoteDebugToUI) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                ) {
                    Icon(Icons.Default.Send, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Promote Results to Main UI")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Results (${state.debugResults.size})", style = MaterialTheme.typography.titleMedium)
                if (state.isDebugLoading) {
                    Spacer(Modifier.width(16.dp))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            state.debugErrorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.debugResults) { departure ->
                    DebugResultItem(departure)
                }
            }
        }
    }
}

@Composable
fun DebugResultItem(dep: UpcomingDeparture) {
    // Uses AndroidColor alias to avoid confusion with Compose Color
    val routeColor = try { 
        Color(AndroidColor.parseColor("#${dep.routeColor}")) 
    } catch (e: Exception) { 
        MaterialTheme.colorScheme.surfaceVariant 
    }
    
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(6.dp).background(routeColor))
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Route ${dep.routeShortName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(text = dep.departureTime, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                }
                Text(text = "To: ${dep.headsign}", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Stop: ${dep.stopName}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(text = "ID: ${dep.stopId} | Seq: ${dep.stopSequence}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Text(text = if (dep.directionId == 0) "INBOUND" else "OUTBOUND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
