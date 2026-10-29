package co.asclepio.mobile.feature.encounters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.NoticeTone
import co.asclepio.mobile.core.ui.PageHeader
import co.asclepio.mobile.core.ui.Panel
import co.asclepio.mobile.core.ui.UiNotice
import co.asclepio.mobile.core.ui.text

/** Solo estados actuales propios; la raíz conserva navegación/logout y refresco por ciclo de vida. */
@Composable
fun MyRequestsScreen(
    state: MyRequestsUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshEnabled = when (state) {
        MyRequestsUiState.Loading -> false
        is MyRequestsUiState.Error -> state.refreshEnabled
        else -> true
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 840.dp).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PageHeader(stringResource(R.string.my_requests_title), stringResource(R.string.my_requests_intro))
            }
            item {
                OutlinedButton(onClick = onRefresh, enabled = refreshEnabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_refresh))
                }
            }
            when (state) {
                MyRequestsUiState.Loading -> item {
                    Row(
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.my_requests_loading))
                    }
                }
                MyRequestsUiState.Empty -> item { UiNotice(stringResource(R.string.my_requests_empty)) }
                is MyRequestsUiState.Error -> item { UiNotice(state.message.text(), NoticeTone.Error) }
                is MyRequestsUiState.Success -> items(state.items) { request ->
                    Panel(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                        Text(stringResource(R.string.home_status_site, request.siteName), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(request.statusRes), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.home_status_sent, request.sentAt), style = MaterialTheme.typography.bodyMedium)
                        request.expiresAt?.let {
                            Text(stringResource(R.string.home_status_expires, it), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
