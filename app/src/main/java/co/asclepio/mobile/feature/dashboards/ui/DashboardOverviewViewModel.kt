package co.asclepio.mobile.feature.dashboards.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.core.auth.*
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.network.*
import co.asclepio.mobile.core.tenants.PublicSiteDto
import co.asclepio.mobile.core.ui.DashboardRead
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardOverviewViewModel(
    destination: StaffDestination,
    role: StaffRole,
    private val repository: DashboardDataRepository,
    changes: StaffDataChanges,
) : ViewModel() {
    private val generation = repository.sessionGeneration
    private val current: Boolean get() = generation == repository.sessionGeneration
    private val allowed = destination in role.destinations && destination in setOf(StaffDestination.MEDICINE, StaffDestination.QUEUE, StaffDestination.ADMINISTRATION, StaffDestination.REPORTS)
    private val _state = MutableStateFlow(DashboardOverviewUiState(destination))
    val state: StateFlow<DashboardOverviewUiState> = _state.asStateFlow()
    private val busy = mutableMapOf<DashboardReadKey, Long>()
    private val coordinator = DashboardRefreshCoordinator(viewModelScope) { current && allowed }
    init {
        if (!allowed) _state.update { it.copy(counts = dashboardStatuses.associateWith { DashboardRead.Forbidden }, recent = DashboardRead.Forbidden, sites = DashboardRead.Forbidden) }
        else {
            dashboardStatuses.forEach { status ->
                val key = DashboardReadKey.Count(status)
                coordinator.register(key, eligible = { (_state.value.counts[status] as? DashboardRead.Error)?.retryEnabled != false }) { revision ->
                    read(key, revision, { repository.count(status) }, { remote -> _state.update { it.copy(counts = it.counts + (status to remote)) } }, { _state.update { state -> val old = state.counts[status] as? DashboardRead.Error; if (old == null) state else state.copy(counts = state.counts + (status to old.copy(retryEnabled = true))) } })
                }
            }
            if (_state.value.administration) registerSites()
            else coordinator.register(DashboardReadKey.Recent, eligible = { (_state.value.recent as? DashboardRead.Error)?.retryEnabled != false }) { revision ->
                read(DashboardReadKey.Recent, revision, repository::recent, { remote -> _state.update { it.copy(recent = remote) } }, { _state.update { state -> val old = state.recent as? DashboardRead.Error; if (old == null) state else state.copy(recent = old.copy(retryEnabled = true)) } })
            }
        }
        viewModelScope.launch {
            changes.changes.collect { change ->
                if (current && change.generation == generation && StaffResource.Encounters in change.resources) {
                    val keys = dashboardStatuses.map { DashboardReadKey.Count(it) }.toMutableSet<DashboardReadKey>()
                    if (_state.value.administration) keys += _state.value.siteCounts.keys.map { DashboardReadKey.Site(it) }
                    else keys += DashboardReadKey.Recent
                    coordinator.invalidate(keys)
                }
            }
        }
    }
    fun setActive(value: Boolean) = coordinator.setActive(value && allowed)
    fun refresh() = coordinator.refresh()
    fun retryCount(status: EncounterStatus) = coordinator.refresh(setOf(DashboardReadKey.Count(status)))
    fun retryRecent() = coordinator.refresh(setOf(DashboardReadKey.Recent))
    fun retrySites() = coordinator.refresh(setOf(DashboardReadKey.Sites))
    fun retrySite(id: String) = coordinator.refresh(setOf(DashboardReadKey.Site(id)))
    fun rememberScroll(index: Int, offset: Int) { if (current) _state.update { it.copy(scrollIndex = index, scrollOffset = offset) } }

    private fun registerSites() {
        coordinator.register(DashboardReadKey.Sites, eligible = { _state.value.sites !is DashboardRead.Ready && (_state.value.sites as? DashboardRead.Error)?.retryEnabled != false }) { revision ->
            read(DashboardReadKey.Sites, revision, repository::listSites, { remote ->
                _state.update { it.copy(sites = remote) }
                if (remote is DashboardRead.Ready) remote.data.forEach(::registerSite)
            }, { _state.update { state -> val old = state.sites as? DashboardRead.Error; if (old == null) state else state.copy(sites = old.copy(retryEnabled = true)) } })
        }
    }
    private fun registerSite(site: PublicSiteDto) {
        _state.update { if (site.id in it.siteCounts) it else it.copy(siteCounts = it.siteCounts + (site.id to DashboardRead.Loading)) }
        val key = DashboardReadKey.Site(site.id)
        coordinator.register(key, eligible = { (_state.value.siteCounts[site.id] as? DashboardRead.Error)?.retryEnabled != false }) { revision ->
            read(key, revision, { repository.siteSummary(site) }, { remote -> _state.update { it.copy(siteCounts = it.siteCounts + (site.id to remote)) } }, { _state.update { state -> val old = state.siteCounts[site.id] as? DashboardRead.Error; if (old == null) state else state.copy(siteCounts = state.siteCounts + (site.id to old.copy(retryEnabled = true))) } })
        }
    }
    private suspend fun <T> read(
        key: DashboardReadKey, revision: Long, request: suspend () -> ApiResult<T>, publish: (DashboardRead<T>) -> Unit, unlockRetry: () -> Unit,
    ): Boolean {
        busy[key] = revision
        if (current) _state.update { it.copy(refreshing = true) }
        try {
            val result = request()
            if (!coordinator.isLatest(key, revision)) return false
            when (result) {
                is ApiResult.Success -> publish(DashboardRead.Ready(result.value))
                is ApiResult.Failure -> {
                    val wait = result.error.rateLimitSeconds()
                    publish(if (result.error.httpStatus == 403) DashboardRead.Forbidden else DashboardRead.Error(result.error.commonMessage(), retryEnabled = wait == null))
                    if (wait != null) viewModelScope.launch { delay(wait * 1000L); if (current) unlockRetry() }
                    return result.error.httpStatus == 403
                }
            }
            return false
        } finally {
            if (busy[key] == revision) busy.remove(key)
            if (current) _state.update { it.copy(refreshing = busy.isNotEmpty()) }
        }
    }
}
