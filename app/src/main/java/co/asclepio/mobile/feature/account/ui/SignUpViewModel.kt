package co.asclepio.mobile.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.httpStatus
import co.asclepio.mobile.core.network.rateLimitSeconds
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.account.data.AccountRepository
import co.asclepio.mobile.feature.account.data.LoginPrefill
import co.asclepio.mobile.feature.account.data.LoginResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

sealed interface ConsentUiState {
    data object Loading : ConsentUiState

    /** `version` es la versión vigente del consentimiento (glosario de la spec). */
    data class Loaded(val version: String, val text: String) : ConsentUiState

    data class Error(val message: UiMessage, val retryEnabled: Boolean) : ConsentUiState
}

data class SignUpUiState(
    val consent: ConsentUiState = ConsentUiState.Loading,
    val form: SignUpForm = SignUpForm(),
    val fieldErrors: Map<SignUpField, UiMessage> = emptyMap(),
    val message: UiMessage? = null,
    val submitting: Boolean = false,
    val submitBlocked: Boolean = false,
    val reloadingConsent: Boolean = false,
    val showGoToLogin: Boolean = false,
    val navigateToLogin: Boolean = false,
) {
    /** R7, R9, R22, R25. */
    val canSubmit: Boolean
        get() = form.consentAccepted && consent is ConsentUiState.Loaded && !submitting && !submitBlocked && !reloadingConsent
}

/** Alta del paciente (R6–R16, R22, R23, R25). La contraseña solo vive aquí, en memoria (R32): no se guarda en el estado guardado del sistema. */
class SignUpViewModel(
    private val repository: AccountRepository,
    private val sessionManager: SessionManager,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    init {
        loadConsent()
    }

    fun retryConsent() {
        val consent = _state.value.consent
        if (consent is ConsentUiState.Error && consent.retryEnabled) loadConsent()
    }

    private fun loadConsent() {
        _state.update { it.copy(consent = ConsentUiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.consent()) {
                is ApiResult.Success -> _state.update {
                    it.copy(consent = ConsentUiState.Loaded(result.value.version, result.value.text))
                }
                is ApiResult.Failure -> {
                    val wait = result.error.rateLimitSeconds()
                    val error = ConsentUiState.Error(consentErrorMessage(result.error), retryEnabled = wait == null)
                    _state.update { it.copy(consent = error) }
                    if (wait != null) {
                        delay(wait * 1_000L)
                        _state.update { if (it.consent == error) it.copy(consent = error.copy(retryEnabled = true)) else it }
                    }
                }
            }
        }
    }

    /** Cambia el formulario y quita el error de los campos que cambiaron. */
    fun updateForm(form: SignUpForm) = _state.update { s ->
        if (s.submitting) return@update s
        val old = s.form
        val changed = buildSet {
            if (old.documentType != form.documentType) add(SignUpField.DocumentType)
            if (old.documentNumber != form.documentNumber) add(SignUpField.DocumentNumber)
            if (old.fullName != form.fullName) add(SignUpField.FullName)
            if (old.birthDate != form.birthDate) add(SignUpField.BirthDate)
            if (old.sex != form.sex) add(SignUpField.Sex)
            if (old.password != form.password) add(SignUpField.Password)
            if (old.passwordConfirmation != form.passwordConfirmation) add(SignUpField.PasswordConfirmation)
        }
        s.copy(form = form, fieldErrors = s.fieldErrors - changed)
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        val consent = s.consent as ConsentUiState.Loaded
        val errors = validateSignUp(s.form, clock)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, message = null) }
            return
        }
        val tenant = sessionManager.rememberedTenant() ?: return
        val form = s.form
        val expectedGeneration = sessionManager.generation
        _state.update { it.copy(submitting = true, message = null, fieldErrors = emptyMap(), showGoToLogin = false) }
        viewModelScope.launch {
            val result = repository.createAccount(form.toRequest(tenant.tenantId, consent.version))
            sessionManager.requireCurrent(expectedGeneration)
            when (result) {
                is ApiResult.Success -> autoLogin(tenant.tenantId, form)
                is ApiResult.Failure -> onCreateFailed(result.error, consent.version)
            }
        }
    }

    /** R16: entra con los mismos datos; si falla, va al login con el documento ya escrito. */
    private suspend fun autoLogin(tenantId: String, form: SignUpForm) {
        val documentType = checkNotNull(form.documentType)
        val result = repository.login(tenantId, documentType, form.documentNumber, form.password)
        if (result != LoginResult.Success) {
            repository.setLoginPrefill(LoginPrefill(documentType, form.documentNumber.trim(), result))
            _state.update { it.copy(submitting = false, navigateToLogin = true) }
        } else {
            _state.update { it.copy(submitting = false) }
        }
    }

    private suspend fun onCreateFailed(error: ApiError, sentVersion: String) {
        val mapped = signUpErrorMessage(error)
        val wait = error.rateLimitSeconds()
        val reload = error.httpStatus == 422
        _state.update {
            it.copy(
                submitting = false,
                fieldErrors = mapped.fieldErrors,
                message = mapped.message,
                showGoToLogin = error.httpStatus == 409,
                submitBlocked = wait != null,
                reloadingConsent = reload,
            )
        }
        if (reload) reloadConsentAfter422(sentVersion)
        if (wait != null) {
            delay(wait * 1_000L)
            _state.update { it.copy(submitBlocked = false) }
        }
    }

    /** R15: si la versión cambió, reemplaza el texto, actualiza la versión vigente y desmarca la casilla. */
    private suspend fun reloadConsentAfter422(sentVersion: String) {
        val result = repository.consent()
        _state.update { s ->
            val fresh = (result as? ApiResult.Success)?.value
            if (fresh != null && fresh.version != sentVersion) {
                s.copy(
                    consent = ConsentUiState.Loaded(fresh.version, fresh.text),
                    form = s.form.copy(consentAccepted = false),
                    reloadingConsent = false,
                )
            } else {
                s.copy(reloadingConsent = false)
            }
        }
    }

    fun onNavigatedToLogin() = _state.update { it.copy(navigateToLogin = false) }
}
