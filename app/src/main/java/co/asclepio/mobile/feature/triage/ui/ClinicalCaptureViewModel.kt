package co.asclepio.mobile.feature.triage.ui

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

/** One encounter and one authenticated generation. No private data is serialized. */
class ClinicalCaptureViewModel(
    private val id: String,
    private val role: StaffRole,
    private val encounters: EncountersRepository,
    private val clinical: ClinicalCaptureRepository,
    changes: StaffDataChanges,
) : ViewModel() {
    private val originGeneration = clinical.sessionGeneration
    private val _state = MutableStateFlow(ClinicalCaptureUiState())
    val state: StateFlow<ClinicalCaptureUiState> = _state.asStateFlow()
    private var active = false
    private var readJob: Job? = null
    private var revision = 0L
    private var nextRowKey = 1L
    private val current: Boolean get() = clinical.sessionGeneration == originGeneration && encounters.sessionGeneration == originGeneration
    init {
        viewModelScope.launch {
            changes.changes.collect { event ->
                if (current && active && event.generation == originGeneration && event.encounterId == id &&
                    (StaffResource.ClinicalCapture in event.resources || StaffResource.Detail in event.resources)) revalidate()
            }
        }
    }
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) refresh()
    }
    fun refresh() {
        if (!current || !active || (_state.value.context as? ClinicalRead.Error)?.retryEnabled == false ||
            (_state.value.capture as? ClinicalRead.Error)?.retryEnabled == false) return
        revalidate()
    }
    /** Invalidations supersede every earlier read, including a WAITING response started before 409. */
    private fun revalidate() {
        if (!current || !active) return
        if (role != StaffRole.NURSE) { unavailable(403); return }
        val request = ++revision
        readJob?.cancel()
        _state.update { it.copy(readRevision = request, checking = true, context = ClinicalRead.Loading, capture = ClinicalRead.Loading) }
        readJob = viewModelScope.launch {
            when (val context = encounters.detail(id)) {
                is ApiResult.Success -> {
                    if (!validRead(request)) return@launch
                    if (context.value.id != id) { readFailure(ApiError.InvalidBody, request, contextFailed = true); return@launch }
                    _state.update { it.copy(context = ClinicalRead.Ready(context.value, request), readOnly = it.readOnly || context.value.status != "WAITING_TRIAGE") }
                }
                is ApiResult.Failure -> { if (validRead(request)) readFailure(context.error, request, contextFailed = true); return@launch }
            }
            when (val capture = clinical.getCapture(id)) {
                is ApiResult.Success -> if (validRead(request)) {
                    if (capture.value.encounterId != id) readFailure(ApiError.InvalidBody, request, contextFailed = false)
                    else _state.update { it.copy(capture = ClinicalRead.Ready(capture.value, request), checking = false, hasLoaded = true, readOnly = capture.value.status != "WAITING_TRIAGE" || (it.context as? ClinicalRead.Ready)?.data?.status != "WAITING_TRIAGE") }
                }
                is ApiResult.Failure -> if (validRead(request)) readFailure(capture.error, request, contextFailed = false)
            }
        }
    }
    private fun validRead(request: Long) = current && request == revision
    private fun readFailure(error: ApiError, request: Long, contextFailed: Boolean) {
        if (error.httpStatus in setOf(403, 404)) { unavailable(error.httpStatus!!); return }
        val wait = error.rateLimitSeconds()
        val failed = ClinicalRead.Error(error.commonMessage(), wait == null)
        _state.update { it.copy(context = if (contextFailed) failed else it.context, capture = failed, checking = false) }
        if (wait != null) viewModelScope.launch {
            delay(wait * 1000L)
            if (validRead(request)) _state.update { state -> state.copy(
                context = (state.context as? ClinicalRead.Error)?.copy(retryEnabled = true) ?: state.context,
                capture = (state.capture as? ClinicalRead.Error)?.copy(retryEnabled = true) ?: state.capture,
            ) }
        }
    }
    private fun unavailable(status: Int) {
        revision++
        readJob?.cancel()
        val remote = if (status == 403) ClinicalRead.Forbidden else ClinicalRead.NotFound
        _state.update { it.copy(context = remote, capture = remote, checking = false, readRevision = revision) }
    }
    fun updateVitals(raw: RawVitalSigns) {
        if (!current || _state.value.vitals.saving) return
        _state.update { it.copy(vitals = it.vitals.copy(raw = raw, errors = emptyMap(), message = null, success = false, focusField = null)) }
    }
    fun vitalsFocusHandled(version: Long) {
        if (current && _state.value.vitals.focusVersion == version) _state.update { it.copy(vitals = it.vitals.copy(focusField = null)) }
    }
    fun findingsFocusHandled(version: Long) {
        if (current && _state.value.findings.focusVersion == version) _state.update { it.copy(findings = it.findings.copy(focusPending = false, focusRowKey = null, focusField = null)) }
    }
    fun saveVitals() {
        val s = _state.value
        if (!current || !active || role != StaffRole.NURSE || !s.canWrite || s.vitals.saving || s.vitals.blocked) return
        val errors = ClinicalRules.validateVitalSigns(s.vitals.raw).mapValues { (field, error) -> validationMessage(error, field) }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(vitals = it.vitals.copy(errors = errors, message = UiMessage(R.string.clinical_review_fields), success = false, focusVersion = it.vitals.focusVersion + 1, focusField = errors.keys.first())) }; return
        }
        val body = ClinicalRules.vitalSignsRequest(s.vitals.raw) ?: return
        _state.update { it.copy(vitals = it.vitals.copy(saving = true, errors = emptyMap(), message = null, success = false)) }
        viewModelScope.launch {
            when (val result = clinical.recordVitalSigns(id, body)) {
                is ApiResult.Success -> if (current) _state.update { it.copy(vitals = VitalSignsFormUiState(success = true)) }
                is ApiResult.Failure -> if (current) {
                    val error = result.error
                    val fields = if (error.httpStatus in setOf(400, 422)) (error as? ApiError.Http)?.problem?.errors.orEmpty().mapNotNull { entry ->
                        VitalField.entries.find { it.wireName == entry.field }?.let { it to UiMessage(R.string.staff_error_field) }
                    }.toMap() else emptyMap()
                    _state.update { it.copy(vitals = it.vitals.copy(saving = false, errors = fields, message = saveMessage(error), blocked = error.rateLimitSeconds() != null, focusVersion = it.vitals.focusVersion + 1, focusField = fields.keys.firstOrNull())) }
                    handleWriteFailure(error, vital = true)
                }
            }
        }
    }
    private fun clearFindingErrors(form: FindingsFormUiState) = form.copy(rows = form.rows.map { it.copy(errors = emptyMap(), rowError = null) }, batchError = null, message = null, success = false, focusPending = false, focusRowKey = null, focusField = null)
    fun addFinding() {
        if (!current || _state.value.findings.saving || _state.value.findings.rows.size >= ClinicalRules.FINDINGS_MAX) return
        _state.update { it.copy(findings = clearFindingErrors(it.findings).let { form -> form.copy(rows = form.rows + FindingRowUiState(nextRowKey++)) }) }
    }
    fun removeFinding(key: Long) {
        if (!current || _state.value.findings.saving) return
        _state.update { it.copy(findings = clearFindingErrors(it.findings).let { form -> form.copy(rows = form.rows.filterNot { row -> row.key == key }) }) }
    }
    fun updateFinding(key: Long, raw: RawSymptomFinding) {
        if (!current || _state.value.findings.saving) return
        _state.update { it.copy(findings = clearFindingErrors(it.findings).let { form -> form.copy(rows = form.rows.map { row -> if (row.key == key) row.copy(raw = raw) else row }) }) }
    }
    fun saveFindings() {
        val s = _state.value
        if (!current || !active || role != StaffRole.NURSE || !s.canWrite || s.findings.saving || s.findings.blocked) return
        val validation = ClinicalRules.validateFindings(s.findings.rows.map { it.raw })
        if (!validation.isValid) {
            val rows = s.findings.rows.mapIndexed { i, row -> row.copy(errors = validation.rows[i].orEmpty().mapValues { (field, error) -> findingValidationMessage(field, error) }) }
            val first = rows.firstOrNull { it.errors.isNotEmpty() }
            _state.update { it.copy(findings = it.findings.copy(rows = rows, batchError = validation.batch?.let { e -> if (e == ClinicalValidationError.Count && rows.isEmpty()) UiMessage(R.string.clinical_findings_min) else UiMessage(R.string.clinical_findings_max, listOf(20)) }, message = UiMessage(R.string.clinical_review_fields), success = false, focusVersion = it.findings.focusVersion + 1, focusPending = true, focusRowKey = first?.key, focusField = first?.errors?.keys?.firstOrNull())) }; return
        }
        val body = ClinicalRules.findingsRequest(s.findings.rows.map { it.raw }) ?: return
        _state.update { it.copy(findings = it.findings.copy(saving = true, batchError = null, message = null, success = false, rows = it.findings.rows.map { row -> row.copy(errors = emptyMap(), rowError = null) })) }
        viewModelScope.launch {
            when (val result = clinical.recordFindings(id, body)) {
                is ApiResult.Success -> if (current) _state.update { it.copy(findings = FindingsFormUiState(rows = listOf(FindingRowUiState(nextRowKey++)), success = true)) }
                is ApiResult.Failure -> if (current) {
                    val error = result.error
                    var form = _state.value.findings.copy(saving = false, message = saveMessage(error), blocked = error.rateLimitSeconds() != null)
                    if (error.httpStatus in setOf(400, 422)) {
                        for (entry in (error as? ApiError.Http)?.problem?.errors.orEmpty()) {
                            val path = entry.field.orEmpty().split('.')
                            if (path == listOf("items")) form = form.copy(batchError = UiMessage(R.string.staff_error_field))
                            else if (path.size in 2..3 && path[0] == "items") {
                                val index = path[1].toIntOrNull()
                                if (index != null && index in form.rows.indices) {
                                    val field = if (path.size == 3) when (path[2]) { "kind" -> FindingField.Kind; "description" -> FindingField.Description; else -> null } else null
                                    if (path.size == 2 || field != null) form = form.copy(rows = form.rows.mapIndexed { i, row -> if (i != index) row else if (field == null) row.copy(rowError = UiMessage(R.string.staff_error_field)) else row.copy(errors = row.errors + (field to UiMessage(R.string.staff_error_field))) })
                                }
                            }
                        }
                    }
                    val first = form.rows.firstOrNull { it.rowError != null || it.errors.isNotEmpty() }
                    form = form.copy(focusVersion = form.focusVersion + 1, focusPending = first != null || form.batchError != null, focusRowKey = first?.key, focusField = first?.errors?.keys?.firstOrNull() ?: if (first != null) FindingField.Description else null)
                    val updated = form
                    _state.update { it.copy(findings = updated) }
                    handleWriteFailure(error, vital = false)
                }
            }
        }
    }
    private suspend fun handleWriteFailure(error: ApiError, vital: Boolean) {
        when (error.httpStatus) {
            403, 404 -> unavailable(error.httpStatus!!)
            409 -> revalidate()
        }
        error.rateLimitSeconds()?.let { wait ->
            delay(wait * 1000L)
            if (current) _state.update { if (vital) it.copy(vitals = it.vitals.copy(blocked = false)) else it.copy(findings = it.findings.copy(blocked = false)) }
        }
    }
    private fun saveMessage(error: ApiError) = when (error.httpStatus) {
        409 -> UiMessage(R.string.clinical_conflict)
        400, 422 -> UiMessage(R.string.clinical_review_fields)
        else -> error.commonMessage()
    }
}

internal fun validationMessage(error: ClinicalValidationError, field: VitalField): UiMessage = when (error) {
    ClinicalValidationError.Required -> UiMessage(R.string.clinical_required)
    ClinicalValidationError.Integer -> UiMessage(R.string.clinical_integer)
    ClinicalValidationError.Precision -> UiMessage(R.string.clinical_temperature_precision)
    ClinicalValidationError.Coherence -> UiMessage(R.string.clinical_pressure_order)
    ClinicalValidationError.Range -> ClinicalRules.vitalDefinitions.getValue(field).let { rule -> UiMessage(R.string.clinical_range, listOf(rule.minimum.toString(), rule.maximum.toString(), vitalUnit(field))) }
    else -> UiMessage(R.string.clinical_number)
}
internal fun findingValidationMessage(field: FindingField, error: ClinicalValidationError) = when {
    field == FindingField.Kind -> UiMessage(R.string.clinical_kind_choose)
    error == ClinicalValidationError.Required -> UiMessage(R.string.clinical_description_required)
    else -> UiMessage(R.string.clinical_description_max, listOf(500))
}
