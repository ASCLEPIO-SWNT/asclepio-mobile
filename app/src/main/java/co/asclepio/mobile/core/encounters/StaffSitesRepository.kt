package co.asclepio.mobile.core.encounters

import co.asclepio.mobile.core.auth.AccessMode
import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.auth.VerifiedPrincipal
import co.asclepio.mobile.core.auth.authenticatedCall
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall
import co.asclepio.mobile.core.tenants.PublicSiteDto
import co.asclepio.mobile.core.tenants.TenantsApi
import kotlinx.serialization.SerializationException

class StaffSitesRepository(private val api: TenantsApi, private val session: SessionManager) {
    val sessionGeneration: Long get() = session.generation

    suspend fun list(): ApiResult<List<PublicSiteDto>> {
        // La consulta es pública: el resultado se anida para que un401 de catálogo no invalide una sesión STAFF.
        // El wrapper exterior comprueba principal, expiración y generación antes y después de la suspensión.
        val result = session.authenticatedCall(AccessMode.STAFF) { handle ->
            val tenantId = (handle.principal as VerifiedPrincipal.Staff).tenantId
            apiCall {
                api.tenants().firstOrNull { it.id == tenantId }?.sites
                    ?: throw SerializationException("Staff tenant absent from public catalog")
            }
        }
        return when (result) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> result
        }
    }
}
