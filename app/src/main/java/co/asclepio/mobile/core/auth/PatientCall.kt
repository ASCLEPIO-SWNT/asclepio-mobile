package co.asclepio.mobile.core.auth

import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall
import co.asclepio.mobile.core.network.httpStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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

/**
 * Frontera de un stream del paciente (F-10): como [patientCall], el Bearer y la generación se capturan al abrirlo. Al
 * llegar un evento terminal se cierra la operación: un 401 vigente cierra solo su sesión (R9) y un resultado de otra
 * generación o con la sesión vencida lanza [SessionInvalidated].
 */
fun <T> SessionManager.patientStream(
    isTerminal: (T) -> Boolean,
    isUnauthorized: (T) -> Boolean,
    open: (String) -> Flow<T>,
): Flow<T> = flow {
    val origin = state.value as? SessionState.Authenticated ?: throw SessionInvalidated()
    val handle = capture(origin.generation)
    open(handle.authorization).collect { event ->
        if (isTerminal(event)) finish(handle, isUnauthorized(event))
        emit(event)
    }
}
