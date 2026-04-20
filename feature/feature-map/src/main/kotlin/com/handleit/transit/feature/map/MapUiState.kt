data class MapUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
