package co.asclepio.mobile.feature.encounters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.AsclepioButton
import co.asclepio.mobile.core.ui.text
import co.asclepio.mobile.core.ui.Panel
import co.asclepio.mobile.core.ui.UiNotice
import co.asclepio.mobile.core.ui.NoticeTone

/**
 * Zona de estado del inicio (R17–R22): sede, texto del estado, "Enviado" y, en `PENDING`, el vencimiento; "Actualizar"
 * y, si el último no está en curso, "Nuevo pre-registro". Nunca muestra nivel ni hora de llegada.
 */
@Composable
fun PreRegistrationStatusPane(
    state: PreRegistrationStatusUiState,
    onRefresh: () -> Unit,
    onNewPreRegistration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier.fillMaxWidth()) {
            when (state) {
                PreRegistrationStatusUiState.Loading -> CircularProgressIndicator()
                PreRegistrationStatusUiState.Empty ->
                    Text(stringResource(R.string.home_no_preregistrations), style = MaterialTheme.typography.bodyLarge)
                is PreRegistrationStatusUiState.Success -> {
                    Text(stringResource(R.string.home_status_site, state.siteName), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(state.statusRes), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.home_status_sent, state.sentAt), style = MaterialTheme.typography.bodyMedium)
                    state.expiresAt?.let {
                        Text(stringResource(R.string.home_status_expires, it), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is PreRegistrationStatusUiState.Error -> UiNotice(state.message.text(), NoticeTone.Error)
            }
            val refreshEnabled = when (state) {
                PreRegistrationStatusUiState.Loading -> false
                is PreRegistrationStatusUiState.Error -> state.refreshEnabled
                else -> true
            }
            OutlinedButton(onClick = onRefresh, enabled = refreshEnabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_refresh))
            }
            if (state.canCreate) {
                AsclepioButton(onClick = onNewPreRegistration, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_action_new_preregistration))
                }
            }
    }
}
