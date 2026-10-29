package co.asclepio.mobile.feature.staffencounters.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.encounters.canCancelEncounter
import co.asclepio.mobile.core.encounters.ui.*
import co.asclepio.mobile.core.ui.*

@Composable
fun StaffEncounterDetailScreen(
    state: StaffEncounterDetailUiState, onRefresh: () -> Unit, onCancelling: (Boolean) -> Unit,
    onReason: (String) -> Unit, onCancel: () -> Unit, onEncounters: () -> Unit, modifier: Modifier = Modifier,
    clinicalHistory: (@Composable () -> Unit)? = null,
) {
    ScreenColumn(modifier, maxWidth = 840.dp) {
        PageHeader(stringResource(R.string.staff_encounter))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !state.refreshing && !state.submitting && (state.remote as? StaffEncounterRemote.Error)?.retryEnabled != false) { Text(stringResource(R.string.home_refresh)) }
            TextButton(onClick = onEncounters) { Text(stringResource(R.string.staff_encounters_all)) }
        }
        state.message?.let { UiNotice(it.text(), if (state.cancelled) NoticeTone.Success else NoticeTone.Error) }
        if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (val remote = state.remote) {
            StaffEncounterRemote.Loading -> UiNotice(stringResource(R.string.staff_encounter_loading))
            StaffEncounterRemote.Forbidden -> UiNotice(stringResource(R.string.access_denied), NoticeTone.Error)
            StaffEncounterRemote.NotFound -> UiNotice(stringResource(R.string.staff_encounter_not_found), NoticeTone.Error)
            is StaffEncounterRemote.Error -> {
                UiNotice(remote.message.text(), NoticeTone.Error)
                AsclepioButton(onClick = onRefresh, enabled = remote.retryEnabled && !state.refreshing) { Text(stringResource(R.string.retry)) }
            }
            is StaffEncounterRemote.Ready -> {
                val encounter = remote.encounter
                StaffFact(stringResource(R.string.staff_status), stringResource(staffStatusLabel(encounter.status)))
                StaffFact(stringResource(R.string.staff_origin), stringResource(staffOriginLabel(encounter.origin)))
                StaffFact(stringResource(R.string.tenant_picker_sites), encounter.siteName)
                StaffFact(stringResource(R.string.staff_arrival_legal), staffDateTime(encounter.arrivalAt))
                encounter.preRegisteredAt?.let { StaffFact(stringResource(R.string.staff_pr_reference), staffDateTime(it)) }
                StaffFact(stringResource(R.string.staff_registered_by), encounter.registeredBy.fullName ?: stringResource(R.string.staff_value_unavailable))
                HorizontalDivider()
                StaffPatientFacts(encounter.patient)
                StaffFact(stringResource(R.string.preregistration_complaint), encounter.chiefComplaintText)
                StaffFact(stringResource(R.string.staff_origin), stringResource(staffComplaintSourceLabel(encounter.complaintSource)))
                clinicalHistory?.let { slot -> HorizontalDivider(); slot() }
                encounter.cancellationReason?.let { StaffFact(stringResource(R.string.staff_cancellation_reason), it) }
                val cancellationRole = state.role in setOf(StaffRole.RECEPTION, StaffRole.NURSE)
                if (cancellationRole && canCancelEncounter(encounter.status)) {
                    HorizontalDivider()
                    if (!state.cancelling) OutlinedButton(onClick = { onCancelling(true) }, enabled = state.canCancel) { Text(stringResource(R.string.staff_encounter_cancel)) }
                    else {
                        Text(stringResource(R.string.staff_encounter_cancel), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        AsclepioTextField(
                            value = state.reason, onValueChange = onReason, modifier = Modifier.fillMaxWidth(), enabled = !state.submitting, minLines = 3,
                            label = { Text(stringResource(R.string.staff_cancellation_reason)) }, isError = state.reasonError != null,
                            supportingText = state.reasonError?.let { { Text(it.text()) } },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AsclepioButton(onClick = onCancel, enabled = state.canCancel) { Text(stringResource(if (state.submitting) R.string.staff_encounter_cancelling else R.string.staff_cancel_confirm)) }
                            TextButton(onClick = { onCancelling(false) }, enabled = !state.submitting) { Text(stringResource(R.string.navigate_back)) }
                        }
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.staff_timeline), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                encounter.events.forEach { event ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(staffEventLabel(event.type)), style = MaterialTheme.typography.titleMedium)
                        Text(staffDateTime(event.occurredAt))
                        Text(when (event.actor.type) {
                            "STAFF" -> event.actor.fullName ?: stringResource(R.string.access_staff)
                            "PATIENT" -> stringResource(R.string.access_patient)
                            "SYSTEM" -> stringResource(R.string.theme_system)
                            else -> stringResource(R.string.staff_value_unavailable)
                        })
                        if (event.fromStatus != null && event.toStatus != null) Text(stringResource(staffStatusLabel(event.fromStatus)) + " → " + stringResource(staffStatusLabel(event.toStatus)))
                    }
                }
            }
        }
    }
}
