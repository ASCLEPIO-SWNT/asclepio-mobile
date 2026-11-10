package co.asclepio.mobile.core.auth

import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall
import co.asclepio.mobile.core.network.httpStatus

/** Credencial de la operación, capturada antes de suspender. Nunca cambia al iniciar otra sesión. */
class AuthHandle internal constructor(val generation: Long, val authorization: String) {
    override fun toString() = "AuthHandle(generation=$generation, authorization=***)"
}

/** Frontera de las llamadas del paciente (F-02), con Bearer y generación inmutables por operación. */
suspend fun <T> SessionManager.patientCall(block: suspend (String) -> T): ApiResult<T> {
    val origin = state.value as? SessionState.Authenticated ?: throw SessionInvalidated()
    val handle = capture(origin.generation)
    val result = apiCall { block(handle.authorization) }
    finish(handle, result is ApiResult.Failure && result.error.httpStatus == 401)
    return result
}
