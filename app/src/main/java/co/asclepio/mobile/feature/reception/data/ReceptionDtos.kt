package co.asclepio.mobile.feature.reception.data

import co.asclepio.mobile.core.encounters.StaffDocumentType
import co.asclepio.mobile.core.encounters.StaffPatientDto
import co.asclepio.mobile.core.model.Sex
import kotlinx.serialization.Serializable

@Serializable
data class PendingPreRegistrationDto(
    val id: String,
    val siteId: String,
    val siteName: String,
    val preRegisteredAt: String,
    val expiresAt: String,
    val chiefComplaintText: String,
    val complaintSource: String,
    val identityVerified: Boolean,
    val patient: StaffPatientDto,
) { override fun toString() = "PendingPreRegistrationDto(***)" }

@Serializable
data class RejectedPreRegistrationDto(val id: String, val status: String, val resolvedAt: String) {
    override fun toString() = "RejectedPreRegistrationDto(***)"
}

@Serializable
data class ConfirmPreRegistrationRequest(val identityVerified: Boolean)

@Serializable
data class CreateStaffPatientRequest(
    val sex: Sex,
    val documentType: StaffDocumentType? = null,
    val documentNumber: String? = null,
    val fullName: String? = null,
    val birthDate: String? = null,
    val estimatedAgeYears: Int? = null,
) { override fun toString() = "CreateStaffPatientRequest(***)" }
