package co.asclepio.mobile.feature.triage.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.encounters.ui.StaffChoiceField
import co.asclepio.mobile.core.ui.*

@Composable
fun ClinicalVitalSignsForm(
    state: VitalSignsFormUiState, canWrite: Boolean, onChange: (RawVitalSigns) -> Unit,
    onSubmit: () -> Unit, onFocusHandled: (Long) -> Unit, editable: Boolean = true, modifier: Modifier = Modifier,
) {
    val focus = remember { VitalField.entries.associateWith { FocusRequester() } }
    val bring = remember { VitalField.entries.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(state.focusVersion, state.focusField, editable, state.saving) {
        state.focusField?.takeIf { editable && !state.saving }?.let { field ->
            bring.getValue(field).bringIntoView()
            focus.getValue(field).requestFocus()
            onFocusHandled(state.focusVersion)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.clinical_vitals_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        UiNotice(stringResource(R.string.clinical_provisional), NoticeTone.Warning)
        VitalField.entries.forEach { field ->
            val fieldModifier = Modifier.fillMaxWidth().bringIntoViewRequester(bring.getValue(field)).focusRequester(focus.getValue(field))
            when (field) {
                VitalField.SupplementalOxygen -> StaffChoiceField(
                    label = stringResource(vitalLabel(field)), selected = state.raw.supplementalOxygen,
                    options = listOf(null to stringResource(R.string.clinical_choose), true to stringResource(R.string.clinical_yes), false to stringResource(R.string.clinical_no)),
                    onSelect = { onChange(state.raw.copy(supplementalOxygen = it)) }, enabled = editable && !state.saving,
                    error = state.errors[field]?.text(), modifier = fieldModifier,
                )
                VitalField.Consciousness -> StaffChoiceField(
                    label = stringResource(vitalLabel(field)), selected = state.raw.consciousness,
                    options = listOf(null to stringResource(R.string.clinical_choose)) + ConsciousnessLevel.entries.map { it to it.name },
                    onSelect = { onChange(state.raw.copy(consciousness = it)) }, enabled = editable && !state.saving,
                    error = state.errors[field]?.text(), modifier = fieldModifier,
                )
                else -> AsclepioTextField(
                    value = state.raw.numberText(field), onValueChange = { onChange(state.raw.withNumber(field, it)) }, modifier = fieldModifier,
                    enabled = editable && !state.saving, singleLine = true, label = { Text(stringResource(vitalLabel(field)) + " (" + vitalUnit(field) + ")") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (field == VitalField.TemperatureC) KeyboardType.Decimal else KeyboardType.Number),
                    isError = state.errors[field] != null,
                    supportingText = if (state.errors[field] != null) ({ Text(state.errors.getValue(field).text()) }) else if (field in setOf(VitalField.DiastolicBp, VitalField.GlucoseMgDl)) ({ Text(stringResource(R.string.clinical_optional)) }) else null,
                )
            }
        }
        if (state.success) UiNotice(stringResource(R.string.clinical_vitals_saved), NoticeTone.Success)
        state.message?.let { UiNotice(it.text(), if (state.success) NoticeTone.Success else NoticeTone.Error) }
        AsclepioButton(onClick = onSubmit, enabled = canWrite && !state.saving && !state.blocked, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.saving) R.string.clinical_saving else R.string.clinical_save_vitals))
        }
    }
}

@StringRes
internal fun vitalLabel(field: VitalField): Int = when (field) {
    VitalField.RespiratoryRate -> R.string.clinical_field_respiratory_rate
    VitalField.OxygenSaturation -> R.string.clinical_field_oxygen_saturation
    VitalField.SupplementalOxygen -> R.string.clinical_field_supplemental_oxygen
    VitalField.SystolicBp -> R.string.clinical_field_systolic_bp
    VitalField.HeartRate -> R.string.clinical_field_heart_rate
    VitalField.Consciousness -> R.string.clinical_field_consciousness
    VitalField.TemperatureC -> R.string.clinical_field_temperature_c
    VitalField.DiastolicBp -> R.string.clinical_field_diastolic_bp
    VitalField.GlucoseMgDl -> R.string.clinical_field_glucose_mg_dl
}
internal fun vitalUnit(field: VitalField): String = when (field) {
    VitalField.RespiratoryRate -> "rpm"
    VitalField.OxygenSaturation -> "%"
    VitalField.SystolicBp, VitalField.DiastolicBp -> "mmHg"
    VitalField.HeartRate -> "lpm"
    VitalField.TemperatureC -> "°C"
    VitalField.GlucoseMgDl -> "mg/dL"
    else -> ""
}
private fun RawVitalSigns.numberText(field: VitalField): String = when (field) {
    VitalField.RespiratoryRate -> respiratoryRate
    VitalField.OxygenSaturation -> oxygenSaturation
    VitalField.SystolicBp -> systolicBp
    VitalField.HeartRate -> heartRate
    VitalField.TemperatureC -> temperatureC
    VitalField.DiastolicBp -> diastolicBp
    VitalField.GlucoseMgDl -> glucoseMgDl
    else -> ""
}
private fun RawVitalSigns.withNumber(field: VitalField, text: String): RawVitalSigns = when (field) {
    VitalField.RespiratoryRate -> copy(respiratoryRate = text)
    VitalField.OxygenSaturation -> copy(oxygenSaturation = text)
    VitalField.SystolicBp -> copy(systolicBp = text)
    VitalField.HeartRate -> copy(heartRate = text)
    VitalField.TemperatureC -> copy(temperatureC = text)
    VitalField.DiastolicBp -> copy(diastolicBp = text)
    VitalField.GlucoseMgDl -> copy(glucoseMgDl = text)
    else -> this
}
