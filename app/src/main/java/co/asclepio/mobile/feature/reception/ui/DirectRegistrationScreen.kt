package co.asclepio.mobile.feature.reception.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.*

@Composable
fun DirectRegistrationScreen(
    state: DirectRegistrationUiState,
    onSearchChange: (StaffSearchForm) -> Unit, onSearch: () -> Unit,
    onStartDocumented: () -> Unit, onStartNn: () -> Unit,
    onPatientChange: (StaffPatientForm) -> Unit, onDatePickerChange: (PrivateDatePickerDraft) -> Unit,
    onCreatePatient: () -> Unit, onCancelPatient: () -> Unit,
    onArrivalChange: (StaffArrivalForm) -> Unit, onRegisterArrival: () -> Unit,
    onRetrySites: () -> Unit, onReception: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        PageHeader(stringResource(R.string.staff_registration_direct))
        if (state.unavailable != null) {
            UiNotice(stringResource(if (state.unavailable == RegistrationUnavailable.Forbidden) R.string.access_denied else R.string.staff_value_unavailable), NoticeTone.Error)
        } else {
            StaffSearchFields(state.search, state.searchErrors, state.busy || state.searchBlocked, onSearchChange, onSearch, onStartNn)
            state.searchMessage?.let { UiNotice(it.text(), NoticeTone.Error) }
            if (state.searchStatus == PatientSearchStatus.Empty && state.patientForm == null && state.selected == null) {
                UiNotice(stringResource(R.string.staff_no_patient))
                AsclepioButton(onClick = onStartDocumented, enabled = !state.busy) { Text(stringResource(R.string.staff_patient_create)) }
            }
            state.patientForm?.let { form ->
                HorizontalDivider()
                StaffPatientFields(form, state.datePicker, state.patientErrors, state.creating, state.createBlocked, onPatientChange, onDatePickerChange, onCreatePatient, onCancelPatient)
                state.patientMessage?.let { UiNotice(it.text(), NoticeTone.Error) }
            }
            state.selected?.let { patient ->
                HorizontalDivider()
                StaffArrivalFields(patient, state.arrival, state.sites, state.arrivalErrors, state.registering, state.arrivalBlocked, onArrivalChange, onRegisterArrival, onRetrySites)
            }
            state.arrivalMessage?.let {
                UiNotice(it.text(), if (it.resId == R.string.staff_arrival_registered) NoticeTone.Success else NoticeTone.Error)
                if (it.resId == R.string.staff_arrival_conflict) TextButton(onClick = onReception) { Text(stringResource(R.string.staff_tray_go)) }
            }
        }
    }
}
