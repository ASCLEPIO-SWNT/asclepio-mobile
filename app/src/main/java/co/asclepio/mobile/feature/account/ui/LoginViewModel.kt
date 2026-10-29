package co.asclepio.mobile.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.core.auth.RememberedTenant
import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.httpStatus
import co.asclepio.mobile.core.network.rateLimitSeconds
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.account.data.AccountRepository
import co.asclepio.mobile.feature.account.data.LoginResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val tenant: RememberedTenant? = null,
    val form: LoginForm = LoginForm(),
    val fieldErrors: Map<LoginField, UiMessage> = emptyMap(),
    val message: UiMessage? = null,
    val submitting: Boolean = false,
    val submitBlocked: Boolean = false,
) {
    /** R22, R25. */
    val canSubmit: Boolean get() = !submitting && !submitBlocked
}

/** Inicio de sesión (R3, R16–R25). La contraseña solo vive aquí, en memoria (R32). */
class LoginViewModel(
    private val repository: AccountRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()
    private var unblockJob: Job? = null

    init {
        viewModelScope.launch {
            sessionManager.state.collect { s ->
                val tenant = (s as? SessionState.LoggedOut)?.tenant
                if (tenant != null) _state.update { it.copy(tenant = tenant) }
            }
        }
        // R16: si la entrada automática tras el alta falló, el documento llega ya escrito con su mensaje.
        viewModelScope.launch {
            repository.loginPrefill.filterNotNull().collect {
                val prefill = repository.consumeLoginPrefill() ?: return@collect
                _state.update { s ->
                    s.copy(form = LoginForm(prefill.documentType, prefill.documentNumber, ""), fieldErrors = emptyMap())
                }
                onResult(prefill.result)
            }
        }
    }

    fun updateForm(form: LoginForm) = _state.update { s ->
        if (s.submitting) return@update s
        val old = s.form
        val changed = buildSet {
            if (old.documentType != form.documentType) add(LoginField.DocumentType)
            if (old.documentNumber != form.documentNumber) add(LoginField.DocumentNumber)
            if (old.password != form.password) add(LoginField.Password)
        }
        s.copy(form = form, fieldErrors = s.fieldErrors - changed)
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        val errors = validateLogin(s.form)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, message = null) }
            return
        }
        val tenant = sessionManager.rememberedTenant() ?: return
        val form = s.form
        _state.update { it.copy(submitting = true, message = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val result = repository.login(tenant.tenantId, checkNotNull(form.documentType), form.documentNumber, form.password)
            onResult(result)
        }
    }

    private fun onResult(result: LoginResult) {
        when (result) {
            LoginResult.Success -> _state.update { it.copy(submitting = false, form = it.form.copy(password = "")) }
            LoginResult.NotPatient -> _state.update { it.copy(submitting = false, message = notPatientMessage) }
            is LoginResult.MeFailed -> _state.update { it.copy(submitting = false, message = meErrorMessage(result.error)) }
            is LoginResult.LoginFailed -> onLoginFailed(result.error)
        }
    }

    private fun onLoginFailed(error: ApiError) {
        val wait = error.rateLimitSeconds()
        _state.update {
            it.copy(
                submitting = false,
                message = loginErrorMessage(error),
                // R18: se vacía la contraseña tras un 401.
                form = if (error.httpStatus == 401) it.form.copy(password = "") else it.form,
                submitBlocked = wait != null,
            )
        }
        if (wait != null) {
            unblockJob?.cancel()
            unblockJob = viewModelScope.launch {
                delay(wait * 1_000L)
                _state.update { it.copy(submitBlocked = false) }
            }
        }
    }
}
