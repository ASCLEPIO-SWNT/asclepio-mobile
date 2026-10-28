package co.asclepio.mobile.feature.encounters.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.R
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.rateLimitSeconds
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.core.ui.formatBogota
import co.asclepio.mobile.feature.encounters.data.PatientPreRegistrationDto
import co.asclepio.mobile.feature.encounters.data.PreRegistrationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del último pre-registro en el inicio (R17–R22). */
sealed interface PreRegistrationStatusUiState {
    /** R19, R20: si se ofrece "Nuevo pre-registro". */
    val canCreate: Boolean

    data object Loading : PreRegistrationStatusUiState {
        override val canCreate = false
    }

    /** R18: el paciente no ha enviado ningún pre-registro. */
    data object Empty : PreRegistrationStatusUiState {
        override val canCreate = true
    }

    /**
     * R17, R21, R22: solo la sede, el texto del estado y las horas de la API ya formateadas en America/Bogota
     * (`expiresAt` solo en `PENDING`). Sin nivel, sin id de la atención y sin hora de llegada (D6).
     */
    data class Success(
        val siteName: String,
        @param:StringRes val statusRes: Int,
        val sentAt: String,
        val expiresAt: String?,
        override val canCreate: Boolean,
    ) : PreRegistrationStatusUiState

    /** R20: `refreshEnabled` es `false` mientras dura un 429 (R24). */
    data class Error(val message: UiMessage, val refreshEnabled: Boolean) : PreRegistrationStatusUiState {
        override val canCreate = false
    }
}

/** Lo que el inicio muestra de un pre-registro (R17). Lanza si una hora no es ISO-8601. */
fun toStatusUi(dto: PatientPreRegistrationDto): PreRegistrationStatusUiState.Success =
    PreRegistrationStatusUiState.Success(
        siteName = dto.siteName,
        statusRes = statusText(dto),
        sentAt = formatBogota(dto.preRegisteredAt),
        expiresAt = if (dto.status == "PENDING") formatBogota(dto.expiresAt) else null,
        canCreate = !isInProgress(dto),
    )

/**
 * Estado del último pre-registro (R12, R17–R24). Se pide al crearse (al mostrarse el inicio), al volver a primer
 * plano y con "Actualizar"; sin sondeo periódico (PA-M02-4). Tras un 201 muestra el pre-registro de la respuesta.
 */
class PreRegistrationStatusViewModel(private val repository: PreRegistrationRepository) : ViewModel() {
    private val _state = MutableStateFlow<PreRegistrationStatusUiState>(PreRegistrationStatusUiState.Loading)
    val state: StateFlow<PreRegistrationStatusUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch { repository.created.collect { _state.value = show(it) } }
        refresh()
    }

    /** R17, R20. Sin efecto si ya hay una petición en curso o mientras dura un 429 (la espera es parte del trabajo, R24). */
    fun refresh() {
        if (job?.isActive == true) return
        _state.value = PreRegistrationStatusUiState.Loading
        job = viewModelScope.launch {
            when (val result = repository.latest()) {
                is ApiResult.Success -> _state.value = result.value?.let(::show) ?: PreRegistrationStatusUiState.Empty
                is ApiResult.Failure -> {
                    val wait = result.error.rateLimitSeconds()
                    val error = PreRegistrationStatusUiState.Error(statusErrorMessage(result.error), refreshEnabled = wait == null)
                    _state.value = error
                    if (wait != null) {
                        delay(wait * 1_000L)
                        _state.update { if (it == error) error.copy(refreshEnabled = true) else it }
                    }
                }
            }
        }
    }

    private fun show(dto: PatientPreRegistrationDto): PreRegistrationStatusUiState =
        runCatching { toStatusUi(dto) }.getOrElse {
            PreRegistrationStatusUiState.Error(UiMessage(R.string.error_general), refreshEnabled = true)
        }
}
