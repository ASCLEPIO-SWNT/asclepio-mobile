package co.asclepio.mobile.feature.status.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.feature.status.data.HealthApi
import co.asclepio.mobile.core.ui.formatBogota
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StatusUiState {
    data object Loading : StatusUiState
    data class Success(val serverTimeBogota: String) : StatusUiState
    data class Error(val message: String) : StatusUiState
}

/** Consulta /health y lo traduce a estado de UI. Separado del ViewModel para probarlo en la JVM. */
suspend fun loadStatus(api: HealthApi): StatusUiState =
    try {
        val health = api.health()
        if (health.status == "ok") StatusUiState.Success(formatBogota(health.time))
        else StatusUiState.Error("estado inesperado")
    } catch (e: retrofit2.HttpException) {
        StatusUiState.Error("HTTP ${e.code()}")
    } catch (e: java.io.IOException) {
        StatusUiState.Error("sin conexión")
    }

class StatusViewModel(private val api: HealthApi) : ViewModel() {
    private val _state = MutableStateFlow<StatusUiState>(StatusUiState.Loading)
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = StatusUiState.Loading
        viewModelScope.launch { _state.value = loadStatus(api) }
    }
}
