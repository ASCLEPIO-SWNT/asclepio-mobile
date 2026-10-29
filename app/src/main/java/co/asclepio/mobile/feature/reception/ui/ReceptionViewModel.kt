package co.asclepio.mobile.feature.reception.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.encounters.ui.StaffSitesUiState
import co.asclepio.mobile.core.network.*
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.reception.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface ReceptionRemote {
    data object Loading : ReceptionRemote
    data class Ready(val items: List<PendingPreRegistrationDto>) : ReceptionRemote
    data class Error(val message: UiMessage, val retryEnabled: Boolean = true) : ReceptionRemote
    data object Forbidden : ReceptionRemote
    data object NotFound : ReceptionRemote
}
enum class ReceptionAction { Confirm, Reject }
data class ReceptionDraft(
    val identityChecked: Boolean = false,
    val rejecting: Boolean = false,
    val reason: String = "",
    val action: ReceptionAction? = null,
    val blocked: Boolean = false,
    val reasonError: UiMessage? = null,
    val message: UiMessage? = null,
) {
    override fun toString() = "ReceptionDraft(identityChecked=$identityChecked, rejecting=$rejecting, reason=***, action=$action, blocked=$blocked)"
}
sealed interface ReceptionNotice {
    data class Confirmed(val encounterId: String, val arrivalAt: String) : ReceptionNotice {
        override fun toString() = "Confirmed(context=***)"
    }
    data object Rejected : ReceptionNotice
}
data class ReceptionUiState(
    val siteId: String? = null,
    val sites: StaffSitesUiState = StaffSitesUiState(),
    val remote: ReceptionRemote = ReceptionRemote.Loading,
    val refreshing: Boolean = false,
    val drafts: Map<String, ReceptionDraft> = emptyMap(),
    val notice: ReceptionNotice? = null,
    val message: UiMessage? = null,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

/** Remote rows and id-keyed drafts have separate lifetimes; no SavedState or polling. */
class ReceptionViewModel(
    private val repository: ReceptionRepository,
    private val sitesRepository: StaffSitesRepository,
    changes: StaffDataChanges,
) : ViewModel() {
    private val originGeneration = repository.sessionGeneration
    private val _state = MutableStateFlow(ReceptionUiState())
    val state: StateFlow<ReceptionUiState> = _state.asStateFlow()
    private var active = false
    private var readJob: Job? = null
    private var readAgain = false
    private var revision = 0L
    private var sitesJob: Job? = null
    private val current: Boolean get() = repository.sessionGeneration == originGeneration

    init {
        retrySites()
        viewModelScope.launch {
            changes.changes.collect { change ->
                if (current && active && change.generation == originGeneration && StaffResource.Pending in change.resources) refresh(invalidated = true)
            }
        }
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value && current) refresh()
    }
    fun rememberScroll(index: Int, offset: Int) {
        if (current) _state.update { it.copy(scrollIndex = index, scrollOffset = offset) }
    }
    fun selectSite(siteId: String?) {
        if (!current || _state.value.siteId == siteId) return
        if (siteId != null && _state.value.sites.sites.none { it.id == siteId }) return
        revision++
        readJob?.cancel()
        readJob = null
        readAgain = false
        _state.update { it.copy(siteId = siteId, remote = ReceptionRemote.Loading, drafts = emptyMap(), notice = null, message = null, scrollIndex = 0, scrollOffset = 0) }
        refresh()
    }
    fun refresh() = refresh(invalidated = false)
    private fun refresh(invalidated: Boolean) {
        if (!current || (_state.value.remote as? ReceptionRemote.Error)?.retryEnabled == false) return
        if (readJob?.isActive == true) {
            if (invalidated) readAgain = true
            return
        }
        val request = revision
        val site = _state.value.siteId
        _state.update { it.copy(refreshing = true) }
        readJob = viewModelScope.launch {
            do {
                readAgain = false
                val result = repository.pending(site)
                if (!current || request != revision) return@launch
                when (result) {
                    is ApiResult.Success -> {
                        val ids = result.value.mapTo(mutableSetOf()) { it.id }
                        _state.update { it.copy(remote = ReceptionRemote.Ready(result.value), drafts = it.drafts.filterKeys { id -> id in ids }) }
                    }
                    is ApiResult.Failure -> {
                        val remote = when (result.error.httpStatus) {
                            403 -> ReceptionRemote.Forbidden
                            404 -> ReceptionRemote.NotFound
                            else -> ReceptionRemote.Error(result.error.commonMessage(), result.error.rateLimitSeconds() == null)
                        }
                        _state.update { it.copy(remote = remote) }
                        result.error.rateLimitSeconds()?.let { wait ->
                            viewModelScope.launch {
                                delay(wait * 1000L)
                                if (current && request == revision) _state.update { state ->
                                    val error = state.remote as? ReceptionRemote.Error
                                    if (error != null) state.copy(remote = error.copy(retryEnabled = true)) else state
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
                    if (wait != null) {
                        delay(wait * 1000L)
                        if (current) _state.update { it.copy(sites = it.sites.copy(retryEnabled = true)) }
                    }
                }
            }
        }
    }
    private fun editable(id: String): Boolean {
        val s = _state.value
        return current && (s.remote as? ReceptionRemote.Ready)?.items?.any { it.id == id } == true &&
            s.drafts[id]?.action == null
    }
    private fun updateDraft(id: String, transform: (ReceptionDraft) -> ReceptionDraft) {
        if (!editable(id)) return
        _state.update { it.copy(drafts = it.drafts + (id to transform(it.drafts[id] ?: ReceptionDraft()))) }
    }
    fun setIdentity(id: String, checked: Boolean) = updateDraft(id) { it.copy(identityChecked = checked, message = null) }
    fun setRejecting(id: String, value: Boolean) = updateDraft(id) { it.copy(rejecting = value, reasonError = null, message = null) }
    fun setReason(id: String, value: String) = updateDraft(id) { it.copy(reason = value, reasonError = null, message = null) }
    fun confirm(id: String) {
        val draft = _state.value.drafts[id] ?: return
        if (!editable(id) || _state.value.refreshing || draft.blocked || !draft.identityChecked) return
        mutate(id, ReceptionAction.Confirm)
    }
    fun reject(id: String) {
        val draft = _state.value.drafts[id] ?: return
        if (!editable(id) || _state.value.refreshing || draft.blocked || !draft.rejecting) return
        val reason = draft.reason.trim()
        val error = when {
            reason.isEmpty() -> R.string.staff_reason_required
            reason.length > 500 -> R.string.staff_reason_max
            else -> null
        }
        if (error != null) {
            updateDraft(id) { it.copy(reasonError = UiMessage(error)) }
            return
        }
        mutate(id, ReceptionAction.Reject)
    }
    private fun mutate(id: String, action: ReceptionAction) {
        val draft = _state.value.drafts.getValue(id)
        val request = revision
        _state.update { it.copy(drafts = it.drafts + (id to draft.copy(action = action, message = null)), notice = null, message = null) }
        viewModelScope.launch {
            val result = if (action == ReceptionAction.Confirm) repository.confirm(id) else repository.reject(id, draft.reason.trim())
            if (!current || request != revision) return@launch
            when (result) {
                is ApiResult.Success -> {
                    val notice = if (action == ReceptionAction.Confirm) {
                        val encounter = result.value as EncounterDto
                        ReceptionNotice.Confirmed(encounter.id, encounter.arrivalAt)
                    } else ReceptionNotice.Rejected
                    _state.update { it.copy(drafts = it.drafts - id, notice = notice) }
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    val wait = error.rateLimitSeconds()
                    val message = when (error.httpStatus) {
                        409 -> UiMessage(if (action == ReceptionAction.Confirm) R.string.staff_confirm_conflict else R.string.staff_reject_conflict)
                        404 -> UiMessage(R.string.staff_pr_gone)
                        else -> error.commonMessage()
                    }
                    val reasonError = (error as? ApiError.Http)?.problem?.errors?.any { it.field == "reason" } == true
                    _state.update { state ->
                        val previous = state.drafts[id] ?: draft
                        state.copy(
                            remote = if (error.httpStatus == 403) ReceptionRemote.Forbidden else state.remote,
                            drafts = if (error.httpStatus == 404) state.drafts - id else state.drafts + (id to previous.copy(action = null, blocked = wait != null, reasonError = if (reasonError) UiMessage(R.string.staff_error_field) else null, message = message)),
                            message = message,
                        )
                    }
                    if (error.httpStatus in setOf(409, 404)) refresh(invalidated = true)
                    if (wait != null) {
                        delay(wait * 1000L)
                        if (current && request == revision) _state.update { state ->
                            val row = state.drafts[id]
                            if (row == null) state else state.copy(drafts = state.drafts + (id to row.copy(blocked = false)))
                        }
                    }
                }
            }
        }
    }
}
