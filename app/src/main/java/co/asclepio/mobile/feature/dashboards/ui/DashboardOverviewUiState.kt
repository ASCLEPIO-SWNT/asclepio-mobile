package co.asclepio.mobile.feature.dashboards.ui

import co.asclepio.mobile.core.auth.StaffDestination
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.tenants.PublicSiteDto
import co.asclepio.mobile.core.ui.DashboardRead

val dashboardStatuses = listOf(EncounterStatus.WAITING_TRIAGE, EncounterStatus.TRIAGED, EncounterStatus.IN_CARE)

data class DashboardOverviewUiState(
    val destination: StaffDestination,
    val counts: Map<EncounterStatus, DashboardRead<EncounterCount>> = dashboardStatuses.associateWith { DashboardRead.Loading },
    val recent: DashboardRead<List<RecentEncounter>> = DashboardRead.Loading,
    val sites: DashboardRead<List<PublicSiteDto>> = DashboardRead.Loading,
    val siteCounts: Map<String, DashboardRead<SiteEncounterCount>> = emptyMap(),
    val refreshing: Boolean = false,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
) {
    val administration: Boolean get() = destination in setOf(StaffDestination.ADMINISTRATION, StaffDestination.REPORTS)
    override fun toString() = "DashboardOverviewUiState(destination=$destination, private=***)"
}
