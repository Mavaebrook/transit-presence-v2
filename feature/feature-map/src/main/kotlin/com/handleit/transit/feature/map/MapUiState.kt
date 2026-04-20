package com.handleit.transit.feature.map

import com.handleit.transit.model.Vehicle

data class MapUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
