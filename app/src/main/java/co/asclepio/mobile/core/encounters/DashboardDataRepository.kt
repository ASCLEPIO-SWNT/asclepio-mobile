package co.asclepio.mobile.core.encounters

import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.tenants.PublicSiteDto

/**
 * Reutiliza GET /encounters y su scope STAFF, sin caché, scheduler ni endpoint adicional.
 * La respuesta HTTP completa es transitoria; cada operación devuelve solo su proyección.
 * El coordinador posee visibilidad, generación, catálogo por sesión y una petición por key.
 */
class DashboardDataRepository(
    private val encounters: EncountersRepository,
    private val sites: StaffSitesRepository,
) {
    val sessionGeneration: Long get() = encounters.sessionGeneration

    suspend fun count(status: EncounterStatus, siteId: String? = null): ApiResult<EncounterCount> =
        when (val result = encounters.list(EncounterFilters(status, siteId, DashboardLimits.COUNT, 0))) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(
                EncounterCount(
                    quantity = result.value.items.size,
                    limited = result.value.items.size >= DashboardLimits.COUNT,
                ),
            )
        }

    suspend fun recent(): ApiResult<List<RecentEncounter>> =
        when (val result = encounters.list(EncounterFilters(limit = DashboardLimits.RECENT, offset = 0))) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(result.value.items.take(DashboardLimits.RECENT).map { row ->
                RecentEncounter(
                    id = row.id,
                    fullName = row.patient.fullName,
                    siteName = row.siteName,
                    arrivalAt = row.arrivalAt,
                    status = row.status,
                )
            })
        }

    /** Carga inicial/reintento explícito del coordinador; no repetir este catálogo en sus ticks. */
    suspend fun listSites(): ApiResult<List<PublicSiteDto>> = sites.list()

    /** Una petición por fila y un resultado independiente; nunca se cargan pacientes por sede. */
    suspend fun siteSummary(site: PublicSiteDto): ApiResult<SiteEncounterCount> =
        when (val result = count(EncounterStatus.WAITING_TRIAGE, site.id)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(SiteEncounterCount(site.id, site.name, result.value))
        }
}
