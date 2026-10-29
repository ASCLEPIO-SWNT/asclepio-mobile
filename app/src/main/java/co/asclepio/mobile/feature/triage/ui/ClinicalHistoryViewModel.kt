package co.asclepio.mobile.feature.triage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.network.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ClinicalHistoryViewModel(
    private val id: String,
    private val role: StaffRole,
    private val repository: ClinicalCaptureRepository,
    changes: StaffDataChanges,
) : ViewModel() {
    private val generation = repository.sessionGeneration
    private val _state = MutableStateFlow<ClinicalRead<ClinicalCaptureDto>>(ClinicalRead.Loading)
    val state: StateFlow<ClinicalRead<ClinicalCaptureDto>> = _state.asStateFlow()
    private var active = false
    private var revision = 0L
    private var job: Job? = null
    private val current: Boolean get() = generation == repository.sessionGeneration
    init {
        viewModelScope.launch { changes.changes.collect { event ->
            if (current && active && event.generation == generation && event.encounterId == id && StaffResource.ClinicalCapture in event.resources) load()
        } }
    }
    fun setActive(value: Boolean) { if (active != value) { active = value; if (value) refresh() } }
    fun refresh() { if ((_state.value as? ClinicalRead.Error)?.retryEnabled != false) load() }
    private fun load() {
        if (!current || !active) return
        if (role !in setOf(StaffRole.NURSE, StaffRole.PHYSICIAN)) { _state.value = ClinicalRead.Forbidden; return }
        val request = ++revision
        job?.cancel()
        _state.value = ClinicalRead.Loading
        job = viewModelScope.launch {
            val result = repository.getCapture(id)
            if (!current || request != revision) return@launch
            when (result) {
                is ApiResult.Success -> _state.value = if (result.value.encounterId == id) ClinicalRead.Ready(result.value, request) else ClinicalRead.Error(ApiError.InvalidBody.commonMessage())
                is ApiResult.Failure -> {
                    val wait = result.error.rateLimitSeconds()
                    _state.value = when (result.error.httpStatus) {
                        403 -> ClinicalRead.Forbidden
                        404 -> ClinicalRead.NotFound
                        else -> ClinicalRead.Error(result.error.commonMessage(), wait == null)
                    }
                    if (wait != null) {
                        delay(wait * 1000L)
                        if (current && request == revision) (_state.value as? ClinicalRead.Error)?.let { _state.value = it.copy(retryEnabled = true) }
                    }
                }
            }
        }
    }
}
