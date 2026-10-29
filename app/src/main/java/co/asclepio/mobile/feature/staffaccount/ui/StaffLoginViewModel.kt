package co.asclepio.mobile.feature.staffaccount.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.R
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.commonMessage
import co.asclepio.mobile.core.network.httpStatus
import co.asclepio.mobile.core.network.rateLimitSeconds
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.staffaccount.data.StaffAccountRepository
import co.asclepio.mobile.feature.staffaccount.data.StaffLoginResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StaffLoginField { Email, Password }

data class StaffLoginForm(val email: String = "", val password: String = "") {
    override fun toString() = "StaffLoginForm(email=***, password=***)"
}

/** Validación de login, no de alta: no añade una longitud mínima a la contraseña. */
fun validateStaffLogin(form: StaffLoginForm): Map<StaffLoginField, UiMessage> = buildMap {
    val email = form.email.trim()
    if (email.isEmpty()) put(StaffLoginField.Email, UiMessage(R.string.error_required)) else
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) put(StaffLoginField.Email, UiMessage(R.string.staff_error_email))
    when {
        form.password.isEmpty() -> put(StaffLoginField.Password, UiMessage(R.string.error_required))
        form.password.length > 128 -> put(StaffLoginField.Password, UiMessage(R.string.error_max_length, listOf(128)))
    }
}

data class StaffLoginUiState(
    val form: StaffLoginForm = StaffLoginForm(),
    val fieldErrors: Map<StaffLoginField, UiMessage> = emptyMap(),
    val message: UiMessage? = null,
    val submitting: Boolean = false,
    val submitBlocked: Boolean = false,
    val validationAttempt: Long = 0,
) {
    val canSubmit: Boolean get() = !submitting && !submitBlocked
}

/** Credenciales únicamente en VM, sin estado guardado ni persistencia. */
class StaffLoginViewModel(private val repository: StaffAccountRepository) : ViewModel() {
    private var loginGeneration = repository.sessionGeneration
    private val _state = MutableStateFlow(StaffLoginUiState())
    val state: StateFlow<StaffLoginUiState> = _state.asStateFlow()
    private var unblockJob: Job? = null

    fun updateForm(form: StaffLoginForm) = _state.update { current ->
        if (current.submitting) current else current.copy(
            form = form,
            fieldErrors = current.fieldErrors - buildSet {
                if (current.form.email != form.email) add(StaffLoginField.Email)
                if (current.form.password != form.password) add(StaffLoginField.Password)
            },
        )
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val errors = validateStaffLogin(current.form)
        if (errors.isNotEmpty()) { _state.update { it.copy(fieldErrors = errors, message = null, validationAttempt = it.validationAttempt + 1) }; return }
        val expectedGeneration = loginGeneration
        _state.update { it.copy(submitting = true, fieldErrors = emptyMap(), message = null) }
        viewModelScope.launch {
            val result = repository.login(current.form.email, current.form.password, expectedGeneration)
            loginGeneration = repository.sessionGeneration
            when (result) {
                StaffLoginResult.Success -> _state.update { it.copy(submitting = false, form = it.form.copy(password = "")) }
                StaffLoginResult.InvalidPrincipal -> _state.update { it.copy(submitting = false, message = UiMessage(R.string.staff_error_access)) }
                is StaffLoginResult.LoginFailed -> failed(result.error, fromLogin = true)
                is StaffLoginResult.VerificationFailed -> failed(result.error, fromLogin = false)
            }
        }
    }

    private fun failed(error: ApiError, fromLogin: Boolean) {
        val wait = error.rateLimitSeconds()
        val fields = if (fromLogin && error.httpStatus == 400 && error is ApiError.Http) {
            error.problem.errors.mapNotNull {
                when (it.field) { "email" -> StaffLoginField.Email; "password" -> StaffLoginField.Password; else -> null }
            }.associateWith { UiMessage(R.string.staff_error_field) }
        } else emptyMap()
        _state.update {
            it.copy(
                submitting = false,
                message = if (fromLogin && error.httpStatus == 401) UiMessage(R.string.staff_error_credentials) else error.commonMessage(),
                fieldErrors = fields,
                validationAttempt = if (fields.isNotEmpty()) it.validationAttempt + 1 else it.validationAttempt,
                form = if (fromLogin && error.httpStatus == 401) it.form.copy(password = "") else it.form,
                submitBlocked = wait != null,
            )
        }
        if (wait != null) {
            unblockJob?.cancel()
            unblockJob = viewModelScope.launch { delay(wait * 1_000L); _state.update { it.copy(submitBlocked = false) } }
        }
    }
}
