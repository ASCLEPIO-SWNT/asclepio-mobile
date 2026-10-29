package co.asclepio.mobile.core.encounters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.encounters.ui.StaffSitesUiState
import co.asclepio.mobile.core.network.*
import co.asclepio.mobile.core.ui.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface EncounterListRemote {
    data object Loading : EncounterListRemote
    data class Ready(val items: List<EncounterSummaryDto>) : EncounterListRemote
    data class Error(val message: UiMessage, val retryEnabled: Boolean = true) : EncounterListRemote
    data object Forbidden : EncounterListRemote
    data object NotFound : EncounterListRemote
}
data class EncounterListUiState(
    val filters: EncounterFilters = EncounterFilters(),
    val sites: StaffSitesUiState = StaffSitesUiState(),
    val remote: EncounterListRemote = EncounterListRemote.Loading,
    val refreshing: Boolean = false,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
) {
    val canNext: Boolean get() = !refreshing && (remote as? EncounterListRemote.Ready)?.items?.size == 50
    val canPrevious: Boolean get() = !refreshing && filters.offset > 0
}
open class EncounterListViewModel(
    private val repository: EncountersRepository,
    private val sitesRepository: StaffSitesRepository,
    changes: StaffDataChanges,
    private val fixedStatus: EncounterStatus? = null,
) : ViewModel() {
    private val originGeneration = repository.sessionGeneration
    private val _state = MutableStateFlow(EncounterListUiState(filters = EncounterFilters(status = fixedStatus)))
    val state: StateFlow<EncounterListUiState> = _state.asStateFlow()
    private var active = false
    private var job: Job? = null
    private var sitesJob: Job? = null
    private var readAgain = false
    private var revision = 0L
    private val current: Boolean get() = repository.sessionGeneration == originGeneration
    init {
        retrySites()
        viewModelScope.launch {
            changes.changes.collect { change ->
                if (current && active && change.generation == originGeneration && StaffResource.Encounters in change.resources) refresh(true)
            }
        }
    }
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value && current) refresh()
    }
    fun rememberScroll(index: Int, offset: Int) {
        if (current && _state.value.remote is EncounterListRemote.Ready) _state.update { it.copy(scrollIndex = index, scrollOffset = offset) }
    }
    fun selectSite(siteId: String?) {
        if (siteId != null && _state.value.sites.sites.none { it.id == siteId }) return
        changeFilters(_state.value.filters.copy(siteId = siteId, offset = 0))
    }
    fun selectStatus(status: EncounterStatus?) { if (fixedStatus == null) changeFilters(_state.value.filters.copy(status = status, offset = 0)) }
    fun previousPage() { if (_state.value.canPrevious) changeFilters(_state.value.filters.copy(offset = (_state.value.filters.offset - 50).coerceAtLeast(0))) }
    fun nextPage() { if (_state.value.canNext) changeFilters(_state.value.filters.copy(offset = _state.value.filters.offset + 50)) }
    private fun changeFilters(filters: EncounterFilters) {
        if (!current || filters == _state.value.filters) return
        revision++
        job?.cancel(); job = null; readAgain = false
        _state.update { it.copy(filters = filters, remote = EncounterListRemote.Loading, scrollIndex = 0, scrollOffset = 0) }
        refresh()
    }
    fun refresh() = refresh(false)
    private fun refresh(invalidated: Boolean) {
        if (!current || (_state.value.remote as? EncounterListRemote.Error)?.retryEnabled == false) return
        if (job?.isActive == true) { if (invalidated) readAgain = true; return }
        val request = revision
        val filters = _state.value.filters
        _state.update { it.copy(refreshing = true) }
        job = viewModelScope.launch {
            do {
                readAgain = false
                val result = repository.list(filters)
                if (!current || request != revision) return@launch
                when (result) {
                    is ApiResult.Success -> _state.update { it.copy(remote = EncounterListRemote.Ready(result.value.items)) }
                    is ApiResult.Failure -> {
                        val error = result.error
                        val remote = when (error.httpStatus) {
                            403 -> EncounterListRemote.Forbidden
                            404 -> EncounterListRemote.NotFound
                            else -> EncounterListRemote.Error(error.commonMessage(), error.rateLimitSeconds() == null)
                        }
                        _state.update { it.copy(remote = remote) }
                        error.rateLimitSeconds()?.let { wait ->
                            viewModelScope.launch {
                                delay(wait * 1000L)
                                if (current && request == revision) _state.update { state ->
                                    val failed = state.remote as? EncounterListRemote.Error
                                    if (failed == null) state else state.copy(remote = failed.copy(retryEnabled = true))
                                }
                            }
                        }
                    }
                }
            } while (readAgain && current && request == revision)
            if (current && request == revision) _state.update { it.copy(refreshing = false) }
        }
    }
    fun retrySites() {
        if (!current || sitesJob?.isActive == true || !_state.value.sites.retryEnabled) return
        _state.update { it.copy(sites = it.sites.copy(loading = true, message = null)) }
        sitesJob = viewModelScope.launch {
            when (val result = sitesRepository.list()) {
                is ApiResult.Success -> if (current) _state.update { it.copy(sites = StaffSitesUiState(result.value, loading = false)) }
                is ApiResult.Failure -> if (current) {
                    val wait = result.error.rateLimitSeconds()
                    _state.update { it.copy(sites = StaffSitesUiState(loading = false, message = result.error.commonMessage(), retryEnabled = wait == null)) }
                    if (wait != null) { delay(wait * 1000L); if (current) _state.update { it.copy(sites = it.sites.copy(retryEnabled = true)) } }
                }
            }
        }
    }
}
