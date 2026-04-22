package com.handleit.transit.feature.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.handleit.transit.app.AppIntent
import com.handleit.transit.app.AppState
import com.handleit.transit.model.UpcomingDeparture
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    state: AppState,
    onIntent: (AppIntent) -> Unit,
    onBack: () -> Unit
) {
    // Local UI state for the input fields
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
            // --- Query Builder Section ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Query Builder", 
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
                        OutlinedTextField(
                            value = stopIdInput,
                            onValueChange = { stopIdInput = it },
                            label = { Text("Stop ID") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = routeNumInput,
                            onValueChange = { routeNumInput = it },
                            label = { Text("Route #") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { timeInput = it },
                        label = { Text("Start Time (HH:mm:ss)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { 
                            onIntent(AppIntent.RunDebugQuery(
                                stopId = stopIdInput.ifBlank { null },
                                routeNumber = routeNumInput.ifBlank { null },
                                time = timeInput.ifBlank { "00:00:00" }
                            )) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Execute Diagnostic Query")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Action Section (Promotion) ---
            if (state.debugResults.isNotEmpty()) {
                Button(
                    onClick = { onIntent(AppIntent.PromoteDebugToUI) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6200EE) // Distinct color for "Promote" action
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Promote Results to Main UI")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- Results Header ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Results (${state.debugResults.size})", 
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.isDebugLoading) {
                    Spacer(Modifier.width(16.dp))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            state.debugErrorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Results List ---
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.debugResults) { departure ->
                    DebugResultItem(departure)
                }
                
                if (state.debugResults.isEmpty() && !state.isDebugLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No results. Try adjusting the filters.", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebugResultItem(dep: UpcomingDeparture) {
    val routeColor = try {
        Color(android.graphics.Color.parseColor("#${dep.routeColor}"))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            // Color strip on the left
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(routeColor)
            )
            
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Route ${dep.routeShortName}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = dep.departureTime,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp
                    )
                }
                
                Text(
                    text = "To: ${dep.headsign}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp), alpha = 0.3f)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Stop: ${dep.stopName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "ID: ${dep.stopId} | Seq: ${dep.stopSequence}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    
                    Text(
                        text = if (dep.directionId == 0) "INBOUND" else "OUTBOUND",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
