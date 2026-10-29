package co.asclepio.mobile.feature.triage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.ClinicalCaptureDto
import co.asclepio.mobile.core.encounters.VitalField
import co.asclepio.mobile.core.encounters.ui.StaffFact
import co.asclepio.mobile.core.encounters.ui.staffDateTime
import co.asclepio.mobile.core.ui.Panel
import co.asclepio.mobile.core.ui.UiNotice

/** Read-only server history; each list retains its received recordedAt/id order. */
@Composable
fun ClinicalHistorySection(capture: ClinicalCaptureDto, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (capture.vitalSigns.isEmpty() && capture.symptomFindings.isEmpty()) {
            UiNotice(stringResource(R.string.clinical_history_empty))
        }
        if (capture.vitalSigns.isNotEmpty()) {
            Text(
                stringResource(R.string.clinical_history_vitals),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            capture.vitalSigns.forEachIndexed { index, take ->
                Panel(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.clinical_measurement, index + 1),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    StaffFact(stringResource(R.string.clinical_recorded_at), staffDateTime(take.recordedAt))
                    StaffFact(
                        stringResource(R.string.clinical_recorded_by),
                        take.recordedBy.fullName ?: stringResource(R.string.staff_value_unavailable),
                    )
                    VitalValue(VitalField.RespiratoryRate, take.respiratoryRate.toString())
                    VitalValue(VitalField.OxygenSaturation, take.oxygenSaturation.toString())
                    StaffFact(
                        stringResource(vitalLabel(VitalField.SupplementalOxygen)),
                        stringResource(if (take.supplementalOxygen) R.string.clinical_yes else R.string.clinical_no),
                    )
                    VitalValue(VitalField.SystolicBp, take.systolicBp.toString())
                    VitalValue(VitalField.HeartRate, take.heartRate.toString())
                    StaffFact(stringResource(vitalLabel(VitalField.Consciousness)), take.consciousness)
                    VitalValue(VitalField.TemperatureC, take.temperatureC.toString())
                    VitalValue(VitalField.DiastolicBp, take.diastolicBp?.toString())
                    VitalValue(VitalField.GlucoseMgDl, take.glucoseMgDl?.toString())
                }
            }
        }
        if (capture.symptomFindings.isNotEmpty()) {
            Text(
                stringResource(R.string.clinical_history_findings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            capture.symptomFindings.forEach { finding ->
                Panel(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(findingKindLabel(finding.kind)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(finding.description, style = MaterialTheme.typography.bodyLarge)
                    StaffFact(stringResource(R.string.clinical_recorded_at), staffDateTime(finding.recordedAt))
                    StaffFact(
                        stringResource(R.string.clinical_recorded_by),
                        finding.recordedBy.fullName ?: stringResource(R.string.staff_value_unavailable),
                    )
                }
            }
        }
    }
}

@Composable
private fun VitalValue(field: VitalField, value: String?) {
    StaffFact(
        stringResource(vitalLabel(field)),
        value?.let { "$it ${vitalUnit(field)}" } ?: stringResource(R.string.value_missing),
    )
}
