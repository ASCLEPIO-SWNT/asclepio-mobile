package co.asclepio.mobile.feature.triage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.encounters.ui.*
import co.asclepio.mobile.core.ui.*

@Composable
fun ClinicalCaptureScreen(
    state: ClinicalCaptureUiState, onRefresh: () -> Unit, onBack: () -> Unit,
    onVitalsChange: (RawVitalSigns) -> Unit, onSaveVitals: () -> Unit, onVitalsFocusHandled: (Long) -> Unit,
    onFindingChange: (Long, RawSymptomFinding) -> Unit, onAddFinding: () -> Unit, onRemoveFinding: (Long) -> Unit,
    onSaveFindings: () -> Unit, onFindingsFocusHandled: (Long) -> Unit, modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier, maxWidth = 840.dp) {
        PageHeader(stringResource(R.string.clinical_capture_title), stringResource(R.string.clinical_capture_intro))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !state.checking && (state.context as? ClinicalRead.Error)?.retryEnabled != false && (state.capture as? ClinicalRead.Error)?.retryEnabled != false) { Text(stringResource(R.string.home_refresh)) }
            TextButton(onClick = onBack) { Text(stringResource(R.string.clinical_back)) }
        }
        if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (val context = state.context) {
            ClinicalRead.Loading -> UiNotice(stringResource(R.string.clinical_state_checking))
            ClinicalRead.Forbidden -> UiNotice(stringResource(R.string.access_denied), NoticeTone.Error)
            ClinicalRead.NotFound -> UiNotice(stringResource(R.string.clinical_not_found), NoticeTone.Error)
            is ClinicalRead.Error -> { UiNotice(stringResource(R.string.clinical_state_error), NoticeTone.Error); UiNotice(context.message.text(), NoticeTone.Error) }
            is ClinicalRead.Ready -> Panel(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.clinical_context), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                StaffPatientFacts(context.data.patient)
                StaffFact(stringResource(R.string.tenant_picker_sites), context.data.siteName)
                StaffFact(stringResource(R.string.staff_status), stringResource(staffStatusLabel(context.data.status)))
                StaffFact(stringResource(R.string.staff_arrival_legal), staffDateTime(context.data.arrivalAt))
                StaffFact(stringResource(R.string.clinical_original_complaint), context.data.chiefComplaintText)
                StaffFact(stringResource(R.string.staff_origin), stringResource(staffComplaintSourceLabel(context.data.complaintSource)))
            }
        }
        if (!state.unavailable) {
            if (state.hasLoaded) {
                if (!state.checking && !state.canWrite && state.context is ClinicalRead.Ready && state.capture is ClinicalRead.Ready) UiNotice(stringResource(R.string.clinical_read_only), NoticeTone.Warning)
                Panel(Modifier.fillMaxWidth()) {
                    ClinicalVitalSignsForm(state.vitals, state.canWrite, onVitalsChange, onSaveVitals, onVitalsFocusHandled, editable = !state.readOnly)
                }
                Panel(Modifier.fillMaxWidth()) {
                    ClinicalFindingsForm(state.findings, state.canWrite, onFindingChange, onAddFinding, onRemoveFinding, onSaveFindings, onFindingsFocusHandled, editable = !state.readOnly)
                }
            }
            when (val capture = state.capture) {
                is ClinicalRead.Ready -> ClinicalHistorySection(capture.data)
                is ClinicalRead.Error -> UiNotice(capture.message.text(), NoticeTone.Error)
                ClinicalRead.Loading -> if (state.context is ClinicalRead.Ready) UiNotice(stringResource(R.string.clinical_history_loading))
                else -> Unit
            }
        }
    }
}

@Composable
fun ClinicalHistoryPane(
    state: ClinicalRead<ClinicalCaptureDto>, onRefresh: () -> Unit,
    onCapture: (() -> Unit)? = null, modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.clinical_capture_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        when (state) {
            ClinicalRead.Loading -> UiNotice(stringResource(R.string.clinical_history_loading))
            ClinicalRead.Forbidden -> UiNotice(stringResource(R.string.access_denied), NoticeTone.Error)
            ClinicalRead.NotFound -> UiNotice(stringResource(R.string.clinical_not_found), NoticeTone.Error)
            is ClinicalRead.Error -> {
                UiNotice(state.message.text(), NoticeTone.Error)
                OutlinedButton(onClick = onRefresh, enabled = state.retryEnabled) { Text(stringResource(R.string.retry)) }
            }
            is ClinicalRead.Ready -> {
                OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.home_refresh)) }
                if (onCapture != null && state.data.status == "WAITING_TRIAGE") AsclepioButton(onClick = onCapture) { Text(stringResource(R.string.clinical_open)) }
                ClinicalHistorySection(state.data)
            }
        }
    }
}
