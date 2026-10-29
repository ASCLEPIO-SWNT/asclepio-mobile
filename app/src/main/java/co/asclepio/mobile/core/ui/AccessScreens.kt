package co.asclepio.mobile.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.AccessMode

@Composable
fun WelcomeScreen(widthSizeClass: WindowWidthSizeClass, onSelect: (AccessMode) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 840.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.tagline))
            Text(stringResource(R.string.access_choose), style = MaterialTheme.typography.titleLarge)
            if (widthSizeClass == WindowWidthSizeClass.Compact) {
                AccessCard(AccessMode.PATIENT, Modifier.fillMaxWidth(), onSelect)
                AccessCard(AccessMode.STAFF, Modifier.fillMaxWidth(), onSelect)
            } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AccessCard(AccessMode.PATIENT, Modifier.weight(1f), onSelect)
                AccessCard(AccessMode.STAFF, Modifier.weight(1f), onSelect)
            }
        }
    }
}

@Composable
private fun AccessCard(mode: AccessMode, modifier: Modifier, onSelect: (AccessMode) -> Unit) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (mode == AccessMode.PATIENT) R.string.access_patient else R.string.access_staff), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(if (mode == AccessMode.PATIENT) R.string.access_patient_intro else R.string.access_staff_intro))
            Button(onClick = { onSelect(mode) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (mode == AccessMode.PATIENT) R.string.access_patient_action else R.string.access_staff_action))
            }
        }
    }
}

@Composable
fun SessionVerificationScreen(message: UiMessage, onRetry: () -> Unit, onLogout: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.session_verification_title), style = MaterialTheme.typography.headlineSmall)
            Text(message.text())
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            OutlinedButton(onClick = onLogout) { Text(stringResource(R.string.logout_confirm)) }
        }
    }
}

@Composable
fun AccessDeniedScreen(onHome: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.access_denied), style = MaterialTheme.typography.titleLarge)
            Button(onClick = onHome) { Text(stringResource(R.string.access_go_home)) }
        }
    }
}
