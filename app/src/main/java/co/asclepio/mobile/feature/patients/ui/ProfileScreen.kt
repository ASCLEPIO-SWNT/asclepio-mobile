package co.asclepio.mobile.feature.patients.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.ScreenColumn
import co.asclepio.mobile.core.ui.PageHeader
import co.asclepio.mobile.core.ui.UiNotice
import co.asclepio.mobile.core.ui.NoticeTone
import co.asclepio.mobile.core.ui.AsclepioTextField
import co.asclepio.mobile.core.ui.AsclepioButton
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.model.datePickerMillisToLocalDate
import co.asclepio.mobile.core.model.toDatePickerMillis
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.core.ui.PrivateDatePicker
import co.asclepio.mobile.core.ui.rememberPrivateDatePickerState
import co.asclepio.mobile.core.ui.text

/** "Mis datos" (R1–R7, R25): columna de 600 dp como máximo en teléfono y tablet (R29). */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onFormChange: (ProfileForm) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
            PageHeader(stringResource(R.string.profile_title))
            when (state) {
                ProfileUiState.Loading -> CircularProgressIndicator()
                is ProfileUiState.Error -> {
                    UiNotice(state.message.text(), NoticeTone.Error)
                    AsclepioButton(onClick = onRetry, enabled = state.retryEnabled) { Text(stringResource(R.string.retry)) }
                }
                is ProfileUiState.Success -> ProfileContent(state, onFormChange, onSave)
            }
            }
}

@Composable
private fun ProfileContent(state: ProfileUiState.Success, onFormChange: (ProfileForm) -> Unit, onSave: () -> Unit) {
    val display = state.display
    if (state.showLockedNotice) {
        Card(Modifier.fillMaxWidth()) { Text(stringResource(R.string.profile_locked), Modifier.padding(16.dp)) }
    }
    ReadOnlyValue(R.string.field_document_type, display.documentType)
    ReadOnlyValue(R.string.field_document_number, display.documentNumber)
    if (!state.editable) {
        ReadOnlyValue(R.string.field_full_name, display.fullName)
        ReadOnlyValue(R.string.field_birth_date, display.birthDate)
        ReadOnlyValue(R.string.field_sex, display.sex)
        return
    }
    val form = state.form
    val enabled = !state.saving && !state.reloading
    val nameError = state.fieldErrors[ProfileField.FullName]
    AsclepioTextField(
        value = form.fullName,
        onValueChange = { onFormChange(form.copy(fullName = it)) },
        label = { Text(stringResource(R.string.field_full_name)) },
        isError = nameError != null,
        supportingText = nameError?.let { { Text(it.text()) } },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    BirthDateField(form, state.fieldErrors[ProfileField.BirthDate], enabled, onFormChange)
    SexSelector(form.sex, state.fieldErrors[ProfileField.Sex], enabled) { onFormChange(form.copy(sex = it)) }
    state.message?.let {
        val color = if (it.resId == R.string.profile_saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        Text(it.text(), color = color)
    }
    AsclepioButton(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
        if (state.saving) CircularProgressIndicator(Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.profile_save))
    }
}

@Composable
private fun DisplayText.text(): String = when (this) {
    is DisplayText.Res -> stringResource(resId)
    is DisplayText.Raw -> value
}

@Composable
private fun ReadOnlyValue(label: Int, value: DisplayText) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        Text(value.text(), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateField(form: ProfileForm, error: UiMessage?, enabled: Boolean, onChange: (ProfileForm) -> Unit) {
    // Solo un indicador de diálogo abierto: la fecha vive en el ViewModel, no en el estado guardado (R27).
    var open by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.field_birth_date), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, enabled = enabled) {
            Text(form.birthDate?.let(::formatBirthDate) ?: stringResource(R.string.field_birth_date_choose))
        }
        error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (open) {
        val pickerState = rememberPrivateDatePickerState(initialSelectedDateMillis = form.birthDate?.toDatePickerMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(form.copy(birthDate = datePickerMillisToLocalDate(it))) }
                    open = false
                }) { Text(stringResource(R.string.date_picker_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.logout_cancel)) } },
        ) { PrivateDatePicker(pickerState) }
    }
}

@Composable
private fun SexSelector(selected: Sex?, error: UiMessage?, enabled: Boolean, onSelect: (Sex) -> Unit) {
    Column {
        Text(stringResource(R.string.field_sex), style = MaterialTheme.typography.labelLarge)
        Sex.entries.forEach { option ->
            Row(
                Modifier.fillMaxWidth().selectable(
                    selected = option == selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onSelect(option) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null, enabled = enabled)
                Text(sexLabel(option).text(), Modifier.padding(start = 8.dp))
            }
        }
        error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
