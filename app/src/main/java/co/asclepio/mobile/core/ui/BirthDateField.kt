package co.asclepio.mobile.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val displayDateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")

/** Confirmed form value and unconfirmed modal draft have separate, VM-owned lifetimes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthDateField(
    value: LocalDate?,
    draft: PrivateDatePickerDraft,
    error: UiMessage?,
    enabled: Boolean,
    onChange: (LocalDate) -> Unit,
    onDraftChange: (PrivateDatePickerDraft) -> Unit,
) {
    Column {
        Text(stringResource(R.string.field_birth_date), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { onDraftChange(PrivateDatePickerDraft.open(value)) }, enabled = enabled, modifier = Modifier.testTag("birth-date-open")) {
            Text(value?.format(displayDateFormat) ?: stringResource(R.string.field_birth_date_choose))
        }
        error?.let { Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (!draft.isOpen) return
    DatePickerDialog(
        onDismissRequest = { onDraftChange(PrivateDatePickerDraft()) },
        confirmButton = {
            TextButton(enabled = enabled && draft.canAccept, modifier = Modifier.testTag("birth-date-accept"), onClick = {
                draft.date?.let { date ->
                    onChange(date)
                    onDraftChange(PrivateDatePickerDraft())
                }
            }) { Text(stringResource(R.string.date_picker_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onDraftChange(PrivateDatePickerDraft()) }, modifier = Modifier.testTag("birth-date-cancel")) { Text(stringResource(R.string.logout_cancel)) }
        },
    ) {
        Column {
            TextButton(onClick = { onDraftChange(draft.copy(inputMode = !draft.inputMode)) }, modifier = Modifier.testTag("birth-date-toggle")) {
                Text(stringResource(if (draft.inputMode) R.string.date_picker_calendar_mode else R.string.date_picker_input_mode))
            }
            if (draft.inputMode) PrivateDatePicker(inputContent = {
                Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val invalid = draft.inputText.isNotEmpty() && draft.date == null
                    AsclepioTextField(
                        value = draft.inputText,
                        onValueChange = { onDraftChange(draft.withInput(it)) },
                        label = { Text(stringResource(R.string.field_birth_date)) },
                        supportingText = {
                            Text(stringResource(if (invalid) R.string.date_picker_invalid else R.string.date_picker_format))
                        },
                        isError = invalid,
                        singleLine = true,
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("birth-date-input"),
                    )
                }
            }) else {
                val picker = rememberPrivateDatePickerState(draft.selectedDateMillis, draft.displayedMonthMillis) { date, month ->
                    onDraftChange(draft.withCalendarSelection(date, month))
                }
                PrivateDatePicker(picker)
            }
        }
    }
}
