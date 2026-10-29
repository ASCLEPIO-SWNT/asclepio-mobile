package co.asclepio.mobile.feature.status.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.AsclepioButton
import co.asclepio.mobile.core.ui.ScreenColumn
import co.asclepio.mobile.core.ui.PageHeader
import co.asclepio.mobile.core.ui.adaptivePaneMode
import co.asclepio.mobile.core.ui.PaneMode
import co.asclepio.mobile.core.ui.text
import co.asclepio.mobile.core.ui.UiNotice
import co.asclepio.mobile.core.ui.NoticeTone

/** Teléfono (Compact): una columna. Tablet (Medium/Expanded): estado e información lado a lado. */
@Composable
fun StatusScreen(
    state: StatusUiState,
    widthSizeClass: WindowWidthSizeClass,
    apiBaseUrl: String,
    contract: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = if (widthSizeClass == WindowWidthSizeClass.Compact) 16.dp else 32.dp
    val content: @Composable (Modifier) -> Unit = { m ->
        StatusCard(state, onRetry, m)
        InfoCard(apiBaseUrl, contract, m)
    }
    ScreenColumn(modifier, maxWidth = 1080.dp) {
        PageHeader(stringResource(R.string.status_title))
        if (adaptivePaneMode(widthSizeClass) == PaneMode.Single) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) { content(Modifier) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) { content(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StatusCard(state: StatusUiState, onRetry: () -> Unit, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.status_title), style = MaterialTheme.typography.titleMedium)
            when (state) {
                StatusUiState.Loading -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.status_loading))
                }
                is StatusUiState.Success -> {
                    Text(stringResource(R.string.status_ok), color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.status_server_time, state.serverTimeBogota))
                }
                is StatusUiState.Error -> {
                    UiNotice(stringResource(R.string.status_error, state.message.text()), NoticeTone.Error)
                    AsclepioButton(onClick = onRetry) { Text(stringResource(R.string.status_retry)) }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(apiBaseUrl: String, contract: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.status_api_url, apiBaseUrl))
            Text(stringResource(R.string.status_contract, contract))
        }
    }
}
