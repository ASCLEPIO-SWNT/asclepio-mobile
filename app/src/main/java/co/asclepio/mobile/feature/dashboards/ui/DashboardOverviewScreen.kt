package co.asclepio.mobile.feature.dashboards.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.StaffDestination
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.encounters.ui.*
import co.asclepio.mobile.core.ui.*
import kotlinx.coroutines.flow.distinctUntilChanged

/** Medicine/Queue share useful recent data; Administration/Reports share patient-free site summaries. */
@Composable
fun DashboardOverviewScreen(
    state: DashboardOverviewUiState, onRefresh: () -> Unit, onRetryCount: (EncounterStatus) -> Unit,
    onRetryRecent: () -> Unit, onRetrySites: () -> Unit, onRetrySite: (String) -> Unit,
    onOpenEncounter: (String) -> Unit, onEncounters: () -> Unit, onScroll: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState(state.scrollIndex, state.scrollOffset)
    val onPosition by rememberUpdatedState(onScroll)
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.distinctUntilChanged().collect { (index, offset) -> onPosition(index, offset) }
    }
    val admin = state.administration
    val title = when (state.destination) {
        StaffDestination.MEDICINE -> R.string.staff_destination_medicine
        StaffDestination.ADMINISTRATION -> R.string.staff_destination_administration
        StaffDestination.REPORTS -> R.string.staff_destination_reports
        else -> R.string.staff_destination_queue
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(state = list, modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item("header") { DashboardHeader(stringResource(title), stringResource(if (admin) R.string.dash_reports_lead else R.string.dash_queue_lead), onRefresh, state.refreshing) }
            item("summary") {
                DashboardSummaryBar(dashboardStatuses.map { status -> DashboardSummaryCell(
                    label = stringResource(dashboardLabel(status)), icon = dashboardIcon(status.name),
                    state = state.counts[status] ?: DashboardRead.Loading,
                    tone = when (status) { EncounterStatus.WAITING_TRIAGE -> NoticeTone.Warning; EncounterStatus.IN_CARE -> NoticeTone.Success; else -> NoticeTone.Info },
                    onRetry = { onRetryCount(status) },
                ) })
            }
            item("work") {
                Panel(Modifier.fillMaxWidth()) {
                    DashboardWorkHeader(stringResource(if (admin) R.string.dash_reports_by_site else R.string.dash_queue_recent))
                    if (admin) {
                        when (val sites = state.sites) {
                            DashboardRead.Loading -> Text(stringResource(R.string.dash_reports_sites_loading))
                            DashboardRead.Forbidden -> Text(stringResource(R.string.dash_stat_forbidden))
                            is DashboardRead.Error -> DashboardReadError(sites, onRetrySites)
                            is DashboardRead.Ready -> {
                                if (sites.data.isEmpty()) Text(stringResource(R.string.dash_reports_sites_empty))
                                sites.data.forEach { site -> key(site.id) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(site.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                        val count = state.siteCounts[site.id] ?: DashboardRead.Loading
                                        when (count) {
                                            DashboardRead.Loading -> Text(stringResource(R.string.dash_stat_loading))
                                            DashboardRead.Forbidden -> Text(stringResource(R.string.dash_stat_forbidden))
                                            is DashboardRead.Error -> {
                                                val retryName = stringResource(R.string.dash_retry_named, site.name)
                                                Text(count.message.text())
                                                TextButton(onClick = { onRetrySite(site.id) }, enabled = count.retryEnabled, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = retryName }) { Text(stringResource(R.string.retry)) }
                                            }
                                            is DashboardRead.Ready -> DashboardCountText(count.data.count)
                                        }
                                        HorizontalDivider()
                                    }
                                } }
                                Text(stringResource(R.string.dash_sites_count, sites.data.size))
                            }
                        }
                    } else when (val recent = state.recent) {
                        DashboardRead.Loading -> Text(stringResource(R.string.dash_queue_recent_loading))
                        DashboardRead.Forbidden -> Text(stringResource(R.string.dash_stat_forbidden))
                        is DashboardRead.Error -> DashboardReadError(recent, onRetryRecent)
                        is DashboardRead.Ready -> {
                            if (recent.data.isEmpty()) Text(stringResource(R.string.dash_queue_recent_empty))
                            recent.data.forEach { encounter -> key(encounter.id) {
                                val name = encounter.fullName?.takeIf(String::isNotBlank) ?: stringResource(R.string.staff_patient_nn)
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                    StaffFact(stringResource(R.string.tenant_picker_sites), encounter.siteName)
                                    StaffFact(stringResource(R.string.staff_arrival), runCatching { formatDashboardBogota(encounter.arrivalAt) }.getOrNull() ?: stringResource(R.string.staff_value_unavailable))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(painterResource(dashboardIcon(encounter.status)), null, modifier = Modifier.size(24.dp))
                                        Text(stringResource(staffStatusLabel(encounter.status)))
                                    }
                                    val action = stringResource(R.string.dash_view_named, name)
                                    OutlinedButton(onClick = { onOpenEncounter(encounter.id) }, modifier = Modifier.semantics { contentDescription = action }) { Text(stringResource(R.string.staff_encounter_view)) }
                                    HorizontalDivider()
                                }
                            } }
                            if (recent.data.isNotEmpty()) Text(stringResource(R.string.dash_recent_footer, recent.data.size))
                        }
                    }
                    TextButton(onClick = onEncounters) { Text(stringResource(R.string.dash_view_all_encounters)) }
                }
            }
            item("pending") {
                DashboardPendingStrip(stringResource(if (admin) R.string.dash_reports_pending_title else R.string.dash_queue_pending_title), stringResource(if (admin) R.string.dash_reports_pending_body else R.string.dash_queue_pending_body))
            }
        }
    }
}

@Composable private fun DashboardReadError(state: DashboardRead.Error, onRetry: () -> Unit) {
    Text(state.message.text(), color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = onRetry, enabled = state.retryEnabled) { Text(stringResource(R.string.retry)) }
}

private fun dashboardLabel(status: EncounterStatus): Int = when (status) {
    EncounterStatus.WAITING_TRIAGE -> R.string.dash_stat_waiting
    EncounterStatus.TRIAGED -> R.string.dash_stat_triaged
    else -> R.string.dash_stat_in_care
}
@DrawableRes private fun dashboardIcon(status: String): Int = when (status) {
    "WAITING_TRIAGE" -> R.drawable.ic_dashboard_clock
    "TRIAGED" -> R.drawable.ic_dashboard_classified
    "IN_CARE" -> R.drawable.ic_dashboard_care
    "REGISTERED" -> R.drawable.ic_dashboard_pending
    "CLOSED" -> R.drawable.ic_dashboard_closed
    "CANCELLED" -> R.drawable.ic_dashboard_cancelled
    else -> R.drawable.ic_dashboard_error
}
