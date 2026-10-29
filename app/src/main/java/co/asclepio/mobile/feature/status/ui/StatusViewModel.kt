package co.asclepio.mobile.feature.status.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.feature.status.data.HealthApi
import co.asclepio.mobile.core.ui.formatBogota
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StatusUiState {
    data object Loading : StatusUiState
    data class Success(val serverTimeBogota: String) : StatusUiState
    data class Error(val message: UiMessage) : StatusUiState
}

/** Consulta /health y lo traduce a estado de UI. Separado del ViewModel para probarlo en la JVM. */
suspend fun loadStatus(api: HealthApi): StatusUiState =
    try {
        val health = api.health()
        if (health.status == "ok") StatusUiState.Success(formatBogota(health.time))
        else StatusUiState.Error(UiMessage(R.string.error_general))
    } catch (e: CancellationException) {
        throw e
    } catch (e: retrofit2.HttpException) {
        StatusUiState.Error(UiMessage(R.string.status_http_error, listOf(e.code())))
    } catch (e: java.io.IOException) {
        StatusUiState.Error(UiMessage(R.string.error_network))
    } catch (_: Exception) {
        StatusUiState.Error(UiMessage(R.string.error_general))
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
