package co.asclepio.mobile.feature.triage.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.encounters.ui.StaffChoiceField
import co.asclepio.mobile.core.ui.*

@Composable
fun ClinicalFindingsForm(
    state: FindingsFormUiState, canWrite: Boolean, onRowChange: (Long, RawSymptomFinding) -> Unit,
    onAdd: () -> Unit, onRemove: (Long) -> Unit, onSubmit: () -> Unit, onFocusHandled: (Long) -> Unit,
    editable: Boolean = true, modifier: Modifier = Modifier,
) {
    val focus = remember { mutableMapOf<Pair<Long, FindingField>, FocusRequester>() }
    val bring = remember { mutableMapOf<Pair<Long, FindingField>, BringIntoViewRequester>() }
    val addFocus = remember { FocusRequester() }
    val addBring = remember { BringIntoViewRequester() }
    val keys = state.rows.flatMap { row -> FindingField.entries.map { row.key to it } }.toSet()
    focus.keys.retainAll(keys)
    bring.keys.retainAll(keys)
    keys.forEach { target -> key(target) {
        focus[target] = remember { FocusRequester() }
        bring[target] = remember { BringIntoViewRequester() }
    } }
    LaunchedEffect(state.focusVersion, state.focusPending, editable, state.saving) {
        if (state.focusPending && editable && !state.saving) {
            val rowKey = state.focusRowKey ?: state.rows.firstOrNull()?.key
            val target = rowKey?.let { it to (state.focusField ?: FindingField.Description) }
            if (target != null && target in focus) {
                bring.getValue(target).bringIntoView()
                focus.getValue(target).requestFocus()
            } else {
                addBring.bringIntoView()
                addFocus.requestFocus()
            }
            onFocusHandled(state.focusVersion)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.clinical_findings_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.clinical_original_readonly))
        state.batchError?.let { UiNotice(it.text(), NoticeTone.Error) }
        state.rows.forEachIndexed { index, row -> key(row.key) {
            Panel(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.clinical_finding_label, index + 1), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                StaffChoiceField(
                    label = stringResource(R.string.clinical_kind), selected = row.raw.kind,
                    options = listOf(null to stringResource(R.string.clinical_choose)) + SymptomFindingKind.entries.map { it to stringResource(findingKindLabel(it.name)) },
                    onSelect = { onRowChange(row.key, row.raw.copy(kind = it)) }, enabled = editable && !state.saving,
                    error = row.errors[FindingField.Kind]?.text(),
                    modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bring.getValue(row.key to FindingField.Kind)).focusRequester(focus.getValue(row.key to FindingField.Kind)),
                )
                AsclepioTextField(
                    value = row.raw.description, onValueChange = { onRowChange(row.key, row.raw.copy(description = it)) }, enabled = editable && !state.saving,
                    label = { Text(stringResource(R.string.clinical_description)) }, minLines = 3,
                    isError = row.errors[FindingField.Description] != null,
                    supportingText = row.errors[FindingField.Description]?.let { { Text(it.text()) } },
                    modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bring.getValue(row.key to FindingField.Description)).focusRequester(focus.getValue(row.key to FindingField.Description)),
                )
                row.rowError?.let { UiNotice(it.text(), NoticeTone.Error) }
                TextButton(onClick = { onRemove(row.key) }, enabled = editable && !state.saving) { Text(stringResource(R.string.clinical_remove_finding, index + 1)) }
            }
        } }
        OutlinedButton(onClick = onAdd, enabled = editable && !state.saving && state.rows.size < ClinicalRules.FINDINGS_MAX, modifier = Modifier.fillMaxWidth().bringIntoViewRequester(addBring).focusRequester(addFocus)) {
            Text(stringResource(R.string.clinical_add_finding))
        }
        if (state.success) UiNotice(stringResource(R.string.clinical_findings_saved), NoticeTone.Success)
        state.message?.let { UiNotice(it.text(), if (state.success) NoticeTone.Success else NoticeTone.Error) }
        AsclepioButton(onClick = onSubmit, enabled = canWrite && !state.saving && !state.blocked, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.saving) R.string.clinical_saving else R.string.clinical_save_findings))
        }
    }
}

@StringRes
internal fun findingKindLabel(kind: String): Int = when (kind) {
    "SYMPTOM" -> R.string.clinical_kind_symptom
    "FINDING" -> R.string.clinical_kind_finding
    "COMPLAINT_NOTE" -> R.string.clinical_kind_complaint_note
    else -> R.string.staff_value_unavailable
}
