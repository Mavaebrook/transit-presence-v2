package com.handleit.transit.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handleit.transit.common.MapProvider
import com.handleit.transit.common.TransitConfig
import com.handleit.transit.fsm.TransitionLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val mapProvider: MapProvider = TransitConfig.MAP_PROVIDER_DEFAULT,
    val mockModeEnabled: Boolean = false,
    val transitionLog: List<TransitionLog> = emptyList(),
)

sealed class SettingsIntent {
    object ToggleMapProvider : SettingsIntent()
    object ToggleMockMode : SettingsIntent()
    data class UpdateTransitionLog(val log: List<TransitionLog>) : SettingsIntent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun dispatch(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.ToggleMapProvider ->
                _state.update {
                    it.copy(mapProvider = if (it.mapProvider == MapProvider.GOOGLE)
                        MapProvider.OSM else MapProvider.GOOGLE)
                }
            is SettingsIntent.ToggleMockMode ->
                _state.update { it.copy(mockModeEnabled = !it.mockModeEnabled) }
            is SettingsIntent.UpdateTransitionLog ->
                _state.update { it.copy(transitionLog = intent.log) }
        }
    }
}
