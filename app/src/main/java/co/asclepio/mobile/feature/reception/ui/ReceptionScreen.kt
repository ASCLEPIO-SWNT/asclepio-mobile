package co.asclepio.mobile.feature.reception.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.ui.*
import co.asclepio.mobile.core.ui.*
import co.asclepio.mobile.feature.reception.data.PendingPreRegistrationDto
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ReceptionScreen(
    state: ReceptionUiState,
    onRefresh: () -> Unit,
    onRetrySites: () -> Unit,
    onSiteChange: (String?) -> Unit,
    onIdentityChange: (String, Boolean) -> Unit,
    onRejectingChange: (String, Boolean) -> Unit,
    onReasonChange: (String, String) -> Unit,
    onConfirm: (String) -> Unit,
    onReject: (String) -> Unit,
    onDirectRegistration: () -> Unit,
    onOpenEncounter: (String) -> Unit,
    onScroll: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    dashboard: Boolean = false,
    summary: (@Composable () -> Unit)? = null,
    onRetryPending: () -> Unit = onRefresh,
) {
    val listState = rememberLazyListState(state.scrollIndex, state.scrollOffset)
    val scrollCallback by rememberUpdatedState(onScroll)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged().collect { (index, offset) -> scrollCallback(index, offset) }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(state = listState, modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item("header") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (dashboard) DashboardHeader(stringResource(R.string.staff_destination_reception), stringResource(R.string.dash_reception_lead), onRefresh, state.refreshing, primary = {
                        AsclepioButton(onClick = onDirectRegistration) { Text(stringResource(R.string.staff_registration_direct)) }
                    }) else PageHeader(stringResource(R.string.staff_destination_reception))
                    if (!dashboard) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRefresh, enabled = !state.refreshing && (state.remote as? ReceptionRemote.Error)?.retryEnabled != false) { Text(stringResource(R.string.home_refresh)) }
                        AsclepioButton(onClick = onDirectRegistration) { Text(stringResource(R.string.staff_registration_direct)) }
                    }
                    summary?.invoke()
                    if (!dashboard) Panel(Modifier.fillMaxWidth()) {
                        StaffChoiceField(
                            label = stringResource(R.string.tenant_picker_sites), selected = state.siteId,
                            options = listOf(null to stringResource(R.string.staff_sites_all)) + state.sites.sites.map { it.id to it.name },
                            enabled = state.sites.available, onSelect = onSiteChange,
                        )
                        state.sites.message?.let {
                            UiNotice(it.text(), NoticeTone.Warning)
                            TextButton(onClick = onRetrySites, enabled = !state.sites.loading && state.sites.retryEnabled) { Text(stringResource(R.string.retry)) }
                        }
                    }
                    when (val notice = state.notice) {
                        is ReceptionNotice.Confirmed -> {
                            UiNotice(stringResource(R.string.staff_arrival_confirmed) + " " + (if (dashboard) formatDashboardBogota(notice.arrivalAt) else formatBogota(notice.arrivalAt)), NoticeTone.Success)
                            TextButton(onClick = { onOpenEncounter(notice.encounterId) }) { Text(stringResource(R.string.staff_encounter_view)) }
                        }
                        ReceptionNotice.Rejected -> UiNotice(stringResource(R.string.staff_pr_rejected), NoticeTone.Success)
                        null -> Unit
                    }
                    if (state.message != null && state.drafts.values.none { it.message == state.message }) UiNotice(state.message.text(), NoticeTone.Error)
                    if (dashboard) DashboardWorkHeader(stringResource(R.string.staff_tray_pending)) else Text(stringResource(R.string.staff_tray_pending), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            when (val remote = state.remote) {
                ReceptionRemote.Loading -> item("loading") { UiNotice(stringResource(R.string.staff_tray_loading)) }
                ReceptionRemote.Forbidden -> item("forbidden") { UiNotice(stringResource(R.string.access_denied), NoticeTone.Error) }
                ReceptionRemote.NotFound -> item("gone") { UiNotice(stringResource(R.string.staff_pr_gone), NoticeTone.Error) }
                is ReceptionRemote.Error -> item("error") {
                    Panel(Modifier.fillMaxWidth()) {
                        UiNotice(remote.message.text(), NoticeTone.Error)
                        AsclepioButton(onClick = onRetryPending, enabled = remote.retryEnabled && !state.refreshing) { Text(stringResource(R.string.retry)) }
                    }
                }
                is ReceptionRemote.Ready -> {
                    if (remote.items.isEmpty()) item("empty") { UiNotice(stringResource(R.string.staff_tray_empty)) }
                    items(remote.items, key = { it.id }, contentType = { "pending" }) { row ->
                        ReceptionCard(
                            row, state.drafts[row.id] ?: ReceptionDraft(), state.refreshing,
                            { onIdentityChange(row.id, it) }, { onRejectingChange(row.id, it) },
                            { onReasonChange(row.id, it) }, { onConfirm(row.id) }, { onReject(row.id) }, dashboard,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceptionCard(
    row: PendingPreRegistrationDto, draft: ReceptionDraft, refreshing: Boolean,
    onIdentity: (Boolean) -> Unit, onRejecting: (Boolean) -> Unit, onReason: (String) -> Unit,
    onConfirm: () -> Unit, onReject: () -> Unit, dashboard: Boolean = false,
) {
    val editable = draft.action == null
    val actionable = editable && !refreshing && !draft.blocked
    Panel(Modifier.fillMaxWidth()) {
        StaffPatientFacts(row.patient)
        UiNotice(stringResource(if (row.identityVerified) R.string.staff_verified else R.string.staff_unverified), if (row.identityVerified) NoticeTone.Success else NoticeTone.Warning)
        StaffFact(stringResource(R.string.tenant_picker_sites), row.siteName)
        StaffFact(stringResource(R.string.preregistration_complaint), row.chiefComplaintText)
        StaffFact(stringResource(R.string.staff_origin), stringResource(if (row.complaintSource == "TYPED") R.string.staff_complaint_typed else if (row.complaintSource == "VOICE_ON_DEVICE") R.string.staff_complaint_voice else R.string.staff_value_unavailable))
        StaffFact(stringResource(R.string.staff_pr_label), (if (dashboard) formatDashboardBogota(row.preRegisteredAt) else formatBogota(row.preRegisteredAt)))
        StaffFact(stringResource(R.string.staff_expires), (if (dashboard) formatDashboardBogota(row.expiresAt) else formatBogota(row.expiresAt)))
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = draft.identityChecked, enabled = editable, role = Role.Checkbox, onValueChange = onIdentity), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(draft.identityChecked, onCheckedChange = null, enabled = editable)
            Text(stringResource(R.string.staff_verify_identity))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsclepioButton(onClick = onConfirm, enabled = actionable && draft.identityChecked) { Text(stringResource(R.string.staff_confirm_arrival)) }
            OutlinedButton(onClick = { onRejecting(true) }, enabled = actionable && !draft.rejecting) { Text(stringResource(R.string.staff_reject)) }
        }
        draft.message?.let { UiNotice(it.text(), NoticeTone.Error) }
        draft.action?.let {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(if (it == ReceptionAction.Confirm) R.string.staff_confirming_arrival else R.string.staff_rejecting))
        }
        if (draft.rejecting) {
            Text(stringResource(R.string.staff_reject_form), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            AsclepioTextField(
                value = draft.reason, onValueChange = onReason, enabled = editable,
                label = { Text(stringResource(R.string.staff_reject_reason)) }, minLines = 3,
                isError = draft.reasonError != null, supportingText = draft.reasonError?.let { { Text(it.text()) } },
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsclepioButton(onClick = onReject, enabled = actionable) { Text(stringResource(R.string.staff_reject_confirm)) }
                TextButton(onClick = { onRejecting(false) }, enabled = editable) { Text(stringResource(R.string.navigate_back)) }
            }
        }
    }
}
