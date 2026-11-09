package co.asclepio.mobile.core.encounters.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.EncounterStatus
import co.asclepio.mobile.core.encounters.deriveWaitingPage
import co.asclepio.mobile.core.encounters.ui.*
import co.asclepio.mobile.core.ui.*
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun EncounterListScreen(
    state: EncounterListUiState, onRefresh: () -> Unit, onRetrySites: () -> Unit,
    onSiteChange: (String?) -> Unit, onStatusChange: (EncounterStatus?) -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onOpen: (String) -> Unit, onScroll: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    clinicalTray: Boolean = false,
) {
    val list = rememberLazyListState(state.scrollIndex, state.scrollOffset)
    val onPosition by rememberUpdatedState(onScroll)
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.distinctUntilChanged().collect { (index, offset) -> onPosition(index, offset) }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(state = list, modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item("header") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (clinicalTray) DashboardHeader(stringResource(R.string.staff_destination_triage), stringResource(R.string.clinical_tray_intro), onRefresh, state.refreshing)
                    else PageHeader(stringResource(R.string.staff_destination_encounters))
                    if (!clinicalTray) OutlinedButton(onClick = onRefresh, enabled = !state.refreshing && (state.remote as? EncounterListRemote.Error)?.retryEnabled != false) { Text(stringResource(R.string.home_refresh)) }
                    if (clinicalTray) {
                        val count = when (val remote = state.remote) {
                            EncounterListRemote.Loading -> DashboardRead.Loading
                            EncounterListRemote.Forbidden -> DashboardRead.Forbidden
                            EncounterListRemote.NotFound -> DashboardRead.Error(UiMessage(R.string.clinical_tray_error))
                            is EncounterListRemote.Error -> DashboardRead.Error(remote.message, remote.retryEnabled)
                            is EncounterListRemote.Ready -> deriveWaitingPage(state.filters.offset, remote.items.size).count?.let { DashboardRead.Ready(it) } ?: DashboardRead.Loading
                        }
                        DashboardSummaryBar(listOf(DashboardSummaryCell(stringResource(R.string.dash_stat_waiting), R.drawable.ic_dashboard_clock, count, NoticeTone.Warning, onRefresh)), filter = { DashboardSiteFilter(state.sites, state.filters.siteId, onSiteChange, onRetrySites) })
                        DashboardWorkHeader(stringResource(R.string.clinical_pending))
                    }
                    if (!clinicalTray) Panel(Modifier.fillMaxWidth()) {
                        StaffChoiceField(
                            label = stringResource(R.string.tenant_picker_sites), selected = state.filters.siteId,
                            options = listOf(null to stringResource(R.string.staff_sites_all)) + state.sites.sites.map { it.id to it.name },
                            enabled = state.sites.available, onSelect = onSiteChange,
                        )
                        state.sites.message?.let {
                            UiNotice(it.text(), NoticeTone.Warning)
                            TextButton(onClick = onRetrySites, enabled = state.sites.retryEnabled && !state.sites.loading) { Text(stringResource(R.string.retry)) }
                        }
                        if (!clinicalTray) StaffChoiceField(
                            label = stringResource(R.string.staff_status), selected = state.filters.status,
                            options = listOf(null to stringResource(R.string.staff_statuses_all)) + EncounterStatus.entries.map { it to stringResource(staffStatusLabel(it.name)) },
                            onSelect = onStatusChange,
                        )
                    }
                    if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            when (val remote = state.remote) {
                EncounterListRemote.Loading -> item("loading") { UiNotice(stringResource(if (clinicalTray) R.string.clinical_tray_loading else R.string.staff_encounters_loading)) }
                EncounterListRemote.Forbidden -> item("forbidden") { UiNotice(stringResource(R.string.access_denied), NoticeTone.Error) }
                EncounterListRemote.NotFound -> item("not-found") { UiNotice(stringResource(R.string.staff_encounter_not_found), NoticeTone.Error) }
                is EncounterListRemote.Error -> item("error") {
                    Panel(Modifier.fillMaxWidth()) {
                        UiNotice(remote.message.text(), NoticeTone.Error)
                        AsclepioButton(onClick = onRefresh, enabled = remote.retryEnabled && !state.refreshing) { Text(stringResource(R.string.retry)) }
                    }
                }
                is EncounterListRemote.Ready -> {
                    if (remote.items.isEmpty()) item("empty") { UiNotice(stringResource(if (clinicalTray) R.string.clinical_tray_empty else R.string.staff_encounters_empty)) }
                    items(remote.items, key = { it.id }, contentType = { "encounter" }) { encounter ->
                        Panel(Modifier.fillMaxWidth()) {
                            val patientName = encounter.patient.fullName?.takeIf(String::isNotBlank) ?: stringResource(R.string.staff_patient_nn)
                            Text(patientName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                            encounter.patient.documentNumber?.let { StaffFact(stringResource(R.string.staff_document), listOfNotNull(encounter.patient.documentType, it).joinToString(" ")) }
                            StaffFact(stringResource(R.string.staff_arrival), if (clinicalTray) runCatching { formatDashboardBogota(encounter.arrivalAt) }.getOrNull() ?: stringResource(R.string.staff_value_unavailable) else staffDateTime(encounter.arrivalAt))
                            StaffFact(stringResource(R.string.tenant_picker_sites), encounter.siteName)
                            StaffFact(stringResource(R.string.staff_status), stringResource(staffStatusLabel(encounter.status)))
                            StaffFact(stringResource(R.string.staff_origin), stringResource(staffOriginLabel(encounter.origin)))
                            val openLabel = stringResource(if (clinicalTray) R.string.dash_capture_named else R.string.dash_view_named, patientName)
                            AsclepioButton(onClick = { onOpen(encounter.id) }, enabled = !state.refreshing, modifier = Modifier.semantics { contentDescription = openLabel }) { Text(stringResource(if (clinicalTray) R.string.clinical_open else R.string.staff_encounter_view)) }
                        }
                    }
                    item("pagination") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.staff_page, state.filters.offset / 50 + 1))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onPrevious, enabled = state.canPrevious) { Text(stringResource(R.string.staff_previous)) }
                                OutlinedButton(onClick = onNext, enabled = state.canNext) { Text(stringResource(R.string.staff_next)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
