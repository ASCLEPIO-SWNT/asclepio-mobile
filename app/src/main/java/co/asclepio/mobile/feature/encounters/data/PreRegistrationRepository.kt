package co.asclepio.mobile.feature.encounters.data

import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.auth.patientCall
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall
import co.asclepio.mobile.core.tenants.PublicTenantDto
import co.asclepio.mobile.core.tenants.TenantsApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Pre-registros del paciente autenticado (D4) y sedes de la lista pública (D11). Un 401 de `/patient/…` cierra la
 * sesión (R23). [created] publica la respuesta 201 para que el estado del inicio la muestre al volver (R12, D3);
 * no contiene el motivo, no guarda el último valor (un inicio nuevo, p. ej. tras otro login, no lo ve) y solo vive
 * en memoria.
 */
class PreRegistrationRepository(
    private val api: PreRegistrationsApi,
    private val tenantsApi: TenantsApi,
    private val sessionManager: SessionManager,
) {
    private val _created = MutableSharedFlow<PatientPreRegistrationDto>(extraBufferCapacity = 1)
    val created: SharedFlow<PatientPreRegistrationDto> = _created.asSharedFlow()

    suspend fun tenants(): ApiResult<List<PublicTenantDto>> = apiCall { tenantsApi.tenants() }

    suspend fun create(request: CreatePreRegistrationRequest): ApiResult<PatientPreRegistrationDto> =
        sessionManager.patientCall { bearer -> api.create(request, bearer) }.also {
            if (it is ApiResult.Success) _created.tryEmit(it.value)
        }

    /** El último pre-registro (el primero de la lista), o `null` si no hay ninguno (R17, R18). */
    suspend fun latest(): ApiResult<PatientPreRegistrationDto?> =
        when (val result = sessionManager.patientCall { bearer -> api.list(bearer) }) {
            is ApiResult.Success -> ApiResult.Success(result.value.firstOrNull())
            is ApiResult.Failure -> result
        }
}
