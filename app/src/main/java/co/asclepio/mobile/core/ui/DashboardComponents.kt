package co.asclepio.mobile.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.encounters.EncounterCount

/** Header/summary/work panel/pending strip retain the single-column order of UI-02-web. */
@Composable
fun DashboardHeader(title: String, description: String, onRefresh: () -> Unit, busy: Boolean, primary: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(title, description)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            primary?.invoke()
            OutlinedButton(onClick = onRefresh, enabled = !busy) { Text(stringResource(R.string.home_refresh)) }
        }
    }
}

data class DashboardSummaryCell(
    val label: String,
    @param:DrawableRes val icon: Int,
    val state: DashboardRead<EncounterCount>,
    val tone: NoticeTone = NoticeTone.Info,
    val onRetry: () -> Unit,
)

@Composable
fun DashboardSummaryBar(cells: List<DashboardSummaryCell>, modifier: Modifier = Modifier, filter: (@Composable () -> Unit)? = null) {
    val summaryLabel = stringResource(R.string.dash_indicators)
    Panel(modifier.fillMaxWidth().semantics { contentDescription = summaryLabel; isTraversalGroup = true }) {
        BoxWithConstraints {
            val wide = maxWidth >= 680.dp && LocalDensity.current.fontScale * LocalUiPreferences.current.textScale <= 1.4f
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                cells.forEach { cell -> SummaryCell(cell, Modifier.weight(1f)) }
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                cells.forEachIndexed { index, cell ->
                    if (index > 0) HorizontalDivider()
                    SummaryCell(cell, Modifier.fillMaxWidth())
                }
            }
        }
        filter?.let { HorizontalDivider(); it() }
    }
}

@Composable
private fun SummaryCell(cell: DashboardSummaryCell, modifier: Modifier) {
    val palette = LocalUiPalette.current
    val state = cell.state
    val failed = state is DashboardRead.Error || state == DashboardRead.Forbidden
    val icon = if (state == DashboardRead.Forbidden) R.drawable.ic_dashboard_locked else if (failed) R.drawable.ic_dashboard_error else cell.icon
    val (background, foreground) = when {
        failed -> palette.errorBackground to palette.error
        cell.tone == NoticeTone.Warning -> palette.warningBackground to palette.warning
        cell.tone == NoticeTone.Success -> palette.successBackground to palette.success
        else -> palette.accentSoft to palette.foreground
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = Color(background), contentColor = Color(foreground), shape = RoundedCornerShape(12.dp)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.padding(10.dp).size(24.dp))
        }
        Text(cell.label, style = MaterialTheme.typography.labelLarge)
        when (state) {
            DashboardRead.Loading -> Text(stringResource(R.string.dash_stat_loading), style = MaterialTheme.typography.bodyMedium)
            DashboardRead.Forbidden -> Text(stringResource(R.string.dash_stat_forbidden), style = MaterialTheme.typography.bodyMedium)
            is DashboardRead.Error -> {
                Text(stringResource(R.string.dash_stat_error), style = MaterialTheme.typography.bodyMedium)
                val retryName = stringResource(R.string.dash_retry_named, cell.label)
                TextButton(onClick = cell.onRetry, enabled = state.retryEnabled, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = retryName }) { Text(stringResource(R.string.retry)) }
            }
            is DashboardRead.Ready -> DashboardCountText(state.data)
        }
    }
}

@Composable
fun DashboardCountText(count: EncounterCount, modifier: Modifier = Modifier) {
    val accessible = if (count.limited) stringResource(R.string.dash_at_least, count.quantity) else count.quantity.toString()
    Text(count.quantity.toString() + if (count.limited) "+" else "", style = MaterialTheme.typography.headlineMedium, modifier = modifier.clearAndSetSemantics { contentDescription = accessible })
}

@Composable
fun DashboardWorkHeader(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.dash_live, 30), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DashboardPendingStrip(title: String, body: String) {
    Panel(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dash_pending_label), style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
