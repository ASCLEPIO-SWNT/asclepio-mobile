package co.asclepio.mobile.feature.reception.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.encounters.ui.StaffSitesUiState
import co.asclepio.mobile.core.network.*
import co.asclepio.mobile.core.ui.PrivateDatePickerDraft
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.reception.data.ReceptionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock

enum class PatientSearchStatus { Idle, Loading, Found, Empty, Error }
enum class RegistrationUnavailable { Forbidden, NotFound }
data class DirectRegistrationUiState(
    val search: StaffSearchForm = StaffSearchForm(),
    val searchStatus: PatientSearchStatus = PatientSearchStatus.Idle,
    val searchErrors: Map<StaffSearchField, UiMessage> = emptyMap(),
    val searchMessage: UiMessage? = null,
    val patientForm: StaffPatientForm? = null,
    val datePicker: PrivateDatePickerDraft = PrivateDatePickerDraft(),
    val patientErrors: Map<StaffPatientField, UiMessage> = emptyMap(),
    val patientMessage: UiMessage? = null,
    val selected: StaffPatientDto? = null,
    val sites: StaffSitesUiState = StaffSitesUiState(),
    val arrival: StaffArrivalForm = StaffArrivalForm(),
    val arrivalErrors: Map<StaffArrivalField, UiMessage> = emptyMap(),
    val arrivalMessage: UiMessage? = null,
    val creating: Boolean = false,
    val registering: Boolean = false,
    val searchBlocked: Boolean = false,
    val createBlocked: Boolean = false,
    val arrivalBlocked: Boolean = false,
    val createdEncounterId: String? = null,
    val unavailable: RegistrationUnavailable? = null,
) {
    val busy: Boolean get() = searchStatus == PatientSearchStatus.Loading || creating || registering
    override fun toString() = "DirectRegistrationUiState(searchStatus=$searchStatus, private=***, busy=$busy, unavailable=$unavailable)"
}

