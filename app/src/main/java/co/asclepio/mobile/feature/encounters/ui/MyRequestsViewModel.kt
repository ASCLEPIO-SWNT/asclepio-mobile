package co.asclepio.mobile.feature.encounters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.R
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.rateLimitSeconds
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.encounters.data.PatientPreRegistrationDto
import co.asclepio.mobile.feature.encounters.data.PreRegistrationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MyRequestsUiState {
    data object Loading : MyRequestsUiState
    data object Empty : MyRequestsUiState

    /** Solo los campos de presentación permitidos; no DTOs, ids ni eventos clínicos. */
    data class Success(val items: List<PreRegistrationStatusUiState.Success>) : MyRequestsUiState {
        override fun toString() = "Success(items=***)"
    }

    data class Error(val message: UiMessage, val refreshEnabled: Boolean) : MyRequestsUiState
}

/** Lista propia en RAM. La raíz llama refresh al entrar/foreground; no hay sondeo ni caché persistida. */
class MyRequestsViewModel(private val repository: PreRegistrationRepository) : ViewModel() {
    private val _state = MutableStateFlow<MyRequestsUiState>(MyRequestsUiState.Loading)
    val state: StateFlow<MyRequestsUiState> = _state.asStateFlow()
    private var job: Job? = null

    init { refresh() }

    /** Entrada y foreground coalescen con la misma petición; Retry-After bloquea solo el envío manual. */
    fun refresh() {
        if (job?.isActive == true) return
        _state.value = MyRequestsUiState.Loading
        job = viewModelScope.launch {
            when (val result = repository.list()) {
                is ApiResult.Success -> _state.value = show(result.value)
                is ApiResult.Failure -> {
                    val wait = result.error.rateLimitSeconds()
                    val error = MyRequestsUiState.Error(statusErrorMessage(result.error), refreshEnabled = wait == null)
                    _state.value = error
                    if (wait != null) {
                        delay(wait * 1_000L)
                        _state.update { if (it == error) error.copy(refreshEnabled = true) else it }
                    }
                }
            }
        }
    }

    private fun show(items: List<PatientPreRegistrationDto>): MyRequestsUiState =
        if (items.isEmpty()) MyRequestsUiState.Empty else runCatching {
            MyRequestsUiState.Success(items.map(::toStatusUi))
        }.getOrElse {
            MyRequestsUiState.Error(UiMessage(R.string.error_general), refreshEnabled = true)
        }
}
