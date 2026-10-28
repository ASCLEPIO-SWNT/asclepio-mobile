package co.asclepio.mobile.core.tenants

import kotlinx.serialization.Serializable
import retrofit2.http.GET

// DTOs escritos a mano según el snapshot de ../asclepio-api/openapi/asclepio-api.v1.json (sin cambios de 0.2.1 a 0.4.0).

/** PublicSiteDto. */
@Serializable
data class PublicSiteDto(val id: String, val name: String)

/** PublicTenantDto. */
@Serializable
data class PublicTenantDto(val id: String, val name: String, val sites: List<PublicSiteDto>)

/**
 * `GET /api/v1/public/tenants` (sin Authorization): lo usan la elección de IPS (F-01) y el formulario de
 * pre-registro (F-02). Vive en `core/` porque lo comparten dos features (D2 de F-02-mobile).
 */
interface TenantsApi {
    @GET("api/v1/public/tenants")
    suspend fun tenants(): List<PublicTenantDto>
}
