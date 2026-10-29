package co.asclepio.mobile.feature.reception.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.CreateDirectEncounterRequest
import co.asclepio.mobile.core.encounters.StaffDocumentType
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.reception.data.CreateStaffPatientRequest
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface StaffPatientMode {
    data class Document(val type: StaffDocumentType, val number: String) : StaffPatientMode {
        override fun toString() = "Document(type=$type, number=***)"
    }
    data object Nn : StaffPatientMode
}
enum class NnAgeMode { BirthDate, EstimatedAge }
enum class StaffPatientField { DocumentType, DocumentNumber, FullName, BirthDate, Sex, EstimatedAge }
enum class StaffSearchField { DocumentType, DocumentNumber }
enum class StaffArrivalField { Site, ChiefComplaint }

data class StaffSearchForm(val documentType: StaffDocumentType? = null, val documentNumber: String = "") {
    override fun toString() = "StaffSearchForm(documentType=$documentType, documentNumber=***)"
}
data class StaffPatientForm(
    val mode: StaffPatientMode,
    val fullName: String = "",
    val birthDate: LocalDate? = null,
    val sex: Sex? = null,
    val ageMode: NnAgeMode = NnAgeMode.BirthDate,
    val estimatedAge: String = "",
) {
    override fun toString() = "StaffPatientForm(mode=$mode, identity=***, birthDate=***, estimatedAge=***, sex=$sex)"
}
data class StaffArrivalForm(val siteId: String? = null, val chiefComplaintText: String = "") {
    override fun toString() = "StaffArrivalForm(siteId=$siteId, chiefComplaintText=***)"
}

fun validateStaffSearch(form: StaffSearchForm): Map<StaffSearchField, UiMessage> = buildMap {
    if (form.documentType == null) put(StaffSearchField.DocumentType, UiMessage(R.string.staff_document_choose))
    val number = form.documentNumber.trim()
    when {
        number.isEmpty() -> put(StaffSearchField.DocumentNumber, UiMessage(R.string.staff_document_required))
        number.length > 64 -> put(StaffSearchField.DocumentNumber, UiMessage(R.string.staff_document_max))
    }
}
fun validateStaffPatient(form: StaffPatientForm, clock: Clock): Map<StaffPatientField, UiMessage> = buildMap {
    val name = form.fullName.trim()
    when {
        name.isEmpty() && form.mode is StaffPatientMode.Document -> put(StaffPatientField.FullName, UiMessage(R.string.staff_name_required))
        name.length > 200 -> put(StaffPatientField.FullName, UiMessage(R.string.staff_name_max))
    }
    if (form.sex == null) put(StaffPatientField.Sex, UiMessage(R.string.staff_sex_choose))
    val withDate = form.mode is StaffPatientMode.Document || form.ageMode == NnAgeMode.BirthDate
    if (withDate) {
        when {
            form.birthDate == null -> put(StaffPatientField.BirthDate, UiMessage(R.string.staff_birth_required))
            form.birthDate.isAfter(LocalDate.now(clock.withZone(ZoneId.of("America/Bogota")))) -> put(StaffPatientField.BirthDate, UiMessage(R.string.staff_birth_future))
        }
    } else {
        val age = form.estimatedAge.trim().toIntOrNull()
        if (age == null || age !in 0..120) put(StaffPatientField.EstimatedAge, UiMessage(R.string.staff_age_invalid))
    }
}
fun StaffPatientForm.toRequest(): CreateStaffPatientRequest {
    val document = mode as? StaffPatientMode.Document
    val withDate = document != null || ageMode == NnAgeMode.BirthDate
    return CreateStaffPatientRequest(
        sex = checkNotNull(sex), documentType = document?.type, documentNumber = document?.number?.trim(),
        fullName = fullName.trim().takeIf(String::isNotEmpty),
        birthDate = if (withDate) checkNotNull(birthDate).toString() else null,
        estimatedAgeYears = if (withDate) null else estimatedAge.trim().toInt(),
    )
}
fun validateStaffArrival(form: StaffArrivalForm): Map<StaffArrivalField, UiMessage> = buildMap {
    if (form.siteId.isNullOrBlank()) put(StaffArrivalField.Site, UiMessage(R.string.staff_site_required))
    val complaint = form.chiefComplaintText.trim()
    when {
        complaint.isEmpty() -> put(StaffArrivalField.ChiefComplaint, UiMessage(R.string.staff_complaint_required))
        complaint.length > 2000 -> put(StaffArrivalField.ChiefComplaint, UiMessage(R.string.staff_complaint_max))
    }
}
fun StaffArrivalForm.toRequest(patientId: String) = CreateDirectEncounterRequest(patientId, checkNotNull(siteId), chiefComplaintText.trim())
