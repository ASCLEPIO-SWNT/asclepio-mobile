package co.asclepio.mobile.feature.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.AsclepioButton
import co.asclepio.mobile.core.tenants.PublicSiteDto
import co.asclepio.mobile.core.tenants.PublicTenantDto
import co.asclepio.mobile.core.ui.PaneMode
import co.asclepio.mobile.core.ui.paneMode
import co.asclepio.mobile.core.ui.adaptivePaneMode
import co.asclepio.mobile.core.ui.ScreenColumn
import co.asclepio.mobile.core.ui.Panel
import co.asclepio.mobile.core.ui.PageHeader
import co.asclepio.mobile.core.ui.UiNotice
import co.asclepio.mobile.core.ui.NoticeTone
import co.asclepio.mobile.core.ui.text

/** Elegir IPS y sede (R1–R5). Tablet: IPS y sedes lado a lado; teléfono: una columna (R35). */
@Composable
fun TenantPickerScreen(
    state: TenantPickerUiState,
    widthSizeClass: WindowWidthSizeClass,
    onSelectTenant: (String) -> Unit,
    onSelectSite: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is TenantPickerUiState.Success && state.confirmed) LaunchedEffect(Unit) { onConfirmed() }
    ScreenColumn(modifier, maxWidth = 1080.dp) {
        PageHeader(stringResource(R.string.tenant_picker_title))
        when (state) {
            TenantPickerUiState.Loading -> CircularProgressIndicator()
            is TenantPickerUiState.Error -> {
                UiNotice(state.message.text(), NoticeTone.Error)
                AsclepioButton(onClick = onRetry, enabled = state.retryEnabled) { Text(stringResource(R.string.retry)) }
            }
            is TenantPickerUiState.Success -> {
                if (adaptivePaneMode(widthSizeClass) == PaneMode.Dual) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Panel(Modifier.weight(1f)) { TenantList(state, onSelectTenant, Modifier) }
                        Panel(Modifier.weight(1f)) { SiteList(state, onSelectSite, Modifier) }
                    }
                } else {
                    TenantList(state, onSelectTenant, Modifier)
                    SiteList(state, onSelectSite, Modifier)
                }
                AsclepioButton(onClick = onConfirm, enabled = state.canContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tenant_picker_continue))
                }
            }
        }
    }
}

@Composable
private fun TenantList(state: TenantPickerUiState.Success, onSelect: (String) -> Unit, modifier: Modifier) {
    Column(modifier) {
        state.tenants.forEach { tenant ->
            val enabled = state.isEnabled(tenant)
            SelectableRow(
                title = tenant.name,
                subtitle = if (enabled) null else stringResource(R.string.tenant_no_sites),
                selected = tenant.id == state.selectedTenantId,
                enabled = enabled,
                onClick = { onSelect(tenant.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SiteList(state: TenantPickerUiState.Success, onSelect: (String) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.tenant_picker_sites), style = MaterialTheme.typography.titleMedium)
        val tenant = state.selectedTenant
        if (tenant == null) {
            Text(stringResource(R.string.tenant_picker_choose_tenant), style = MaterialTheme.typography.bodyMedium)
        } else {
            Column {
                tenant.sites.forEach { site ->
                    SelectableRow(
                        title = site.name,
                        subtitle = null,
                        selected = site.id == state.selectedSiteId,
                        enabled = !state.saving,
                        onClick = { onSelect(site.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableRow(title: String, subtitle: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        modifier = Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    )
}
