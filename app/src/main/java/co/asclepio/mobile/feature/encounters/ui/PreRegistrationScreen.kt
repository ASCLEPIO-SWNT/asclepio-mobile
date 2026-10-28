package co.asclepio.mobile.feature.encounters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.PaneMode
import co.asclepio.mobile.core.ui.paneMode
import co.asclepio.mobile.core.ui.text

/** R29: dos paneles (sedes y motivo) en `Medium`/`Expanded`; una columna en `Compact` (D14). */
fun preRegistrationPaneMode(widthSizeClass: WindowWidthSizeClass): PaneMode = paneMode(widthSizeClass)

/** Nuevo pre-registro: sede de la lista y motivo escrito (R8–R15, R25, R29). */
@Composable
fun PreRegistrationScreen(
    state: PreRegistrationUiState,
    widthSizeClass: WindowWidthSizeClass,
    onSelectSite: (String) -> Unit,
    onComplaintChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetrySites: () -> Unit,
    onViewStatus: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.created) LaunchedEffect(Unit) { onCreated() }
    val sites: @Composable (Modifier) -> Unit = { m -> SitesPane(state, onSelectSite, onRetrySites, m) }
    val complaint: @Composable (Modifier) -> Unit = { m -> ComplaintPane(state, onComplaintChange, onSubmit, onViewStatus, m) }
    if (preRegistrationPaneMode(widthSizeClass) == PaneMode.Dual) {
        Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.preregistration_title), style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                sites(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                complaint(Modifier.weight(1f).verticalScroll(rememberScrollState()))
            }
        }
    } else {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.preregistration_title), style = MaterialTheme.typography.headlineSmall)
            sites(Modifier)
            complaint(Modifier)
        }
    }
}

@Composable
private fun SitesPane(state: PreRegistrationUiState, onSelect: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.preregistration_site), style = MaterialTheme.typography.titleMedium)
        when (val sites = state.sites) {
            SitesUiState.Loading -> CircularProgressIndicator()
            is SitesUiState.Error -> {
                Text(sites.message.text(), color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry, enabled = sites.retryEnabled) { Text(stringResource(R.string.retry)) }
            }
            is SitesUiState.Loaded -> sites.sites.forEach { site ->
                val selected = site.id == state.form.siteId
                ListItem(
                    headlineContent = { Text(site.name) },
                    leadingContent = { RadioButton(selected = selected, onClick = null, enabled = !state.submitting) },
                    modifier = Modifier.selectable(
                        selected = selected,
                        enabled = !state.submitting,
                        role = Role.RadioButton,
                        onClick = { onSelect(site.id) },
                    ),
                )
            }
        }
        state.fieldErrors[PreRegistrationField.Site]?.let {
            Text(it.text(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ComplaintPane(
    state: PreRegistrationUiState,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onViewStatus: () -> Unit,
    modifier: Modifier,
) {
    val error = state.fieldErrors[PreRegistrationField.ChiefComplaint]
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.form.chiefComplaintText,
            onValueChange = onChange,
            label = { Text(stringResource(R.string.preregistration_complaint)) },
            isError = error != null,
            supportingText = {
                Column {
                    error?.let { Text(it.text()) }
                    Text(stringResource(R.string.preregistration_counter, state.complaintLength, CHIEF_COMPLAINT_MAX))
                }
            },
            enabled = !state.submitting,
            minLines = 4,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )
        state.message?.let { Text(it.text(), color = MaterialTheme.colorScheme.error) }
        if (state.showViewStatus) {
            OutlinedButton(onClick = onViewStatus, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.preregistration_view_status))
            }
        }
        Button(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
            if (state.submitting) CircularProgressIndicator(Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.preregistration_submit))
        }
    }
}
