package com.handleit.transit.app

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

// Use your actual internal model
import com.handleit.transit.model.UpcomingDeparture
import com.handleit.transit.ui.theme.TransitTheme

// 1. LOCAL UI MODEL (Prevents Import Errors)
data class RouteArrival(
    val route: String,
    val time: String,
    val headsign: String,
    val color: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(state: AppState, onIntent: (AppIntent) -> Unit) {
    TransitTheme {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "map") {
            composable("map") {
                val scaffoldState = rememberBottomSheetScaffoldState()

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 200.dp,
                    sheetContainerColor = Color(0xFF121212),
                    sheetContentColor = Color.White,
                    sheetContent = {
                        // 2. MAPPING LOGIC
                        val uiArrivals = state.arrivalsForUI.map { dep ->
                            RouteArrival(
                                route = dep.routeShortName,
                                time = dep.departureTime,
                                headsign = dep.headsign,
                                color = dep.routeColor
                            )
                        }

                        ArrivalSheetContent(
                            arrivals = uiArrivals,
                            isLoading = state.isLoadingDepartures,
                            errorMessage = state.departureErrorMessage,
                            onRouteClicked = { routeName ->
                                // Logic for when a bus is tapped
                                onIntent(AppIntent.RouteSelected(routeName, null, null))
                                navController.navigate("riding")
                            }
                        )
                    }
                ) { padding ->
                    // Your Map UI goes here
                    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                        Text("Map View Placeholder", modifier = Modifier.align(Alignment.Center))
                        
                        // Debug Trigger
                        SmallFloatingActionButton(
                            onClick = { navController.navigate("debug") },
                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                        ) {
                            Icon(Icons.Default.Build, "Debug")
                        }
                    }
                }
            }
            
            composable("debug") { 
                // Placeholder if DebugScreen is elsewhere, or paste DebugScreen code here
                Text("GTFS Diagnostic Lab") 
            }
            
            composable("riding") { 
                Text("Riding Dashboard")
            }
        }
    }
}

// 3. THE MISSING COMPONENT (Defined here to fix "Unresolved Reference")
@Composable
fun ArrivalSheetContent(
    arrivals: List<RouteArrival>,
    isLoading: Boolean,
    errorMessage: String?,
    onRouteClicked: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .heightIn(min = 200.dp)
    ) {
        Text(
            "Upcoming Departures", 
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (errorMessage != null) {
            Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error)
        } else if (arrivals.isEmpty()) {
            Text("No upcoming buses nearby.", color = Color.Gray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(arrivals) { arrival ->
                    ArrivalCard(arrival, onRouteClicked)
                }
            }
        }
    }
}

@Composable
fun ArrivalCard(arrival: RouteArrival, onClick: (String) -> Unit) {
    val busColor = try { Color(AndroidColor.parseColor("#${arrival.color}")) } catch (e: Exception) { Color.DarkGray }
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(arrival.route) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = busColor,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(width = 50.dp, height = 30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(arrival.route, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(arrival.headsign, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Text(arrival.time, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
