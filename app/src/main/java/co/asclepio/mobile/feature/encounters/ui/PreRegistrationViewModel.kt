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
import co.asclepio.mobile.core.voice.DictationError
import co.asclepio.mobile.core.voice.DictationUiState
import co.asclepio.mobile.core.voice.NoDictation
import co.asclepio.mobile.core.voice.OnDeviceAvailability
import co.asclepio.mobile.core.voice.OnDeviceRecognizer
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
    /** Dictado en el dispositivo (F-08). Nunca contiene texto reconocido: ese texto solo pasa a [form]. */
    val dictation: DictationUiState = DictationUiState.Checking,
    /** Aviso local del dictado (permiso, sin resultado, error); nunca bloquea el envío por texto (R4, R7). */
    val dictationMessage: UiMessage? = null,
) {
    /** R11: longitud actual del motivo en unidades UTF-16 (`String.length`). */
    val complaintLength: Int get() = form.chiefComplaintText.length

    /** R9, R24, R25; y R5 de F-08: no se envía mientras se escucha o se espera el resultado. */
    val canSubmit: Boolean get() = sites is SitesUiState.Loaded && !submitting && !submitBlocked && !created && !dictationActive

    private val dictationActive: Boolean
        get() = dictation == DictationUiState.Listening || dictation == DictationUiState.Processing
}

/**
 * Formulario de pre-registro (R8–R15, R24, R25). La sede recordada solo se usa para preseleccionar; elegir otra no la
 * cambia (PA-M02-5). El motivo solo vive aquí, en memoria (R27).
 *
 * F-08 (D3): también es dueño del estado del dictado y del origen del motivo. [recognizer] es el reconocedor local
 * y [beforeListen] detiene la guía TTS antes de escuchar (D8).
 */
class PreRegistrationViewModel(
    private val repository: PreRegistrationRepository,
    private val sessionManager: SessionManager,
    private val recognizer: OnDeviceRecognizer = NoDictation,
    private val beforeListen: () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(PreRegistrationUiState())
    val state: StateFlow<PreRegistrationUiState> = _state.asStateFlow()

    /** Variante de español del reconocimiento local (R1); solo se conoce si está disponible. */
    private var languageTag: String? = null

    /** D4: cada escucha tiene su número; los callbacks de una escucha anterior o cancelada se ignoran (R10, R11). */
    private var listenGeneration = 0L

    init {
        loadSites()
        checkDictation()
    }

    /** R1, R16: sin abrir el micrófono ni pedir permisos. */
    private fun checkDictation() = recognizer.checkAvailability { availability ->
        val available = availability as? OnDeviceAvailability.Available
        languageTag = available?.languageTag
        _state.update {
            if (it.dictation != DictationUiState.Checking) it
            else it.copy(dictation = if (available != null) DictationUiState.Idle else DictationUiState.Unavailable)
        }
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

    /**
     * "Dictar" con el permiso concedido (R3): solo desde `Idle` y con el formulario abierto; una sola escucha (R5).
     * Detiene la guía antes de abrir el micrófono (R13).
     */
    fun startDictation() {
        val s = _state.value
        val tag = languageTag
        if (s.dictation != DictationUiState.Idle || tag == null || s.submitting || s.created) return
        beforeListen()
        val generation = ++listenGeneration
        _state.update { it.copy(dictation = DictationUiState.Listening, dictationMessage = null) }
        recognizer.start(tag, onFinal = { onDictationResult(generation, it) }, onError = { onDictationError(generation, it) })
    }

    /** "Detener" (R5): deja de escuchar y espera el resultado de lo ya dicho. */
    fun stopDictation() {
        if (_state.value.dictation != DictationUiState.Listening) return
        recognizer.stop()
        _state.update { it.copy(dictation = DictationUiState.Processing) }
    }

    /**
     * "Cancelar", segundo plano, salida de la pantalla, rotación, cambio de sesión o la guía empieza a hablar
     * (R10, R11, R13): libera el reconocedor y descarta lo que llegue después. El borrador no cambia.
     */
    fun cancelDictation() {
        if (!isListening(listenGeneration)) return
        listenGeneration++
        recognizer.cancel()
        _state.update { it.copy(dictation = DictationUiState.Idle, dictationMessage = UiMessage(R.string.dictation_cancelled)) }
    }

    private fun isListening(generation: Long): Boolean {
        val dictation = _state.value.dictation
        return generation == listenGeneration && (dictation == DictationUiState.Listening || dictation == DictationUiState.Processing)
    }

    /** R6, R8: añade el resultado aceptado una sola vez al final del motivo; no lo envía ni lo trunca. */
    private fun onDictationResult(generation: Long, text: String) {
        if (!isListening(generation)) return
        listenGeneration++
        val accepted = text.trim()
        _state.update {
            if (accepted.isEmpty()) {
                it.copy(dictation = DictationUiState.Idle, dictationMessage = UiMessage(R.string.dictation_no_match))
            } else {
                it.copy(
                    form = it.form.withDictation(accepted),
                    fieldErrors = it.fieldErrors - PreRegistrationField.ChiefComplaint,
                    dictation = DictationUiState.Idle,
                    dictationMessage = null,
                )
            }
        }
    }

    /** R4, R7, R16: el motivo y su origen no cambian; un error de idioma deja el dictado no disponible. */
    private fun onDictationError(generation: Long, error: DictationError) {
        if (!isListening(generation)) return
        listenGeneration++
        if (error == DictationError.LanguageUnavailable) {
            languageTag = null
            _state.update { it.copy(dictation = DictationUiState.Unavailable, dictationMessage = null) }
            return
        }
        val message = when (error) {
            DictationError.Permission -> R.string.dictation_permission_denied
            DictationError.NoMatch -> R.string.dictation_no_match
            DictationError.Busy -> R.string.dictation_busy
            DictationError.LanguageUnavailable, DictationError.Other -> R.string.dictation_error
        }
        _state.update { it.copy(dictation = DictationUiState.Idle, dictationMessage = UiMessage(message)) }
    }

    /** R4: permiso denegado al pulsar "Dictar"; el motivo, su origen y el envío por texto no cambian. */
    fun onDictationPermissionDenied() =
        _state.update { it.copy(dictationMessage = UiMessage(R.string.dictation_permission_denied)) }

    fun selectSite(siteId: String) = _state.update { s ->
        val sites = s.sites as? SitesUiState.Loaded ?: return@update s
        if (s.submitting || sites.sites.none { it.id == siteId }) return@update s
        s.copy(form = s.form.copy(siteId = siteId), fieldErrors = s.fieldErrors - PreRegistrationField.Site)
    }

    fun updateComplaint(text: String) = _state.update { s ->
        if (s.submitting) return@update s
        s.copy(form = s.form.withTypedText(text), fieldErrors = s.fieldErrors - PreRegistrationField.ChiefComplaint)
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

    /** R10: al destruir el formulario se libera el reconocedor y se descarta cualquier resultado posterior. */
    override fun onCleared() {
        listenGeneration++
        recognizer.release()
    }
}
