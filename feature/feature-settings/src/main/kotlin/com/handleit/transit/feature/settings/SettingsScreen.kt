package com.handleit.transit.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.handleit.transit.common.MapProvider
import com.handleit.transit.common.TransitConfig
import com.handleit.transit.fsm.TransitionLog

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "SETTINGS",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(8.dp))

        // ── Map Provider ─────────────────────────────────────────────────────
        SectionHeader("MAP PROVIDER")
        SettingCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Map Source", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (state.mapProvider == MapProvider.GOOGLE)
                            "Google Maps (requires API key)"
                        else
                            "OpenStreetMap (open source, no key needed)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Switch(
                    checked = state.mapProvider == MapProvider.OSM,
                    onCheckedChange = { viewModel.dispatch(SettingsIntent.ToggleMapProvider) },
                )
            }
            if (state.mapProvider == MapProvider.OSM) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "No API key required · Tiles cached offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        // ── Mock Mode ────────────────────────────────────────────────────────
        SectionHeader("DEVELOPMENT")
        SettingCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Mock Mode", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Use fake GTFS data instead of live feeds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Switch(
                    checked = state.mockModeEnabled,
                    onCheckedChange = { viewModel.dispatch(SettingsIntent.ToggleMockMode) },
                )
            }
        }

        // ── Feed URLs ────────────────────────────────────────────────────────
        SectionHeader("DATA SOURCES")
        SettingCard {
            FeedUrlRow("Vehicle Positions", TransitConfig.GTFS_RT_VEHICLE_POSITIONS_URL)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            FeedUrlRow("Trip Updates", TransitConfig.GTFS_RT_TRIP_UPDATES_URL)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            FeedUrlRow("Static Feed", TransitConfig.GTFS_STATIC_BASE_URL)
            Spacer(Modifier.height(4.dp))
            Text(
                "To change feeds, edit TransitConfig.kt and rebuild.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }

        // ── Tuning ───────────────────────────────────────────────────────────
        SectionHeader("TUNING")
        SettingCard {
            TuningRow("Geofence Radius",
                "${TransitConfig.STOP_GEOFENCE_RADIUS_METERS.toInt()}m")
            TuningRow("ON_BUS Threshold",
                "${(TransitConfig.ON_BUS_CONFIDENCE_THRESHOLD * 100).toInt()}%")
            TuningRow("Poll Interval",
                "${TransitConfig.GTFS_RT_POLL_INTERVAL_MS / 1000}s")
            TuningRow("Nearby Routes Radius",
                "${TransitConfig.NEARBY_ROUTES_RADIUS_METERS.toInt()}m")
            TuningRow("Max Buses on Map",
                "${TransitConfig.MAX_NEARBY_ROUTES_ON_MAP}")
        }

        // ── Debug log ────────────────────────────────────────────────────────
        if (state.transitionLog.isNotEmpty()) {
            SectionHeader("STATE TRANSITION LOG")
            SettingCard {
                state.transitionLog.takeLast(10).reversed().forEach { log ->
                    TransitionLogRow(log)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun FeedUrlRow(label: String, url: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Text(
            url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TuningRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TransitionLogRow(log: TransitionLog) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            log.fromState.take(8),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Text("→", color = MaterialTheme.colorScheme.primary)
        Text(
            log.toState.take(12),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        log.confidence?.let {
            Text(
                "${(it * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
