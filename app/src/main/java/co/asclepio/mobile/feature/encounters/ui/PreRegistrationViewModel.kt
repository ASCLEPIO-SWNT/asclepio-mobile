package co.asclepio.mobile.feature.encounters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.rateLimitSeconds
import co.asclepio.mobile.core.tenants.PublicSiteDto
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.encounters.data.PreRegistrationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sedes de la IPS recordada (R8, R9). */
sealed interface SitesUiState {
    data object Loading : SitesUiState

    data class Loaded(val sites: List<PublicSiteDto>) : SitesUiState

    /** Fallo, IPS ausente o sin sedes (R9); `retryEnabled` es `false` mientras dura un 429 (R24). */
    data class Error(val message: UiMessage, val retryEnabled: Boolean) : SitesUiState
}

data class PreRegistrationUiState(
    val sites: SitesUiState = SitesUiState.Loading,
    val form: PreRegistrationForm = PreRegistrationForm(),
    val fieldErrors: Map<PreRegistrationField, UiMessage> = emptyMap(),
    val message: UiMessage? = null,
    val submitting: Boolean = false,
    val submitBlocked: Boolean = false,
    /** R13: acción "Ver mi estado". */
    val showViewStatus: Boolean = false,
    /** R12: el POST respondió 201; la pantalla vuelve al inicio. */
    val created: Boolean = false,
) {
    /** R11: longitud actual del motivo en unidades UTF-16 (`String.length`). */
    val complaintLength: Int get() = form.chiefComplaintText.length

    /** R9, R24, R25. */
    val canSubmit: Boolean get() = sites is SitesUiState.Loaded && !submitting && !submitBlocked && !created
}

/**
 * Formulario de pre-registro (R8–R15, R24, R25). La sede recordada solo se usa para preseleccionar; elegir otra no la
 * cambia (PA-M02-5). El motivo solo vive aquí, en memoria (R27).
 */
class PreRegistrationViewModel(
    private val repository: PreRegistrationRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(PreRegistrationUiState())
    val state: StateFlow<PreRegistrationUiState> = _state.asStateFlow()

    init {
        loadSites()
    }

    fun retrySites() {
        val sites = _state.value.sites
        if (sites is SitesUiState.Error && sites.retryEnabled) loadSites()
    }

    private fun loadSites() {
        _state.update { it.copy(sites = SitesUiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.tenants()) {
                is ApiResult.Success -> {
                    val remembered = sessionManager.rememberedTenant()
                    val sites = result.value.firstOrNull { it.id == remembered?.tenantId }?.sites.orEmpty()
                    _state.update { s ->
                        if (sites.isEmpty()) {
                            s.copy(sites = SitesUiState.Error(UiMessage(R.string.error_no_sites), retryEnabled = true))
                        } else {
                            val preselected = remembered?.siteId?.takeIf { id -> sites.any { it.id == id } }
                            s.copy(sites = SitesUiState.Loaded(sites), form = s.form.copy(siteId = preselected))
                        }
                    }
                }
                is ApiResult.Failure -> {
                    val wait = result.error.rateLimitSeconds()
                    val error = SitesUiState.Error(sitesErrorMessage(result.error), retryEnabled = wait == null)
                    _state.update { it.copy(sites = error) }
                    if (wait != null) {
                        delay(wait * 1_000L)
                        _state.update { if (it.sites == error) it.copy(sites = error.copy(retryEnabled = true)) else it }
                    }
                }
            }
        }
    }

    fun selectSite(siteId: String) = _state.update { s ->
        val sites = s.sites as? SitesUiState.Loaded ?: return@update s
        if (s.submitting || sites.sites.none { it.id == siteId }) return@update s
        s.copy(form = s.form.copy(siteId = siteId), fieldErrors = s.fieldErrors - PreRegistrationField.Site)
    }

    fun updateComplaint(text: String) = _state.update { s ->
        if (s.submitting) return@update s
        s.copy(form = s.form.copy(chiefComplaintText = text), fieldErrors = s.fieldErrors - PreRegistrationField.ChiefComplaint)
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        val errors = validatePreRegistration(s.form)
        if (errors.isNotEmpty()) {
            _state.value = s.copy(fieldErrors = errors, message = null)
            return
        }
        val request = s.form.toRequest()
        _state.value = s.copy(submitting = true, fieldErrors = emptyMap(), message = null, showViewStatus = false)
        viewModelScope.launch {
            when (val result = repository.create(request)) {
                // R12: se descarta el motivo escrito; el inicio muestra el pre-registro de la respuesta.
                is ApiResult.Success -> _state.update { it.copy(submitting = false, form = PreRegistrationForm(), created = true) }
                is ApiResult.Failure -> onCreateFailed(result.error)
            }
        }
    }

    private suspend fun onCreateFailed(error: ApiError) {
        val mapped = preRegistrationErrorMessage(error)
        val wait = error.rateLimitSeconds()
        _state.update {
            it.copy(
                submitting = false,
                fieldErrors = mapped.fieldErrors,
                message = mapped.message,
                showViewStatus = mapped.showViewStatus,
                submitBlocked = wait != null,
                form = if (mapped.siteUnavailable) it.form.copy(siteId = null) else it.form,
            )
        }
        if (mapped.siteUnavailable) loadSites()
        if (wait != null) {
            delay(wait * 1_000L)
            _state.update { it.copy(submitBlocked = false) }
        }
    }

    fun onCreatedHandled() = _state.update { it.copy(created = false) }
}
