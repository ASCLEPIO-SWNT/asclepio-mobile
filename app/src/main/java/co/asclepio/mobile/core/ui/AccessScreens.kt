package co.asclepio.mobile.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import co.asclepio.mobile.R

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
