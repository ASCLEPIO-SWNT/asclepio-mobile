package co.asclepio.mobile.feature.patients.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.R
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.rateLimitSeconds
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.core.ui.PrivateDatePickerDraft
import co.asclepio.mobile.feature.patients.data.PatientProfileDto
import co.asclepio.mobile.feature.patients.data.PatientProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    /** El perfil no se pudo cargar (R24); `retryEnabled` es `false` mientras dura un 429. */
    data class Error(val message: UiMessage, val retryEnabled: Boolean) : ProfileUiState

    data class Success(
        val loaded: PatientProfileDto,
        val form: ProfileForm,
        val datePicker: PrivateDatePickerDraft = PrivateDatePickerDraft(),
        val fieldErrors: Map<ProfileField, UiMessage> = emptyMap(),
        /** Mensaje general: "Datos guardados" (R3) o un error (R7, R24). */
        val message: UiMessage? = null,
        val saving: Boolean = false,
        val saveBlocked: Boolean = false,
        /** R6: la API respondió 409 (datos verificados); se mantiene el aviso aunque el perfil siga marcado editable. */
        val lockedByConflict: Boolean = false,
        val reloading: Boolean = false,
    ) : ProfileUiState {
        val display: ProfileDisplay get() = profileDisplay(loaded)

        /** R2, R6. */
        val editable: Boolean get() = loaded.editable && !lockedByConflict
        val showLockedNotice: Boolean get() = !editable

        /** R4: algún campo editable difiere del perfil cargado. */
        val hasChanges: Boolean get() = !diff(loaded, form).isEmpty

        /** R2, R4, R24, R25. */
        val canSave: Boolean get() = editable && hasChanges && !saving && !saveBlocked && !reloading
    }
}

/** "Mis datos" (R1–R7, R24, R25). El perfil y el formulario solo viven en memoria (R27). */
class ProfileViewModel(private val repository: PatientProfileRepository, private val clock: Clock) : ViewModel() {
    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    /** R6: el 409 sigue vigente si la recarga posterior falla y se reintenta. */
    private var lockedByConflict = false

    init {
        load()
    }

    fun retry() {
        val s = _state.value
        if (s is ProfileUiState.Error && s.retryEnabled) load(lockedByConflict)
    }

    private fun load(lockedByConflict: Boolean = false) {
        this.lockedByConflict = lockedByConflict
        if (_state.value !is ProfileUiState.Success) _state.value = ProfileUiState.Loading
        viewModelScope.launch {
            when (val result = repository.profile()) {
                is ApiResult.Success -> _state.value = ProfileUiState.Success(
                    loaded = result.value,
                    form = result.value.toForm(),
                    lockedByConflict = lockedByConflict,
                )
                is ApiResult.Failure -> {
                    val wait = result.error.rateLimitSeconds()
                    val error = ProfileUiState.Error(profileLoadErrorMessage(result.error), retryEnabled = wait == null)
                    _state.value = error
                    if (wait != null) {
                        delay(wait * 1_000L)
                        _state.update { if (it == error) error.copy(retryEnabled = true) else it }
                    }
                }
            }
        }
    }

    /** Cambia el formulario y quita el error de los campos que cambiaron. Sin efecto si no es editable o se está guardando. */
    fun updateForm(form: ProfileForm) = _state.update { s ->
        if (s !is ProfileUiState.Success || !s.editable || s.saving || s.reloading) return@update s
        val changed = buildSet {
            if (s.form.fullName != form.fullName) add(ProfileField.FullName)
            if (s.form.birthDate != form.birthDate) add(ProfileField.BirthDate)
            if (s.form.sex != form.sex) add(ProfileField.Sex)
        }
        val message = s.message.takeUnless { it?.resId == R.string.profile_saved && changed.isNotEmpty() }
        s.copy(form = form, fieldErrors = s.fieldErrors - changed, message = message)
    }

    fun updateDatePicker(draft: PrivateDatePickerDraft) = _state.update { state ->
        if (state is ProfileUiState.Success && state.editable && !state.saving && !state.reloading) state.copy(datePicker = draft) else state
    }

    fun save() {
        val s = _state.value as? ProfileUiState.Success ?: return
        if (!s.canSave) return
        val errors = validateProfile(s.form, clock)
        if (errors.isNotEmpty()) {
            _state.value = s.copy(fieldErrors = errors, message = null)
            return
        }
        val request = diff(s.loaded, s.form)
        _state.value = s.copy(saving = true, fieldErrors = emptyMap(), message = null)
        viewModelScope.launch {
            when (val result = repository.update(request)) {
                is ApiResult.Success -> _state.value = ProfileUiState.Success(
                    loaded = result.value,
                    form = result.value.toForm(),
                    message = UiMessage(R.string.profile_saved),
                )
                is ApiResult.Failure -> onSaveFailed(result.error)
            }
        }
    }

    private suspend fun onSaveFailed(error: ApiError) {
        val mapped = profileErrorMessage(error)
        if (mapped.locked) {
            // R6: aviso de R2 y nuevo GET; los datos que se muestran son los recibidos.
            _state.update { (it as ProfileUiState.Success).copy(saving = false, lockedByConflict = true, reloading = true) }
            load(lockedByConflict = true)
            return
        }
        val wait = error.rateLimitSeconds()
        _state.update {
            (it as ProfileUiState.Success).copy(
                saving = false,
                fieldErrors = mapped.fieldErrors,
                message = mapped.message,
                saveBlocked = wait != null,
            )
        }
        if (wait != null) {
            delay(wait * 1_000L)
            _state.update { (it as? ProfileUiState.Success)?.copy(saveBlocked = false) ?: it }
        }
    }
}
