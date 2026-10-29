package co.asclepio.mobile.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.AccessMode

@Composable
fun WelcomeScreen(widthSizeClass: WindowWidthSizeClass, onSelect: (AccessMode) -> Unit) {
    ScreenColumn(maxWidth = 1000.dp) {
        PageHeader(stringResource(R.string.access_choose), stringResource(R.string.tagline))
        if (adaptivePaneMode(widthSizeClass) == PaneMode.Single) {
            AccessCard(AccessMode.PATIENT, Modifier.fillMaxWidth(), onSelect)
            AccessCard(AccessMode.STAFF, Modifier.fillMaxWidth(), onSelect)
        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AccessCard(AccessMode.PATIENT, Modifier.weight(1f), onSelect)
            AccessCard(AccessMode.STAFF, Modifier.weight(1f), onSelect)
        }
    }
}

@Composable
private fun AccessCard(mode: AccessMode, modifier: Modifier, onSelect: (AccessMode) -> Unit) {
    Panel(modifier) {
        Text(stringResource(if (mode == AccessMode.PATIENT) R.string.access_patient else R.string.access_staff), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(if (mode == AccessMode.PATIENT) R.string.access_patient_intro else R.string.access_staff_intro))
        AsclepioButton(onClick = { onSelect(mode) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (mode == AccessMode.PATIENT) R.string.access_patient_action else R.string.access_staff_action))
        }
    }
}

@Composable
fun SessionVerificationScreen(message: UiMessage, onRetry: () -> Unit, onLogout: () -> Unit) {
    ScreenColumn {
        PageHeader(stringResource(R.string.session_verification_title))
        UiNotice(message.text(), NoticeTone.Error)
        AsclepioButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        OutlinedButton(onClick = onLogout) { Text(stringResource(R.string.logout_confirm)) }
    }
}

@Composable
fun AccessDeniedScreen(onHome: () -> Unit) {
    ScreenColumn {
        UiNotice(stringResource(R.string.access_denied), NoticeTone.Warning)
        AsclepioButton(onClick = onHome) { Text(stringResource(R.string.access_go_home)) }
    }
}
