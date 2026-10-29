package co.asclepio.mobile.feature.staffencounters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.network.*
import co.asclepio.mobile.core.ui.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface StaffEncounterRemote {
    data object Loading : StaffEncounterRemote
    data class Ready(val encounter: EncounterDto) : StaffEncounterRemote
    data class Error(val message: UiMessage, val retryEnabled: Boolean = true) : StaffEncounterRemote
    data object Forbidden : StaffEncounterRemote
    data object NotFound : StaffEncounterRemote
}
data class StaffEncounterDetailUiState(
    val role: StaffRole,
    val remote: StaffEncounterRemote = StaffEncounterRemote.Loading,
    val refreshing: Boolean = false,
    val cancelling: Boolean = false,
    val reason: String = "",
    val reasonError: UiMessage? = null,
    val submitting: Boolean = false,
    val blocked: Boolean = false,
    val message: UiMessage? = null,
    val cancelled: Boolean = false,
) {
    val canCancel: Boolean get() = role in setOf(StaffRole.RECEPTION, StaffRole.NURSE) &&
        !refreshing && !submitting && !blocked && (remote as? StaffEncounterRemote.Ready)?.encounter?.status?.let(::canCancelEncounter) == true
    override fun toString() = "StaffEncounterDetailUiState(role=$role, private=***, refreshing=$refreshing, submitting=$submitting)"
}
/** Its id and generation never change; another encounter receives another Navigation3 VM store. */
class StaffEncounterDetailViewModel(
    private val id: String,
    role: StaffRole,
    private val repository: EncountersRepository,
    changes: StaffDataChanges,
) : ViewModel() {
    private val originGeneration = repository.sessionGeneration
    private val _state = MutableStateFlow(StaffEncounterDetailUiState(role))
    val state: StateFlow<StaffEncounterDetailUiState> = _state.asStateFlow()
    private var active = false
    private var readJob: Job? = null
    private var readAgain = false
    private val current: Boolean get() = repository.sessionGeneration == originGeneration
    init {
        viewModelScope.launch {
            changes.changes.collect { change ->
                if (current && active && change.generation == originGeneration && StaffResource.Detail in change.resources && (change.encounterId == null || change.encounterId == id)) refresh(true)
            }
        }
    }
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value && current) refresh()
    }
    fun refresh() = refresh(false)
    private fun refresh(invalidated: Boolean) {
        if (!current || (_state.value.remote as? StaffEncounterRemote.Error)?.retryEnabled == false) return
        if (readJob?.isActive == true || _state.value.submitting) { if (invalidated) readAgain = true; return }
        _state.update { it.copy(refreshing = true) }
        readJob = viewModelScope.launch {
            do {
                readAgain = false
                val result = repository.detail(id)
                if (!current) return@launch
                when (result) {
                    is ApiResult.Success -> _state.update { it.copy(remote = StaffEncounterRemote.Ready(result.value)) }
                    is ApiResult.Failure -> {
                        val error = result.error
                        val remote = when (error.httpStatus) {
                            403 -> StaffEncounterRemote.Forbidden
                            404 -> StaffEncounterRemote.NotFound
                            else -> StaffEncounterRemote.Error(error.commonMessage(), error.rateLimitSeconds() == null)
                        }
                        _state.update { it.copy(remote = remote) }
                        error.rateLimitSeconds()?.let { wait ->
                            viewModelScope.launch {
                                delay(wait * 1000L)
                                if (current) _state.update { state ->
                                    val failed = state.remote as? StaffEncounterRemote.Error
                                    if (failed == null) state else state.copy(remote = failed.copy(retryEnabled = true))
                                }
                            }
                        }
                    }
                }
            } while (readAgain && current)
            if (current) _state.update { it.copy(refreshing = false) }
        }
    }
    fun setCancelling(value: Boolean) {
        if (!current || _state.value.submitting || (value && !_state.value.canCancel)) return
        _state.update { it.copy(cancelling = value, reasonError = null, message = null) }
    }
    fun setReason(reason: String) {
        if (current && _state.value.cancelling && !_state.value.submitting) _state.update { it.copy(reason = reason, reasonError = null, message = null) }
    }
    fun cancel() {
        val s = _state.value
        if (!current || !s.canCancel || !s.cancelling) return
        val reason = s.reason.trim()
        val error = when { reason.isEmpty() -> R.string.staff_reason_required; reason.length > 500 -> R.string.staff_reason_max; else -> null }
        if (error != null) { _state.update { it.copy(reasonError = UiMessage(error)) }; return }
        _state.update { it.copy(submitting = true, message = null, reasonError = null) }
        viewModelScope.launch {
            val result = repository.cancel(id, reason)
            if (!current) return@launch
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(remote = StaffEncounterRemote.Ready(result.value), submitting = false, cancelling = false, reason = "", cancelled = true, message = UiMessage(R.string.staff_encounter_cancelled)) }
                    if (active) refresh(true)
                }
                is ApiResult.Failure -> {
                    val failed = result.error
                    val remote = when (failed.httpStatus) { 403 -> StaffEncounterRemote.Forbidden; 404 -> StaffEncounterRemote.NotFound; else -> _state.value.remote }
                    val message = if (failed.httpStatus == 409) UiMessage(R.string.staff_cancel_conflict) else failed.commonMessage()
                    val reasonField = failed.httpStatus in setOf(400, 422) && (failed as? ApiError.Http)?.problem?.errors?.any { it.field == "reason" } == true
                    val wait = failed.rateLimitSeconds()
                    _state.update { it.copy(remote = remote, submitting = false, reasonError = if (reasonField) UiMessage(R.string.staff_error_field) else null, message = message, blocked = wait != null) }
                    if (failed.httpStatus == 409 || readAgain && active) refresh(true)
                    if (wait != null) { delay(wait * 1000L); if (current) _state.update { it.copy(blocked = false) } }
                }
            }
        }
    }
}
