package com.handleit.transit.feature.riding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handleit.transit.fsm.AlertLevel
import com.handleit.transit.fsm.RideState
import com.handleit.transit.model.*

@Composable
fun RidingScreen(
    state: RideState,
    onConfirmBoarding: () -> Unit,
    onConfirmExit: () -> Unit,
    onDismissTrip: () -> Unit,
    onReset: () -> Unit,
) {
    AnimatedContent(
        targetState = state::class,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "RidingTransition",
    ) { _ ->
        when (state) {
            is RideState.WaitingAtStop   -> WaitingScreen(state, onReset)
            is RideState.BusApproaching  -> ApproachingScreen(state, onReset)
            is RideState.BoardingWindow  -> BoardingScreen(state, onConfirmBoarding, onReset)
            is RideState.OnBus           -> OnBusScreen(state, onConfirmExit, onReset)
            is RideState.ApproachingExit -> ApproachingExitScreen(state, onConfirmExit)
            is RideState.ExitWindow      -> ExitWindowScreen(state, onConfirmExit)
            is RideState.TripComplete    -> TripCompleteScreen(state, onDismissTrip)
            else                         -> {}
        }
    }
}

@Composable
fun WaitingScreen(state: RideState.WaitingAtStop, onCancel: () -> Unit) {
    TransitScaffold {
        StateChip("WAITING AT STOP", MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))
        Text(
            state.stop.stopName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        RouteTag(state.route)
        Spacer(Modifier.height(28.dp))
        if (state.arrivals.isEmpty()) {
            LoadingRow("Loading arrivals...")
        } else {
            Text(
                "NEXT BUS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(8.dp))
            state.arrivals.take(3).forEach { ArrivalRow(it) }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) {
            Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun ApproachingScreen(state: RideState.BusApproaching, onCancel: () -> Unit) {
    TransitScaffold {
        StateChip("BUS APPROACHING", MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        val progress = (1f - state.secsToArrival / 300f).coerceIn(0f, 1f)
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(160.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
                strokeWidth = 6.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CountdownText(
                    state.secsToArrival,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "AWAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        InfoCard(
            "ROUTE ${state.route.routeShortName}",
            state.arrival.headsign.ifEmpty { state.route.routeLongName },
            MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "🚶 Position yourself at the stop",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) {
            Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun BoardingScreen(
    state: RideState.BoardingWindow,
    onConfirmBoarding: () -> Unit,
    onCancel: () -> Unit,
) {
    val alertColor = when (state.level) {
        AlertLevel.PASSIVE  -> MaterialTheme.colorScheme.secondary
        AlertLevel.ACTIVE   -> Color(0xFFFF9800)
        AlertLevel.STRONG   -> Color(0xFFFF5722)
        AlertLevel.CRITICAL -> Color(0xFFFF3366)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(alertColor.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                if (state.level >= AlertLevel.STRONG) "🚨 BOARD NOW 🚨" else "BOARD NOW",
                style = MaterialTheme.typography.displaySmall,
                color = alertColor,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(20.dp))
            CountdownText(
                state.secsToArrival,
                style = MaterialTheme.typography.displayLarge,
                color = alertColor,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "ROUTE ${state.route.routeShortName}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                state.stop.stopName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onConfirmBoarding,
                colors = ButtonDefaults.buttonColors(containerColor = alertColor),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    "I'M ON THE BUS",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onCancel) {
                Text(
                    "Missed it",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
fun OnBusScreen(
    state: RideState.OnBus,
    onConfirmExit: () -> Unit,
    onReset: () -> Unit,
) {
    val trip = state.trip
    val stopsTotal = trip.stopsRemaining.size + 1
    val progress = if (stopsTotal > 0) 1f - trip.stopsRemaining.size.toFloat() / stopsTotal else 0f

    TransitScaffold {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateChip("ON BUS", MaterialTheme.colorScheme.secondary)
            ConfidenceBadge(state.fusionResult.onBusConfidence)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Route ${trip.route.routeShortName}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            trip.route.routeLongName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "ROUTE PROGRESS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(20.dp))
        trip.nextStop?.let { next ->
            InfoCard("NEXT STOP", next.stopName, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
        }
        trip.destinationStop?.let { dest ->
            InfoCard(
                "YOUR DESTINATION",
                "${dest.stopName}\n${trip.stopsRemaining.size} stops away",
                MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onConfirmExit) {
                Text(
                    "Exit now",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
            TextButton(onClick = onReset) {
                Text(
                    "End trip",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
fun ApproachingExitScreen(state: RideState.ApproachingExit, onConfirmExit: () -> Unit) {
    TransitScaffold {
        StateChip("PREPARE TO EXIT", Color(0xFFFF9800))
        Spacer(Modifier.height(28.dp))
        Text("🔔", fontSize = 64.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            if (state.stopsRemaining == 1) "1 STOP AWAY"
            else "${state.stopsRemaining} STOPS AWAY",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFFFF9800),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(20.dp))
        InfoCard("YOUR DESTINATION", state.destinationStop.stopName, Color(0xFFFF9800))
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onConfirmExit,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                "I'VE EXITED",
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ExitWindowScreen(state: RideState.ExitWindow, onConfirmExit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFF3366).copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text("🛑", fontSize = 80.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "PULL CORD NOW",
                style = MaterialTheme.typography.displaySmall,
                color = Color(0xFFFF3366),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                state.destinationStop.stopName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            state.secsToArrival?.let { secs ->
                Spacer(Modifier.height(8.dp))
                CountdownText(secs, MaterialTheme.typography.headlineMedium, Color(0xFFFF3366))
            }
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onConfirmExit,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3366)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    "EXITED",
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun TripCompleteScreen(state: RideState.TripComplete, onDismiss: () -> Unit) {
    TransitScaffold {
        Spacer(Modifier.weight(1f))
        Text(
            "✓",
            fontSize = 80.sp,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "TRIP COMPLETE",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        state.exitedStop?.let {
            Text(
                it.stopName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val mins = state.durationMs / 60_000
        Text(
            "${mins}m ride on Route ${state.routeName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                "DONE",
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ─── Shared Components ────────────────────────────────────────────────────────

@Composable
private fun TransitScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun StateChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RouteTag(route: Route) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Text(
            "ROUTE ${route.routeShortName}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.06f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = color)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ArrivalRow(arrival: BusArrival) {
    val mins = arrival.secsToArrival / 60
    val secs = arrival.secsToArrival % 60
    val eta = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    arrival.routeShortName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    arrival.headsign.ifEmpty { "Route ${arrival.routeId}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    eta,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (mins < 2) Color(0xFFFF9800)
                            else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (arrival.isRealtime) "LIVE" else "SCHED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (arrival.isRealtime) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun CountdownText(
    secs: Long,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
) {
    val m = secs / 60
    val s = secs % 60
    Text(
        if (m > 0) "${m}m ${"%02d".format(s)}s" else "${s}s",
        style = style,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ConfidenceBadge(confidence: Float) {
    val color = when {
        confidence >= 0.85f -> MaterialTheme.colorScheme.secondary
        confidence >= 0.60f -> Color(0xFFFFD600)
        else                -> Color(0xFFFF9800)
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(
            "${(confidence * 100).toInt()}% CONF",
            modifier = Modifier.padding(horizontal = 
