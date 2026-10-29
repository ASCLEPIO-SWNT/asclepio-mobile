package co.asclepio.mobile.feature.account.data

import co.asclepio.mobile.core.auth.AccessMode
import co.asclepio.mobile.core.auth.MeApi
import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

sealed interface LoginResult {
    /** Sesión guardada (R19). */
    data object Success : LoginResult

    /** El login falló (401, 429, red…). */
    data class LoginFailed(val error: ApiError) : LoginResult

    /** `/me` no es un paciente de la IPS recordada: el token se descarta (R20). */
    data object NotPatient : LoginResult

    /** `/me` falló: el token se descarta (R21). */
    data class MeFailed(val error: ApiError) : LoginResult
}

/** Documento ya escrito para el login cuando la entrada automática tras el alta falla (R16). Solo en memoria. */
data class LoginPrefill(val documentType: DocumentType, val documentNumber: String, val result: LoginResult) {
    override fun toString(): String = "LoginPrefill(documentType=$documentType, documentNumber=***, result=$result)"
}

class AccountRepository(
    private val publicApi: PublicApi,
    private val authApi: PatientAuthApi,
    private val meApi: MeApi,
    private val sessionManager: SessionManager,
) {
    private val _loginPrefill = MutableStateFlow<LoginPrefill?>(null)
    val loginPrefill: StateFlow<LoginPrefill?> = _loginPrefill.asStateFlow()

    init { sessionManager.onInvalidated { _loginPrefill.value = null } }

    suspend fun consent(): ApiResult<ConsentDto> = apiCall { publicApi.consent() }

    suspend fun createAccount(request: CreatePatientAccountRequest): ApiResult<PatientAccountCreatedDto> =
        apiCall { authApi.createAccount(request) }

    /**
     * Login + `GET /me` con la cabecera explícita (R17, R19–R21). Solo si `/me` es un paciente de la misma IPS
     * se guarda la sesión; en otro caso el token se descarta sin guardarse.
     */
    suspend fun login(tenantId: String, documentType: DocumentType, documentNumber: String, password: String, expectedGeneration: Long = sessionManager.generation): LoginResult {
        val attempt = sessionManager.beginLogin(AccessMode.PATIENT, expectedGeneration)
        val request = PatientLoginRequest(tenantId, documentType, documentNumber.trim(), password)
        val token = when (val result = apiCall { authApi.login(request) }) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> { sessionManager.requireCurrent(attempt.generation); return LoginResult.LoginFailed(result.error) }
        }
        sessionManager.requireCurrent(attempt.generation)
        if (token.tokenType != "Bearer" || token.accessToken.isBlank()) return LoginResult.NotPatient
        val expiresAt = runCatching { Instant.parse(token.expiresAt) }.getOrNull()
            ?: return LoginResult.MeFailed(ApiError.InvalidBody)
        val me = apiCall { meApi.meWithToken("Bearer ${token.accessToken}") }
        sessionManager.requireCurrent(attempt.generation)
        return when (me) {
            is ApiResult.Failure -> LoginResult.MeFailed(me.error)
            is ApiResult.Success -> if (sessionManager.completeLogin(attempt, token.accessToken, expiresAt, me.value)) {
                LoginResult.Success
            } else LoginResult.NotPatient
        }
    }

    fun setLoginPrefill(prefill: LoginPrefill) {
        _loginPrefill.value = prefill
    }

    fun consumeLoginPrefill(): LoginPrefill? = _loginPrefill.value.also { _loginPrefill.value = null }
}
