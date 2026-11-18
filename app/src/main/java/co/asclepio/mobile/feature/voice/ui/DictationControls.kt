package co.asclepio.mobile.feature.voice.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.NoticeTone
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.core.ui.UiNotice
import co.asclepio.mobile.core.ui.text
import co.asclepio.mobile.core.voice.DictationUiState
import co.asclepio.mobile.feature.voice.DictationAction
import co.asclepio.mobile.feature.voice.dictationAction

/**
 * Controles del dictado del motivo (R3–R5, R14, R16). `RECORD_AUDIO` se pide solo al pulsar "Dictar" (D9); el
 * resultado del permiso decide si se escucha ([onStart]) o se muestra el aviso ([onPermissionDenied]).
 * No muestra ni recibe texto reconocido: ese texto solo llega al campo del motivo.
 */
@Composable
fun DictationControls(
    state: DictationUiState,
    message: UiMessage?,
    enabled: Boolean,
    onStart: () -> Unit,
    onPermissionDenied: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onStart() else onPermissionDenied()
    }
    val polite = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            DictationUiState.Checking -> Unit
            // D7: sin reconocimiento local en español se oculta "Dictar" y se explica por qué.
            DictationUiState.Unavailable -> UiNotice(stringResource(R.string.dictation_unavailable), NoticeTone.Info, polite)
            DictationUiState.Idle -> {
                OutlinedButton(
                    onClick = {
                        when (dictationAction(hasRecordAudio(context))) {
                            DictationAction.Listen -> onStart()
                            DictationAction.RequestPermission -> permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.dictation_start)) }
                SpanishOnlyNote()
            }
            DictationUiState.Listening, DictationUiState.Processing -> {
                val status = if (state == DictationUiState.Listening) R.string.dictation_listening else R.string.dictation_processing
                Text(stringResource(status), style = MaterialTheme.typography.titleSmall, modifier = polite)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onStop,
                        enabled = state == DictationUiState.Listening,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.dictation_stop)) }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.dictation_cancel))
                    }
                }
                SpanishOnlyNote()
            }
        }
        message?.let { UiNotice(it.text(), NoticeTone.Warning, polite) }
    }
}

@Composable
private fun SpanishOnlyNote() {
    Text(stringResource(R.string.dictation_spanish_only), style = MaterialTheme.typography.bodySmall)
}

private fun hasRecordAudio(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
