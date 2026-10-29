package co.asclepio.mobile.core.encounters.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.StaffPatientDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StaffFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun StaffPatientFacts(patient: StaffPatientDto, title: Boolean = true) {
    if (title) Text(patient.fullName?.takeIf(String::isNotBlank) ?: stringResource(R.string.staff_patient_nn), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    if (patient.documentType != null && patient.documentNumber != null) StaffFact(stringResource(R.string.staff_document), "${patient.documentType} ${patient.documentNumber}")
    patient.birthDate?.let { date ->
        val display = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")) }.getOrNull()
        StaffFact(stringResource(R.string.field_birth_date), display ?: stringResource(R.string.staff_value_unavailable))
    }
    patient.estimatedAgeYears?.let { StaffFact(stringResource(R.string.staff_estimated_age), it.toString()) }
    StaffFact(stringResource(R.string.field_sex), stringResource(when (patient.sex) {
        "M" -> R.string.sex_m
        "F" -> R.string.sex_f
        "X" -> R.string.sex_x
        else -> R.string.staff_value_unavailable
    }))
}

/** A persistent label and a native menu, usable with large text and keyboard. */
@Composable
fun <T> StaffChoiceField(
    label: String, selected: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit,
    enabled: Boolean = true, error: String? = null, modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(options.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.staff_choose))
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) })
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
