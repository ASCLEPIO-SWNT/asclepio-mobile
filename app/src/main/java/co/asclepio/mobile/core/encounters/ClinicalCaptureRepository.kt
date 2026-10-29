package co.asclepio.mobile.core.encounters

import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.httpStatus

class ClinicalCaptureRepository(
    private val api: ClinicalCaptureApi,
    private val session: SessionManager,
    private val changes: StaffDataChanges,
) {
    val sessionGeneration: Long get() = session.generation

    suspend fun getCapture(id: String): ApiResult<ClinicalCaptureDto> =
        session.staffRequest(setOf(StaffRole.NURSE, StaffRole.PHYSICIAN)) { api.getCapture(id, it.authorization) }.result

    suspend fun recordVitalSigns(id: String, request: CreateVitalSignsRequest): ApiResult<VitalSignsDto> {
        val result = session.staffRequest(setOf(StaffRole.NURSE)) { api.recordVitalSigns(id, request, it.authorization) }
        invalidate(id, result)
        return result.result
    }

    suspend fun recordFindings(id: String, request: CreateSymptomFindingsRequest): ApiResult<SymptomFindingListDto> {
        val body = request.copy(items = request.items.map { it.copy(description = it.description.trim()) })
        val result = session.staffRequest(setOf(StaffRole.NURSE)) { api.recordFindings(id, body, it.authorization) }
        invalidate(id, result)
        return result.result
    }

    private suspend fun invalidate(id: String, result: ScopedStaffResult<*>) {
        if (result.result is ApiResult.Success || (result.result as? ApiResult.Failure)?.error?.httpStatus == 409) {
            changes.publish(StaffDataChange(result.generation,
                setOf(StaffResource.ClinicalCapture, StaffResource.Detail, StaffResource.Encounters), id))
        }
    }
}
