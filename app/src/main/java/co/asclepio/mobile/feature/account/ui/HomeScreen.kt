package co.asclepio.mobile.feature.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.ScreenColumn
import co.asclepio.mobile.core.ui.PageHeader
import co.asclepio.mobile.core.ui.UiNotice
import co.asclepio.mobile.core.ui.NoticeTone
import co.asclepio.mobile.core.ui.AsclepioButton

/**
 * Inicio (R30, R31 de F-01; R16 de F-02): IPS y sede, aviso de identidad, "Mis datos", el estado del último
 * pre-registro (slot que compone AppNavigation, D3 de F-02) y "Cerrar sesión" con confirmación.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenProfile: () -> Unit,
    onOpenMyRequests: () -> Unit,
    status: @Composable () -> Unit,
    onRequestLogout: () -> Unit,
    onCancelLogout: () -> Unit,
    onConfirmLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        PageHeader(stringResource(R.string.home_title))
        state.tenant?.let {
            Text(stringResource(R.string.tenant_and_site, it.tenantName, it.siteName), style = MaterialTheme.typography.titleMedium)
        }
        if (state.showIdentityNotice) {
            Card(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_identity_unverified), Modifier.padding(16.dp))
            }
        }
        status()
        OutlinedButton(onClick = onOpenMyRequests, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.my_requests_title))
        }
        OutlinedButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_action_profile))
        }
        OutlinedButton(onClick = onRequestLogout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.logout_confirm))
        }
        }
    if (state.showLogoutDialog) {
        AlertDialog(
        onDismissRequest = onCancelLogout,
        title = { Text(stringResource(R.string.logout_dialog_title)) },
        confirmButton = { TextButton(onClick = onConfirmLogout) { Text(stringResource(R.string.logout_confirm)) } },
        dismissButton = { TextButton(onClick = onCancelLogout) { Text(stringResource(R.string.logout_cancel)) } },
        )
    }
}
