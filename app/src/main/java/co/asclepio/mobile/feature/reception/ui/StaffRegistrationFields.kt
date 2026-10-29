package co.asclepio.mobile.feature.reception.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.StaffDocumentType
import co.asclepio.mobile.core.encounters.StaffPatientDto
import co.asclepio.mobile.core.encounters.ui.*
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.ui.*

@Composable
fun StaffSearchFields(
    form: StaffSearchForm, errors: Map<StaffSearchField, UiMessage>, busy: Boolean,
    onChange: (StaffSearchForm) -> Unit, onSearch: () -> Unit, onNn: () -> Unit,
) {
    Text(stringResource(R.string.staff_patient_search), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    StaffChoiceField(
        label = stringResource(R.string.field_document_type), selected = form.documentType,
        options = listOf(null to stringResource(R.string.staff_choose)) + StaffDocumentType.entries.map { it to stringResource(when (it) {
            StaffDocumentType.CC -> R.string.document_type_cc
            StaffDocumentType.TI -> R.string.document_type_ti
            StaffDocumentType.CE -> R.string.document_type_ce
            StaffDocumentType.PA -> R.string.document_type_pa
            StaffDocumentType.RC -> R.string.document_type_rc
        }) },
        enabled = !busy, error = errors[StaffSearchField.DocumentType]?.text(), onSelect = { onChange(form.copy(documentType = it)) },
    )
    AsclepioTextField(
        form.documentNumber, { onChange(form.copy(documentNumber = it)) }, Modifier.fillMaxWidth(), enabled = !busy,
        label = { Text(stringResource(R.string.field_document_number)) }, singleLine = true,
        isError = errors[StaffSearchField.DocumentNumber] != null, supportingText = errors[StaffSearchField.DocumentNumber]?.let { { Text(it.text()) } },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AsclepioButton(onClick = onSearch, enabled = !busy) { Text(stringResource(if (busy) R.string.staff_searching else R.string.staff_search)) }
        OutlinedButton(onClick = onNn, enabled = !busy) { Text(stringResource(R.string.staff_patient_nn_register)) }
    }
}

@Composable
fun StaffPatientFields(
    form: StaffPatientForm, datePicker: PrivateDatePickerDraft, errors: Map<StaffPatientField, UiMessage>, busy: Boolean, blocked: Boolean,
    onChange: (StaffPatientForm) -> Unit, onDatePickerChange: (PrivateDatePickerDraft) -> Unit, onSave: () -> Unit, onCancel: () -> Unit,
) {
    Text(stringResource(if (form.mode is StaffPatientMode.Nn) R.string.staff_patient_nn_register else R.string.staff_patient_create), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    (form.mode as? StaffPatientMode.Document)?.let {
        StaffFact(stringResource(R.string.staff_document), "${it.type.name} ${it.number}")
        listOfNotNull(errors[StaffPatientField.DocumentType], errors[StaffPatientField.DocumentNumber]).distinct().forEach { error ->
            Text(error.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    AsclepioTextField(
        value = form.fullName, onValueChange = { onChange(form.copy(fullName = it)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
        label = { Text(stringResource(R.string.field_full_name)) }, singleLine = true, isError = errors[StaffPatientField.FullName] != null,
        supportingText = if (errors[StaffPatientField.FullName] != null) ({ Text(errors.getValue(StaffPatientField.FullName).text()) }) else if (form.mode is StaffPatientMode.Nn) ({ Text(stringResource(R.string.staff_nn_optional)) }) else null,
    )
    StaffChoiceField(
        label = stringResource(R.string.field_sex), selected = form.sex,
        options = listOf(null to stringResource(R.string.staff_choose)) + Sex.entries.map { it to stringResource(when (it) { Sex.M -> R.string.sex_m; Sex.F -> R.string.sex_f; Sex.X -> R.string.sex_x }) },
        enabled = !busy, error = errors[StaffPatientField.Sex]?.text(), onSelect = { onChange(form.copy(sex = it)) },
    )
    if (form.mode is StaffPatientMode.Nn) StaffChoiceField(
        label = stringResource(R.string.staff_age_information), selected = form.ageMode,
        options = listOf(NnAgeMode.BirthDate to stringResource(R.string.field_birth_date), NnAgeMode.EstimatedAge to stringResource(R.string.staff_estimated_age)),
        onSelect = { onChange(form.copy(ageMode = it)) }, enabled = !busy,
    )
    if (form.mode is StaffPatientMode.Document || form.ageMode == NnAgeMode.BirthDate) {
        BirthDateField(form.birthDate, datePicker, errors[StaffPatientField.BirthDate], !busy, { onChange(form.copy(birthDate = it)) }, onDatePickerChange)
    } else AsclepioTextField(
        value = form.estimatedAge, onValueChange = { onChange(form.copy(estimatedAge = it)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
        label = { Text(stringResource(R.string.staff_estimated_age)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = errors[StaffPatientField.EstimatedAge] != null, supportingText = errors[StaffPatientField.EstimatedAge]?.let { { Text(it.text()) } },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AsclepioButton(onClick = onSave, enabled = !busy && !blocked) { Text(stringResource(if (busy) R.string.staff_patient_saving else R.string.staff_patient_save)) }
        TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.navigate_back)) }
    }
}

@Composable
fun StaffArrivalFields(
    patient: StaffPatientDto, form: StaffArrivalForm, sites: StaffSitesUiState, errors: Map<StaffArrivalField, UiMessage>, busy: Boolean, blocked: Boolean,
    onChange: (StaffArrivalForm) -> Unit, onSubmit: () -> Unit, onRetrySites: () -> Unit,
) {
    Text(stringResource(R.string.staff_patient_selected), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    StaffPatientFacts(patient)
    StaffChoiceField(
        label = stringResource(R.string.tenant_picker_sites), selected = form.siteId,
        options = listOf(null to stringResource(R.string.staff_site_choose)) + sites.sites.map { it.id to it.name },
        onSelect = { onChange(form.copy(siteId = it)) }, enabled = !busy && sites.available, error = errors[StaffArrivalField.Site]?.text(),
    )
    sites.message?.let { UiNotice(it.text(), NoticeTone.Warning) }
    if (sites.message != null || (!sites.loading && sites.sites.isEmpty())) TextButton(onClick = onRetrySites, enabled = !sites.loading && sites.retryEnabled) { Text(stringResource(R.string.retry)) }
    AsclepioTextField(
        value = form.chiefComplaintText, onValueChange = { onChange(form.copy(chiefComplaintText = it)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
        label = { Text(stringResource(R.string.preregistration_complaint)) }, minLines = 4,
        isError = errors[StaffArrivalField.ChiefComplaint] != null,
        supportingText = { Text(errors[StaffArrivalField.ChiefComplaint]?.text() ?: stringResource(R.string.preregistration_counter, form.chiefComplaintText.trim().length, 2000)) },
    )
    AsclepioButton(onClick = onSubmit, enabled = !busy && !blocked && sites.available, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (busy) R.string.staff_arrival_registering else R.string.staff_arrival_register))
    }
}
