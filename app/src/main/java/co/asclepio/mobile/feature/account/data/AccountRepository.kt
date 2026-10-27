package co.asclepio.mobile.feature.account.data

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

    suspend fun consent(): ApiResult<ConsentDto> = apiCall { publicApi.consent() }

    suspend fun createAccount(request: CreatePatientAccountRequest): ApiResult<PatientAccountCreatedDto> =
        apiCall { authApi.createAccount(request) }

    /**
     * Login + `GET /me` con la cabecera explícita (R17, R19–R21). Solo si `/me` es un paciente de la misma IPS
     * se guarda la sesión; en otro caso el token se descarta sin guardarse.
     */
    suspend fun login(tenantId: String, documentType: DocumentType, documentNumber: String, password: String): LoginResult {
        val request = PatientLoginRequest(tenantId, documentType, documentNumber.trim(), password)
        val token = when (val result = apiCall { authApi.login(request) }) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return LoginResult.LoginFailed(result.error)
        }
        val expiresAt = runCatching { Instant.parse(token.expiresAt) }.getOrNull()
            ?: return LoginResult.MeFailed(ApiError.InvalidBody)
        return when (val me = apiCall { meApi.meWithToken("Bearer ${token.accessToken}") }) {
            is ApiResult.Failure -> LoginResult.MeFailed(me.error)
            is ApiResult.Success -> if (me.value.isPatient && me.value.tenantId == tenantId) {
                sessionManager.startSession(token.accessToken, expiresAt)
                LoginResult.Success
            } else {
                LoginResult.NotPatient
            }
        }
    }

    fun setLoginPrefill(prefill: LoginPrefill) {
        _loginPrefill.value = prefill
    }

    fun consumeLoginPrefill(): LoginPrefill? = _loginPrefill.value.also { _loginPrefill.value = null }
}
