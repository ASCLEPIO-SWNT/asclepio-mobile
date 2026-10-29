package co.asclepio.mobile.feature.staffaccount.data

import co.asclepio.mobile.core.auth.AccessMode
import co.asclepio.mobile.core.auth.MeApi
import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall
import java.time.Instant

sealed interface StaffLoginResult {
    data object Success : StaffLoginResult
    data object InvalidPrincipal : StaffLoginResult
    data class LoginFailed(val error: ApiError) : StaffLoginResult
    data class VerificationFailed(val error: ApiError) : StaffLoginResult
}

class StaffAccountRepository(private val api: StaffAuthApi, private val meApi: MeApi, private val session: SessionManager) {
    val sessionGeneration: Long get() = session.generation

    suspend fun login(email: String, password: String, expectedGeneration: Long = session.generation): StaffLoginResult {
        val attempt = session.beginLogin(AccessMode.STAFF, expectedGeneration)
        val login = apiCall { api.login(StaffLoginRequest(email.trim(), password)) }
        session.requireCurrent(attempt.generation)
        val token = when (login) {
            is ApiResult.Failure -> return StaffLoginResult.LoginFailed(login.error)
            is ApiResult.Success -> login.value
        }
        val expiration = runCatching { Instant.parse(token.expiresAt) }.getOrNull()
            ?: return StaffLoginResult.VerificationFailed(ApiError.InvalidBody)
        if (token.tokenType != "Bearer" || token.accessToken.isBlank()) return StaffLoginResult.InvalidPrincipal
        val result = apiCall { meApi.meWithToken("Bearer ${token.accessToken}") }
        session.requireCurrent(attempt.generation)
        return when (result) {
            is ApiResult.Failure -> StaffLoginResult.VerificationFailed(result.error)
            is ApiResult.Success -> if (session.completeLogin(attempt, token.accessToken, expiration, result.value))
                StaffLoginResult.Success else StaffLoginResult.InvalidPrincipal
        }
    }
}
