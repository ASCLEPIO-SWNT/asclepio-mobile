package co.asclepio.mobile.feature.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.model.datePickerMillisToLocalDate
import co.asclepio.mobile.core.model.toDatePickerMillis
import co.asclepio.mobile.core.ui.PaneMode
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.core.ui.PrivateDatePicker
import co.asclepio.mobile.core.ui.rememberPrivateDatePickerState
import co.asclepio.mobile.core.ui.markdownToAnnotated
import co.asclepio.mobile.core.ui.paneMode
import co.asclepio.mobile.core.ui.text
import co.asclepio.mobile.feature.account.data.DocumentType
import java.time.format.DateTimeFormatter

private val birthDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Alta: formulario y consentimiento en la misma pantalla; en tablet, lado a lado (R6, R7, R35). */
@Composable
fun SignUpScreen(
    state: SignUpUiState,
    widthSizeClass: WindowWidthSizeClass,
    onFormChange: (SignUpForm) -> Unit,
    onSubmit: () -> Unit,
    onRetryConsent: () -> Unit,
    onGoToLogin: () -> Unit,
    onNavigatedToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.navigateToLogin) {
        LaunchedEffect(Unit) {
            onNavigatedToLogin()
            onGoToLogin()
        }
    }
    val form: @Composable (Modifier) -> Unit = { m -> SignUpFormPane(state, onFormChange, onSubmit, onGoToLogin, m) }
    val consent: @Composable (Modifier) -> Unit = { m -> ConsentPane(state.consent, onRetryConsent, m) }
    if (paneMode(widthSizeClass) == PaneMode.Dual) {
        Row(modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            form(Modifier.weight(1f).verticalScroll(rememberScrollState()))
            consent(Modifier.weight(1f).verticalScroll(rememberScrollState()))
        }
    } else {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            consent(Modifier)
            form(Modifier)
        }
    }
}

@Composable
private fun ConsentPane(consent: ConsentUiState, onRetry: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.signup_consent_title), style = MaterialTheme.typography.titleMedium)
            when (consent) {
                ConsentUiState.Loading -> CircularProgressIndicator()
                is ConsentUiState.Error -> {
                    Text(consent.message.text(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry, enabled = consent.retryEnabled) { Text(stringResource(R.string.retry)) }
                }
                is ConsentUiState.Loaded -> {
                    val annotated = remember(consent.text) { markdownToAnnotated(consent.text) }
                    Text(annotated, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SignUpFormPane(
    state: SignUpUiState,
    onChange: (SignUpForm) -> Unit,
    onSubmit: () -> Unit,
    onGoToLogin: () -> Unit,
    modifier: Modifier,
) {
    val form = state.form
    val errors = state.fieldErrors
    val enabled = !state.submitting
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.signup_title), style = MaterialTheme.typography.headlineSmall)
        DocumentTypeSelector(form.documentType, errors[SignUpField.DocumentType], enabled) { onChange(form.copy(documentType = it)) }
        FormTextField(form.documentNumber, R.string.field_document_number, errors[SignUpField.DocumentNumber], enabled) {
            onChange(form.copy(documentNumber = it))
        }
        FormTextField(form.fullName, R.string.field_full_name, errors[SignUpField.FullName], enabled) {
            onChange(form.copy(fullName = it))
        }
        BirthDateField(form, errors[SignUpField.BirthDate], enabled, onChange)
        SexSelector(form.sex, errors[SignUpField.Sex], enabled) { onChange(form.copy(sex = it)) }
        FormTextField(form.password, R.string.field_password, errors[SignUpField.Password], enabled, password = true) {
            onChange(form.copy(password = it))
        }
        FormTextField(
            form.passwordConfirmation,
            R.string.field_password_confirmation,
            errors[SignUpField.PasswordConfirmation],
            enabled,
            password = true,
        ) { onChange(form.copy(passwordConfirmation = it)) }
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = form.consentAccepted,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onChange(form.copy(consentAccepted = it)) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = form.consentAccepted, onCheckedChange = null, enabled = enabled)
            Text(stringResource(R.string.signup_consent_accept), Modifier.padding(start = 8.dp))
        }
        state.message?.let { Text(it.text(), color = MaterialTheme.colorScheme.error) }
        Button(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
            if (state.submitting) CircularProgressIndicator(Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.signup_submit))
        }
        if (state.showGoToLogin) {
            OutlinedButton(onClick = onGoToLogin, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.signup_go_to_login))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateField(form: SignUpForm, error: UiMessage?, enabled: Boolean, onChange: (SignUpForm) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.field_birth_date), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, enabled = enabled) {
            Text(form.birthDate?.format(birthDateFormat) ?: stringResource(R.string.field_birth_date_choose))
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
    val labels = mapOf(Sex.M to R.string.sex_m, Sex.F to R.string.sex_f, Sex.X to R.string.sex_x)
    OptionGroup(R.string.field_sex, Sex.entries, labels, selected, error, enabled, onSelect)
}

@Composable
internal fun DocumentTypeSelector(selected: DocumentType?, error: UiMessage?, enabled: Boolean, onSelect: (DocumentType) -> Unit) {
    val labels = mapOf(
        DocumentType.CC to R.string.document_type_cc,
        DocumentType.CE to R.string.document_type_ce,
        DocumentType.PA to R.string.document_type_pa,
    )
    OptionGroup(R.string.field_document_type, DocumentType.entries, labels, selected, error, enabled, onSelect)
}

@Composable
private fun <T> OptionGroup(
    title: Int,
    options: List<T>,
    labels: Map<T, Int>,
    selected: T?,
    error: UiMessage?,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
        options.forEach { option ->
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
                Text(stringResource(labels.getValue(option)), Modifier.padding(start = 8.dp))
            }
        }
        error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun FormTextField(
    value: String,
    label: Int,
    error: UiMessage?,
    enabled: Boolean,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(it.text()) } },
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}
