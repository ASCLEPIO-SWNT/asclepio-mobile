package co.asclepio.mobile.core.encounters

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable enum class StaffDocumentType { CC, TI, CE, PA, RC }
enum class EncounterStatus { REGISTERED, WAITING_TRIAGE, TRIAGED, IN_CARE, CLOSED, CANCELLED }

/** No se deserializan códigos remotos como enums cerrados: futuros valores no conceden permisos. */
@Serializable
data class StaffPatientDto(
    val id: String,
    val documentType: String?,
    val documentNumber: String?,
    val fullName: String?,
    val birthDate: String?,
    val estimatedAgeYears: Int?,
    val sex: String?,
    val isNN: Boolean,
) { override fun toString() = "StaffPatientDto(***)" }

@Serializable
data class StaffRefDto(val id: String, val fullName: String?) {
    override fun toString() = "StaffRefDto(***)"
}

@Serializable
data class EventActorDto(val type: String, val id: String?, val fullName: String?) {
    override fun toString() = "EventActorDto(***)"
}

@Serializable
data class EncounterEventDto(
    val id: String,
    val type: String,
    val fromStatus: String?,
    val toStatus: String?,
    val payload: JsonObject,
    val actor: EventActorDto,
    val occurredAt: String,
) { override fun toString() = "EncounterEventDto(***)" }

@Serializable
data class EncounterSummaryDto(
    val id: String,
    val siteId: String,
    val siteName: String,
    val status: String,
    val origin: String,
    val arrivalAt: String,
    val preRegistrationId: String?,
    val preRegisteredAt: String?,
    val chiefComplaintText: String,
    val complaintSource: String,
    val cancellationReason: String?,
    val registeredBy: StaffRefDto,
    val patient: StaffPatientDto,
) { override fun toString() = "EncounterSummaryDto(***)" }

@Serializable
data class EncounterDto(
    val id: String,
    val siteId: String,
    val siteName: String,
    val status: String,
    val origin: String,
    val arrivalAt: String,
    val preRegistrationId: String?,
    val preRegisteredAt: String?,
    val chiefComplaintText: String,
    val complaintSource: String,
    val cancellationReason: String?,
    val registeredBy: StaffRefDto,
    val patient: StaffPatientDto,
    val events: List<EncounterEventDto>,
) { override fun toString() = "EncounterDto(***)" }

@Serializable
data class EncounterListDto(val items: List<EncounterSummaryDto>) {
    override fun toString() = "EncounterListDto(items=***)"
}

data class EncounterFilters(
    val status: EncounterStatus? = null,
    val siteId: String? = null,
    val limit: Int = 50,
    val offset: Int = 0,
) {
    init { require(limit in 1..100 && offset >= 0) }
}

/** Input local: el repositorio añade TYPED al DTO de red, obligatorio aun con encodeDefaults=false. */
data class CreateDirectEncounterRequest(val patientId: String, val siteId: String, val chiefComplaintText: String) {
    override fun toString() = "CreateDirectEncounterRequest(***)"
}

@Serializable
data class CreateDirectEncounterBody(
    val patientId: String,
    val siteId: String,
    val chiefComplaintText: String,
    val complaintSource: String,
) { override fun toString() = "CreateDirectEncounterBody(***)" }

@Serializable
data class StaffReasonRequest(val reason: String) {
    override fun toString() = "StaffReasonRequest(***)"
}

fun canCancelEncounter(status: String): Boolean = status in setOf("REGISTERED", "WAITING_TRIAGE", "TRIAGED", "IN_CARE")
