package co.asclepio.mobile.core.auth

import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall
import co.asclepio.mobile.core.network.httpStatus

/**
 * Llamada a un endpoint `/api/v1/patient/…` (D7 de F-02-mobile, R23): como [apiCall], y ante un 401 borra la sesión
 * con [SessionManager.logout] (conserva la IPS recordada). La navegación vuelve al login al cambiar el estado.
 */
suspend fun <T> SessionManager.patientCall(block: suspend () -> T): ApiResult<T> {
    val result = apiCall(block)
    if (result is ApiResult.Failure && result.error.httpStatus == 401) logout()
    return result
}
