package co.asclepio.mobile.feature.staffaccount.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.*
import co.asclepio.mobile.core.ui.AsclepioButton
import co.asclepio.mobile.core.auth.*

private fun StaffDestination.label(): Int = when (this) {
    StaffDestination.RECEPTION -> R.string.staff_destination_reception
    StaffDestination.TRIAGE -> R.string.staff_destination_triage
    StaffDestination.MEDICINE -> R.string.staff_destination_medicine
    StaffDestination.ADMINISTRATION -> R.string.staff_destination_administration
    StaffDestination.QUEUE -> R.string.staff_destination_queue
    StaffDestination.ENCOUNTERS -> R.string.staff_destination_encounters
    StaffDestination.REPORTS -> R.string.staff_destination_reports
}
private fun StaffRole.label(): Int = when (this) {
    StaffRole.RECEPTION -> R.string.staff_role_reception
    StaffRole.NURSE -> R.string.staff_role_nurse
    StaffRole.PHYSICIAN -> R.string.staff_role_physician
    StaffRole.ADMIN -> R.string.staff_role_admin
}

@Composable
fun StaffWorkspaceScreen(
    principal: VerifiedPrincipal.Staff,
    destination: StaffDestination,
    widthSizeClass: WindowWidthSizeClass,
    confirmation: SessionConfirmation?,
    onSelect: (StaffDestination) -> Unit,
    onLogout: () -> Unit,
    onChangeAccess: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    ScreenColumn(maxWidth = 1080.dp) {
        Text(principal.fullName, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(principal.role.label()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (adaptivePaneMode(widthSizeClass) == PaneMode.Single) {
            StaffNavigation(principal.role, destination, onSelect)
            PendingDestination(destination)
        } else Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.widthIn(max = 280.dp).weight(1f)) { StaffNavigation(principal.role, destination, onSelect) }
            Column(Modifier.weight(2f)) { PendingDestination(destination) }
        }
        HorizontalDivider()
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.logout_confirm)) }
        TextButton(onClick = onChangeAccess) { Text(stringResource(R.string.change_access)) }
    }
    if (confirmation != null) AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(if (confirmation == SessionConfirmation.LOGOUT) R.string.logout_dialog_title else R.string.change_access_dialog_title)) },
        text = if (confirmation == SessionConfirmation.CHANGE_ACCESS) ({ Text(stringResource(R.string.change_access_dialog_body)) }) else null,
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(if (confirmation == SessionConfirmation.LOGOUT) R.string.logout_confirm else R.string.change_access)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.logout_cancel)) } },
    )
}

@Composable
private fun StaffNavigation(role: StaffRole, selected: StaffDestination, onSelect: (StaffDestination) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        role.destinations.forEach { destination ->
            OutlinedButton(onClick = { onSelect(destination) }, enabled = selected != destination, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(destination.label()))
            }
        }
    }
}

@Composable
private fun PendingDestination(destination: StaffDestination) {
    Panel(Modifier.fillMaxWidth()) {
        PageHeader(stringResource(destination.label()))
        UiNotice(stringResource(R.string.staff_destination_pending))
    }
}
