package co.asclepio.mobile.core.encounters

import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.httpStatus

class EncountersRepository(
    private val api: EncountersApi,
    private val session: SessionManager,
    private val changes: StaffDataChanges,
) {
    val sessionGeneration: Long get() = session.generation

    suspend fun list(filters: EncounterFilters = EncounterFilters()): ApiResult<EncounterListDto> =
        session.staffRequest(CONSULTATION_ROLES) {
            api.list(it.authorization, filters.status?.name, filters.siteId?.takeIf(String::isNotBlank), filters.limit, filters.offset)
        }.result

    suspend fun detail(id: String): ApiResult<EncounterDto> =
        session.staffRequest(CONSULTATION_ROLES) { api.detail(id, it.authorization) }.result

    suspend fun createDirect(request: CreateDirectEncounterRequest): ApiResult<EncounterDto> {
        val scoped = session.staffRequest(RECEPTION_ROLES) {
            api.createDirect(CreateDirectEncounterBody(request.patientId, request.siteId, request.chiefComplaintText.trim(), "TYPED"), it.authorization)
        }
        when (val result = scoped.result) {
            is ApiResult.Success -> changes.publish(StaffDataChange(scoped.generation,
                setOf(StaffResource.Pending, StaffResource.Encounters, StaffResource.Detail), result.value.id))
            is ApiResult.Failure -> if (result.error.httpStatus in setOf(409, 404)) changes.publish(StaffDataChange(scoped.generation,
                setOf(StaffResource.Pending, StaffResource.Encounters)))
        }
        return scoped.result
    }

    suspend fun cancel(id: String, reason: String): ApiResult<EncounterDto> {
        val scoped = session.staffRequest(CANCELLATION_ROLES) {
            api.cancel(id, StaffReasonRequest(reason.trim()), it.authorization)
        }
        if (scoped.result is ApiResult.Success || (scoped.result as? ApiResult.Failure)?.error?.httpStatus in setOf(409, 404)) {
            changes.publish(StaffDataChange(scoped.generation,
                setOf(StaffResource.Pending, StaffResource.Encounters, StaffResource.Detail), id))
        }
        return scoped.result
    }
}
