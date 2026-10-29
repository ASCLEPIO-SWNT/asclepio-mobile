package co.asclepio.mobile.core.encounters

import kotlinx.serialization.Serializable

@Serializable enum class ConsciousnessLevel { A, C, V, P, U }
@Serializable enum class SymptomFindingKind { SYMPTOM, FINDING, COMPLAINT_NOTE }

/** Los siete obligatorios no tienen default: false se serializa, no se confunde con ausencia. */
@Serializable
data class CreateVitalSignsRequest(
    val respiratoryRate: Int,
    val oxygenSaturation: Int,
    val supplementalOxygen: Boolean,
    val systolicBp: Int,
    val heartRate: Int,
    val consciousness: ConsciousnessLevel,
    val temperatureC: Double,
    val diastolicBp: Int? = null,
    val glucoseMgDl: Int? = null,
) { override fun toString() = "CreateVitalSignsRequest(***)" }

@Serializable
data class VitalSignsDto(
    val id: String,
    val encounterId: String,
    val respiratoryRate: Int,
    val oxygenSaturation: Int,
    val supplementalOxygen: Boolean,
    val systolicBp: Int,
    val heartRate: Int,
    val consciousness: String,
    val temperatureC: Double,
    val diastolicBp: Int?,
    val glucoseMgDl: Int?,
    val recordedAt: String,
    val recordedBy: StaffRefDto,
) { override fun toString() = "VitalSignsDto(***)" }

@Serializable
data class SymptomFindingInput(val kind: SymptomFindingKind, val description: String) {
    override fun toString() = "SymptomFindingInput(***)"
}

@Serializable
data class CreateSymptomFindingsRequest(val items: List<SymptomFindingInput>) {
    override fun toString() = "CreateSymptomFindingsRequest(items=***)"
}

@Serializable
data class SymptomFindingDto(
    val id: String,
    val encounterId: String,
    val kind: String,
    val description: String,
    val recordedAt: String,
    val recordedBy: StaffRefDto,
) { override fun toString() = "SymptomFindingDto(***)" }

@Serializable
data class SymptomFindingListDto(val items: List<SymptomFindingDto>) {
    override fun toString() = "SymptomFindingListDto(items=***)"
}

@Serializable
data class ClinicalCaptureDto(
    val encounterId: String,
    val status: String,
    val chiefComplaintText: String,
    val complaintSource: String,
    val vitalSigns: List<VitalSignsDto>,
    val symptomFindings: List<SymptomFindingDto>,
) { override fun toString() = "ClinicalCaptureDto(***)" }
