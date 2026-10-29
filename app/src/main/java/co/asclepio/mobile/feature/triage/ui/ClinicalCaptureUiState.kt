package co.asclepio.mobile.feature.triage.ui

import co.asclepio.mobile.core.encounters.ClinicalCaptureDto
import co.asclepio.mobile.core.encounters.EncounterDto
import co.asclepio.mobile.core.ui.UiMessage

sealed interface ClinicalRead<out T> {
    data object Loading : ClinicalRead<Nothing>
    data class Ready<T>(val data: T, val revision: Long) : ClinicalRead<T> {
        override fun toString() = "ClinicalRead.Ready(revision=$revision, private=***)"
    }
    data class Error(val message: UiMessage, val retryEnabled: Boolean = true) : ClinicalRead<Nothing>
    data object Forbidden : ClinicalRead<Nothing>
    data object NotFound : ClinicalRead<Nothing>
}

data class ClinicalCaptureUiState(
    val context: ClinicalRead<EncounterDto> = ClinicalRead.Loading,
    val capture: ClinicalRead<ClinicalCaptureDto> = ClinicalRead.Loading,
    val readRevision: Long = 0,
    val checking: Boolean = false,
    val hasLoaded: Boolean = false,
    val readOnly: Boolean = false,
    val vitals: VitalSignsFormUiState = VitalSignsFormUiState(),
    val findings: FindingsFormUiState = FindingsFormUiState(),
) {
    val canWrite: Boolean get() {
        val encounter = context as? ClinicalRead.Ready ?: return false
        val clinical = capture as? ClinicalRead.Ready ?: return false
        return !checking && encounter.revision == readRevision && clinical.revision == readRevision &&
            encounter.data.id == clinical.data.encounterId && encounter.data.status == "WAITING_TRIAGE" &&
            clinical.data.status == "WAITING_TRIAGE"
    }
    val unavailable: Boolean get() = context == ClinicalRead.Forbidden || context == ClinicalRead.NotFound ||
        capture == ClinicalRead.Forbidden || capture == ClinicalRead.NotFound
    override fun toString() = "ClinicalCaptureUiState(revision=$readRevision, checking=$checking, private=***)"
}
