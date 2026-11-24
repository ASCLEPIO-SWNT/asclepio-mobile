package co.asclepio.mobile.feature.interview.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.AsclepioButton
import co.asclepio.mobile.core.ui.NoticeTone
import co.asclepio.mobile.core.ui.UiNotice
import co.asclepio.mobile.feature.interview.model.ModelDownloadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

private const val MB = 1_000_000L

/** Texto y acciones de la descarga del asistente (R5): catálogo cerrado, sin datos del paciente. */
data class AssistantView(val text: Int, val args: List<Long> = emptyList(), val canDownload: Boolean, val canCancel: Boolean, val canDelete: Boolean)

/** R5: estado → texto (porcentaje y MB) y botones. Sin RAM suficiente no se ofrece la descarga. */
fun assistantView(state: ModelDownloadState, enoughMemory: Boolean): AssistantView = when (state) {
    ModelDownloadState.NotDownloaded ->
        if (enoughMemory) AssistantView(R.string.assistant_not_downloaded, canDownload = true, canCancel = false, canDelete = false)
        else AssistantView(R.string.assistant_not_capable, canDownload = false, canCancel = false, canDelete = false)
    is ModelDownloadState.Waiting -> AssistantView(R.string.assistant_waiting, canDownload = false, canCancel = true, canDelete = false)
    is ModelDownloadState.Downloading ->
        AssistantView(R.string.assistant_downloading, listOf(state.percent.toLong(), state.written / MB, state.total / MB), canDownload = false, canCancel = true, canDelete = false)
    ModelDownloadState.Verified -> AssistantView(R.string.assistant_ready, canDownload = false, canCancel = false, canDelete = true)
    is ModelDownloadState.NotEnoughSpace ->
        AssistantView(R.string.assistant_no_space, listOf((state.missingBytes + MB - 1) / MB), canDownload = enoughMemory, canCancel = false, canDelete = false)
    ModelDownloadState.Corrupted -> AssistantView(R.string.assistant_corrupted, canDownload = enoughMemory, canCancel = false, canDelete = false)
}

/** Descarga opcional del asistente en Perfil (R1, R5). */
class AssistantSettingsViewModel(
    states: Flow<ModelDownloadState>,
    private val enoughMemory: () -> Boolean,
    private val onDownload: () -> Unit,
    private val onCancel: () -> Unit,
    private val onDelete: () -> Unit,
) : ViewModel() {
    val state: StateFlow<ModelDownloadState> = states.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelDownloadState.NotDownloaded)

    fun view(state: ModelDownloadState): AssistantView = assistantView(state, enoughMemory())

    fun download() = onDownload()

    fun cancel() = onCancel()

    fun delete() = onDelete()
}

/**
 * Sección "Asistente de síntomas" de Perfil (R1, R5): descarga solo por Wi-Fi, progreso en porcentaje y MB, cancelar
 * y borrar. En Android 13+ pide el permiso de notificaciones al pulsar "Descargar" (la descarga sigue aunque se niegue).
 */
@Composable
fun AssistantSettings(view: AssistantView, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit, progress: Float?, modifier: Modifier = Modifier) {
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDownload() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.assistant_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.assistant_intro), style = MaterialTheme.typography.bodyMedium)
        UiNotice(stringResource(view.text, *view.args.toTypedArray()), if (view.text == R.string.assistant_ready) NoticeTone.Success else NoticeTone.Info)
        progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
        if (view.canDownload) {
            AsclepioButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else onDownload()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.assistant_download)) }
        }
        if (view.canCancel) OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.assistant_cancel)) }
        if (view.canDelete) OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.assistant_delete)) }
    }
}
