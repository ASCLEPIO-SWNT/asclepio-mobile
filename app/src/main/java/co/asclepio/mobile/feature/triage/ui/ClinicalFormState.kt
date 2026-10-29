package co.asclepio.mobile.feature.triage.ui

import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.ui.UiMessage

data class VitalSignsFormUiState(
    val raw: RawVitalSigns = RawVitalSigns(),
    val errors: Map<VitalField, UiMessage> = emptyMap(),
    val message: UiMessage? = null,
    val success: Boolean = false,
    val saving: Boolean = false,
    val blocked: Boolean = false,
    val focusVersion: Long = 0,
    val focusField: VitalField? = null,
) { override fun toString() = "VitalSignsFormUiState(private=***, saving=$saving, blocked=$blocked)" }

data class FindingRowUiState(
    val key: Long,
    val raw: RawSymptomFinding = RawSymptomFinding(),
    val errors: Map<FindingField, UiMessage> = emptyMap(),
    val rowError: UiMessage? = null,
) { override fun toString() = "FindingRowUiState(key=$key, private=***)" }

data class FindingsFormUiState(
    val rows: List<FindingRowUiState> = listOf(FindingRowUiState(0)),
    val batchError: UiMessage? = null,
    val message: UiMessage? = null,
    val success: Boolean = false,
    val saving: Boolean = false,
    val blocked: Boolean = false,
    val focusVersion: Long = 0,
    val focusPending: Boolean = false,
    val focusRowKey: Long? = null,
    val focusField: FindingField? = null,
) { override fun toString() = "FindingsFormUiState(private=***, saving=$saving, blocked=$blocked)" }
