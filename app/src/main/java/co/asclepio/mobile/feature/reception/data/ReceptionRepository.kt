package co.asclepio.mobile.feature.reception.data

import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.httpStatus

class ReceptionRepository(
    private val api: ReceptionApi,
    private val session: SessionManager,
    private val changes: StaffDataChanges,
) {
    val sessionGeneration: Long get() = session.generation

    suspend fun pending(siteId: String? = null): ApiResult<List<PendingPreRegistrationDto>> =
        session.staffRequest(RECEPTION_ROLES) { api.pending(siteId?.takeIf(String::isNotBlank), it.authorization) }.result

    suspend fun confirm(id: String): ApiResult<EncounterDto> {
        val scoped = session.staffRequest(RECEPTION_ROLES) { api.confirm(id, ConfirmPreRegistrationRequest(true), it.authorization) }
        when (val result = scoped.result) {
            is ApiResult.Success -> changes.publish(StaffDataChange(scoped.generation,
                setOf(StaffResource.Pending, StaffResource.Encounters, StaffResource.Detail), result.value.id))
            is ApiResult.Failure -> if (result.error.httpStatus in setOf(409, 404)) changes.publish(StaffDataChange(scoped.generation,
                setOf(StaffResource.Pending, StaffResource.Encounters)))
        }
        return scoped.result
    }

    suspend fun reject(id: String, reason: String): ApiResult<RejectedPreRegistrationDto> {
        val scoped = session.staffRequest(RECEPTION_ROLES) { api.reject(id, StaffReasonRequest(reason.trim()), it.authorization) }
        if (scoped.result is ApiResult.Success || (scoped.result as? ApiResult.Failure)?.error?.httpStatus in setOf(409, 404)) {
            changes.publish(StaffDataChange(scoped.generation, setOf(StaffResource.Pending)))
        }
        return scoped.result
    }

    suspend fun search(documentType: StaffDocumentType, documentNumber: String): ApiResult<List<StaffPatientDto>> =
        session.staffRequest(RECEPTION_ROLES) { api.search(documentType.name, documentNumber.trim(), it.authorization) }.result

    suspend fun createPatient(request: CreateStaffPatientRequest): ApiResult<StaffPatientDto> =
        session.staffRequest(RECEPTION_ROLES) {
            api.createPatient(request.copy(documentNumber = request.documentNumber?.trim(),
                fullName = request.fullName?.trim()?.takeIf(String::isNotEmpty)), it.authorization)
        }.result
}
