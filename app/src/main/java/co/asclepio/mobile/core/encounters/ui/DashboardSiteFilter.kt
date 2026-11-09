package co.asclepio.mobile.core.encounters.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.*

@Composable
fun DashboardSiteFilter(state: StaffSitesUiState, selected: String?, onSelect: (String?) -> Unit, onRetry: () -> Unit) {
    StaffChoiceField(stringResource(R.string.tenant_picker_sites), selected,
        listOf(null to stringResource(R.string.staff_sites_all)) + state.sites.map { it.id to it.name }, onSelect, enabled = state.available)
    state.message?.let {
        Text(it.text(), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry, enabled = state.retryEnabled && !state.loading) { Text(stringResource(R.string.retry)) }
    }
}
