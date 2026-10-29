package co.asclepio.mobile.core.auth

import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall
import co.asclepio.mobile.core.network.httpStatus

/** Credencial de la operación, capturada antes de suspender. Nunca cambia al iniciar otra sesión. */
class AuthHandle internal constructor(val generation: Long, val authorization: String, val principal: VerifiedPrincipal) {
    override fun toString() = "AuthHandle(generation=$generation, authorization=***, principal=***)"
}

suspend fun <T> SessionManager.authenticatedCall(scope: AccessMode, block: suspend (AuthHandle) -> T): ApiResult<T> {
    val handle = capture(scope)
    val result = apiCall { block(handle) }
    finish(handle, result is ApiResult.Failure && result.error.httpStatus == 401)
    return result
}

/** F-02 conserva su frontera paciente, ahora con Bearer y generación inmutables. */
suspend fun <T> SessionManager.patientCall(block: suspend (String) -> T): ApiResult<T> =
    authenticatedCall(AccessMode.PATIENT) { block(it.authorization) }