/** One flow-scoped VM; changing patient context never reuses another patient's arrival draft. */
class DirectRegistrationViewModel(
    private val repository: ReceptionRepository,
    private val encounters: EncountersRepository,
    private val sitesRepository: StaffSitesRepository,
    private val clock: Clock,
) : ViewModel() {
    private val originGeneration = repository.sessionGeneration
    private val _state = MutableStateFlow(DirectRegistrationUiState())
    val state: StateFlow<DirectRegistrationUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var sitesJob: Job? = null
    private var revision = 0L
    private var searched: StaffSearchForm? = null
    private val current: Boolean get() = repository.sessionGeneration == originGeneration
    private val editable: Boolean get() = current && !_state.value.busy && _state.value.unavailable == null

    init { retrySites() }

    fun updateSearch(form: StaffSearchForm) {
        if (!editable) return
        if (form == _state.value.search) return
        revision++
        searchJob?.cancel()
        searched = null
        _state.update { it.copy(search = form, searchStatus = PatientSearchStatus.Idle, searchErrors = emptyMap(), searchMessage = null, selected = null, patientForm = null, datePicker = PrivateDatePickerDraft(), arrival = StaffArrivalForm(), arrivalErrors = emptyMap(), arrivalMessage = null) }
    }
    fun search() {
        if (!editable || _state.value.searchBlocked) return
        val form = _state.value.search
        val errors = validateStaffSearch(form)
        if (errors.isNotEmpty()) { _state.update { it.copy(searchErrors = errors, searchMessage = null) }; return }
        val request = ++revision
        searched = form.copy(documentNumber = form.documentNumber.trim())
        _state.update { it.copy(searchStatus = PatientSearchStatus.Loading, searchErrors = emptyMap(), searchMessage = null, selected = null, patientForm = null, datePicker = PrivateDatePickerDraft(), arrival = StaffArrivalForm(), arrivalErrors = emptyMap(), arrivalMessage = null) }
        searchJob = viewModelScope.launch {
            val result = repository.search(checkNotNull(form.documentType), form.documentNumber)
            if (!current || request != revision) return@launch
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(searchStatus = if (result.value.isEmpty()) PatientSearchStatus.Empty else PatientSearchStatus.Found, selected = result.value.firstOrNull()) }
                is ApiResult.Failure -> {
                    val error = result.error
                    val fields = (error as? ApiError.Http)?.problem?.errors.orEmpty().mapNotNull { field ->
                        when (field.field) { "documentType" -> StaffSearchField.DocumentType; "documentNumber" -> StaffSearchField.DocumentNumber; else -> null }
                    }.associateWith { UiMessage(R.string.staff_error_field) }
                    _state.update { it.copy(searchStatus = PatientSearchStatus.Error, searchErrors = fields, searchMessage = error.commonMessage(), unavailable = unavailable(error)) }
                    error.rateLimitSeconds()?.let { wait ->
                        _state.update { it.copy(searchBlocked = true) }
                        delay(wait * 1000L)
                        if (current && request == revision) _state.update { it.copy(searchBlocked = false) }
                    }
                }
            }
        }
    }
    fun startDocumentedPatient() {
        if (!editable || _state.value.searchStatus != PatientSearchStatus.Empty) return
        val search = searched ?: return
        _state.update { it.copy(patientForm = StaffPatientForm(StaffPatientMode.Document(checkNotNull(search.documentType), search.documentNumber)), patientErrors = emptyMap(), patientMessage = null, datePicker = PrivateDatePickerDraft(), selected = null, arrival = StaffArrivalForm()) }
    }
    fun startNnPatient() {
        if (!editable) return
        revision++
        searched = null
        _state.update { it.copy(searchStatus = PatientSearchStatus.Idle, patientForm = StaffPatientForm(StaffPatientMode.Nn), patientErrors = emptyMap(), patientMessage = null, datePicker = PrivateDatePickerDraft(), selected = null, arrival = StaffArrivalForm(), arrivalErrors = emptyMap(), arrivalMessage = null) }
    }
    fun cancelPatientCreation() {
        if (editable) _state.update { it.copy(patientForm = null, datePicker = PrivateDatePickerDraft(), patientErrors = emptyMap(), patientMessage = null) }
    }
    fun updatePatient(form: StaffPatientForm) {
        if (!editable || _state.value.patientForm?.mode != form.mode) return
        _state.update { it.copy(patientForm = form, patientErrors = emptyMap(), patientMessage = null) }
    }
    fun updateDatePicker(draft: PrivateDatePickerDraft) {
        if (editable && _state.value.patientForm != null) _state.update { it.copy(datePicker = draft) }
    }
    fun createPatient() {
        if (!editable || _state.value.createBlocked) return
        val form = _state.value.patientForm ?: return
        val errors = validateStaffPatient(form, clock)
        if (errors.isNotEmpty()) { _state.update { it.copy(patientErrors = errors, patientMessage = null) }; return }
        val request = revision
        _state.update { it.copy(creating = true, patientErrors = emptyMap(), patientMessage = null) }
        viewModelScope.launch {
            val result = repository.createPatient(form.toRequest())
            if (!current || request != revision) return@launch
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(creating = false, selected = result.value, patientForm = null, datePicker = PrivateDatePickerDraft(), searchStatus = PatientSearchStatus.Found, arrival = StaffArrivalForm()) }
                is ApiResult.Failure -> {
                    val error = result.error
                    val fields = (error as? ApiError.Http)?.problem?.errors.orEmpty().mapNotNull { field ->
                        when (field.field) { "fullName" -> StaffPatientField.FullName; "birthDate" -> StaffPatientField.BirthDate; "sex" -> StaffPatientField.Sex; "estimatedAgeYears" -> StaffPatientField.EstimatedAge; else -> null }
                    }.associateWith { UiMessage(R.string.staff_error_field) }.toMutableMap()
                    if (error.httpStatus == 422) fields[StaffPatientField.BirthDate] = UiMessage(R.string.staff_birth_future)
                    val message = if (error.httpStatus == 409) UiMessage(R.string.staff_patient_conflict) else error.commonMessage()
                    _state.update { it.copy(creating = false, patientErrors = fields, patientMessage = message, unavailable = unavailable(error), createBlocked = error.rateLimitSeconds() != null) }
                    error.rateLimitSeconds()?.let { wait -> delay(wait * 1000L); if (current && request == revision) _state.update { it.copy(createBlocked = false) } }
                }
            }
        }
    }
    fun updateArrival(form: StaffArrivalForm) {
        if (!editable || _state.value.selected == null) return
        if (form.siteId != null && _state.value.sites.sites.none { it.id == form.siteId }) return
        _state.update { it.copy(arrival = form, arrivalErrors = emptyMap(), arrivalMessage = null) }
    }
    fun registerArrival() {
        if (!editable || _state.value.arrivalBlocked || !_state.value.sites.available) return
        val s = _state.value
        val patient = s.selected ?: return
        val errors = validateStaffArrival(s.arrival)
        if (errors.isNotEmpty()) { _state.update { it.copy(arrivalErrors = errors, arrivalMessage = null) }; return }
        if (s.sites.sites.none { it.id == s.arrival.siteId }) return
        val request = revision
        _state.update { it.copy(registering = true, arrivalErrors = emptyMap(), arrivalMessage = null) }
        viewModelScope.launch {
            val result = encounters.createDirect(s.arrival.toRequest(patient.id))
            if (!current || request != revision) return@launch
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(registering = false, selected = null, patientForm = null, arrival = StaffArrivalForm(), createdEncounterId = result.value.id, arrivalMessage = UiMessage(R.string.staff_arrival_registered)) }
                is ApiResult.Failure -> {
                    val error = result.error
                    val fields = (error as? ApiError.Http)?.problem?.errors.orEmpty().mapNotNull { field ->
                        when (field.field) { "siteId" -> StaffArrivalField.Site; "chiefComplaintText" -> StaffArrivalField.ChiefComplaint; else -> null }
                    }.associateWith { UiMessage(R.string.staff_error_field) }
                    val message = when (error.httpStatus) { 409 -> UiMessage(R.string.staff_arrival_conflict); 422 -> UiMessage(R.string.staff_arrival_invalid); else -> error.commonMessage() }
                    _state.update { it.copy(registering = false, arrivalErrors = fields, arrivalMessage = message, unavailable = unavailable(error), arrivalBlocked = error.rateLimitSeconds() != null) }
                    if (error.httpStatus == 422) retrySites()
                    error.rateLimitSeconds()?.let { wait -> delay(wait * 1000L); if (current && request == revision) _state.update { it.copy(arrivalBlocked = false) } }
                }
            }
        }
    }
    fun consumeCreatedEncounter() { if (current) _state.update { it.copy(createdEncounterId = null) } }
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
    private fun unavailable(error: ApiError) = when (error.httpStatus) { 403 -> RegistrationUnavailable.Forbidden; 404 -> RegistrationUnavailable.NotFound; else -> null }
}
