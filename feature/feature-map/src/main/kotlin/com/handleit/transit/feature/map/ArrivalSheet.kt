package com.handleit.transit.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handleit.transit.model.Route

// ─── Data model for an arrival card ──────────────────────────────────────────

data class RouteArrival(
    val route: Route,
    val headsign: String,
    val stopId: String?,
    val nearestStopName: String,
    val etaMinutes: Int?,       // null = scheduled only
    val isRealtime: Boolean,
    val directionId: Int,       // 0 = outbound, 1 = inbound
    val scheduledTime: String = "", // Added for unique key identification
)

// ─── Search Bar ──────────────────────────────────────────────────────────────

@Composable
fun ArrivalSearchBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1B5E20),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Where to?",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
            )
        }
    }
}

// ─── Route Arrival Card ───────────────────────────────────────────────────────

@Composable
fun RouteArrivalCard(
    arrival: RouteArrival,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routeColor = try {
        Color(android.graphics.Color.parseColor("#${arrival.route.routeColor}"))
    } catch (_: Exception) {
        Color(0xFF1B5E20)
    }

    val textColor = try {
        Color(android.graphics.Color.parseColor("#${arrival.route.routeTextColor}"))
    } catch (_: Exception) {
        Color.White
    }

    // Direction-based styling
    // 0 = Outbound, 1 = Inbound (usually toward terminal)
    val isInbound = arrival.directionId == 1
    val backgroundColor = if (isInbound) {
        Color(0xFF2C1C4D) // Darker purple for Inbound
    } else {
        routeColor.copy(alpha = 0.15f) // Thematic tint for Outbound
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp), // Slightly rounded for modern look
        color = backgroundColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(routeColor),
            ) {
                // If routeShortName is empty, fallback to the first few chars of routeId
                val displayText = arrival.route.routeShortName.ifBlank { 
                    arrival.route.routeId.take(3) 
                }
                Text(
                    text = displayText,
                    color = textColor,
                    fontSize = if (displayText.length > 3) 14.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = arrival.headsign,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                
                if (isInbound) {
                    Text(
                        text = "(Lynx Central)",
                        color = Color(0xFF69F0AE), // Soft green for indicator
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = arrival.nearestStopName,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                if (arrival.etaMinutes != null) {
                    Text(
                        text = if (arrival.etaMinutes <= 0) "NOW" else "${arrival.etaMinutes}",
                        color = if (arrival.isRealtime) Color(0xFF69F0AE) else Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 28.sp,
                    )
                    if (arrival.etaMinutes > 0) {
                        Text(
                            text = "min",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    Text(
                        text = "--",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ─── Sheet Content ────────────────────────────────────────────────────────────

@Composable
fun ArrivalSheetContent(
    arrivals: List<RouteArrival>,
    onRouteClicked: (RouteArrival) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ArrivalSearchBar()

        HorizontalDivider(
            color = Color.White.copy(alpha = 0.08f),
            thickness = 1.dp,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = Color(0xFF69F0AE),
                        modifier = Modifier.size(36.dp)
                    )
                }
                errorMessage != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
                arrivals.isEmpty() -> {
                    Text(
                        text = "No upcoming buses for this stop.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(
                            items = arrivals,
                            // Key combines route, direction, and time to ensure uniqueness
                            key = { "${it.route.routeId}-${it.directionId}-${it.scheduledTime}" }
                        ) { arrival ->
                            RouteArrivalCard(
                                arrival = arrival,
                                onClick = { onRouteClicked(arrival) }
                            )
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.05f),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
