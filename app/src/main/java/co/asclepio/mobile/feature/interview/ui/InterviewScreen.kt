package co.asclepio.mobile.feature.interview.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.asclepio.mobile.R
import co.asclepio.mobile.core.interview.InterviewLimits
import co.asclepio.mobile.core.interview.UrgentSubmission
import co.asclepio.mobile.core.ui.AsclepioButton
import co.asclepio.mobile.core.ui.AsclepioTextField
import co.asclepio.mobile.core.ui.NoticeTone
import co.asclepio.mobile.core.ui.PageHeader
import co.asclepio.mobile.core.ui.ScreenColumn
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.core.ui.UiNotice
import co.asclepio.mobile.core.ui.text

/**
 * R12, R13: en la pantalla de alarma no se puede salir (gesto atrás ni "Volver") hasta que el envío urgente termine:
 * enviado, o un error definitivo que no se reintenta. Así no se descarta el prerregistro de alarma por accidente.
 */
fun redFlagBlocksBack(state: InterviewUiState, urgent: UrgentSubmission?): Boolean =
    state is InterviewUiState.RedFlag && urgent != UrgentSubmission.Sent && urgent !is UrgentSubmission.Failed

/** Texto del estado del envío de alarma (catálogo cerrado; nunca contenido del paciente). */
@StringRes
fun urgentStatusText(status: UrgentSubmission): Int = when (status) {
    UrgentSubmission.Sending -> R.string.red_flag_sending
    is UrgentSubmission.Retrying -> R.string.red_flag_retrying
    UrgentSubmission.Sent -> R.string.red_flag_sent
    is UrgentSubmission.Failed -> R.string.red_flag_failed
}

/**
 * Entrevista de síntomas (R6, R8, R12, R14): aviso del servidor, una pregunta a la vez con "No sé", "Terminar" y
 * "Siguiente", resumen editable y pantalla de alarma. [dictation] es el slot de los controles de F-08 que compone la
 * raíz. Solo muestra preguntas que ya pasaron el validador o son fijas (R10).
 */
@Composable
fun InterviewScreen(
    state: InterviewUiState,
    onAcceptNotice: () -> Unit,
    onSkip: () -> Unit,
    onDownloadModel: () -> Unit,
    onAnswerChange: (String) -> Unit,
    onDontKnow: () -> Unit,
    onFinish: () -> Unit,
    onNext: () -> Unit,
    onEditAnswer: (Int, String) -> Unit,
    onConfirmSummary: () -> Unit,
    summarySubmitting: Boolean,
    summaryMessage: UiMessage?,
    urgent: UrgentSubmission?,
    onUrgentDone: () -> Unit,
    modifier: Modifier = Modifier,
    dictation: @Composable () -> Unit = {},
) {
    when (state) {
        InterviewUiState.Preparing, InterviewUiState.Skipped -> ScreenColumn(modifier) {
            PageHeader(stringResource(R.string.interview_title))
            CircularProgressIndicator()
            Text(stringResource(R.string.interview_preparing))
        }
        is InterviewUiState.ServerNotice -> ServerNotice(state, onAcceptNotice, onSkip, onDownloadModel, modifier)
        is InterviewUiState.Asking -> Asking(state, onAnswerChange, onDontKnow, onFinish, onNext, dictation, modifier)
        is InterviewUiState.Summary -> InterviewSummary(state, onEditAnswer, onConfirmSummary, summarySubmitting, summaryMessage, modifier)
        is InterviewUiState.RedFlag -> RedFlagScreen(urgent, onUrgentDone, modifier)
    }
}

@Composable
private fun ServerNotice(state: InterviewUiState.ServerNotice, onAccept: () -> Unit, onSkip: () -> Unit, onDownload: () -> Unit, modifier: Modifier) {
    ScreenColumn(modifier) {
        PageHeader(stringResource(R.string.server_notice_title))
        UiNotice(stringResource(R.string.server_notice_body), NoticeTone.Info)
        AsclepioButton(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_notice_continue)) }
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_notice_skip)) }
        if (state.canDownload) {
            if (state.downloadRequested) {
                UiNotice(stringResource(R.string.server_notice_download_requested), NoticeTone.Success)
            } else {
                TextButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_notice_download)) }
            }
        }
    }
}

@Composable
private fun Asking(
    state: InterviewUiState.Asking,
    onAnswerChange: (String) -> Unit,
    onDontKnow: () -> Unit,
    onFinish: () -> Unit,
    onNext: () -> Unit,
    dictation: @Composable () -> Unit,
    modifier: Modifier,
) {
    ScreenColumn(modifier) {
        PageHeader(stringResource(R.string.interview_title), stringResource(R.string.interview_disclaimer))
        Text(stringResource(R.string.interview_progress, state.number, InterviewLimits.MAX_TURNS), style = MaterialTheme.typography.labelLarge)
        // La pregunta es texto validado o fijo, siempre en español (el brief deja otros idiomas fuera de alcance).
        Text(
            state.question,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        AsclepioTextField(
            value = state.answer,
            onValueChange = onAnswerChange,
            label = { Text(stringResource(R.string.interview_answer)) },
            enabled = !state.generating,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        dictation()
        if (state.generating) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.interview_generating))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDontKnow, enabled = !state.generating, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.interview_dont_know)) }
            OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.interview_finish)) }
        }
        AsclepioButton(onClick = onNext, enabled = state.canSend, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.interview_next)) }
    }
}

/** R14: resumen editable de las respuestas (no verificadas) antes de enviar. */
@Composable
fun InterviewSummary(
    state: InterviewUiState.Summary,
    onEditAnswer: (Int, String) -> Unit,
    onConfirm: () -> Unit,
    submitting: Boolean,
    message: UiMessage?,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        PageHeader(stringResource(R.string.summary_title), stringResource(R.string.summary_unverified))
        if (state.result.assistantUnavailable) UiNotice(stringResource(R.string.summary_unavailable), NoticeTone.Warning)
        state.result.turns.forEachIndexed { index, turn ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(turn.question, style = MaterialTheme.typography.titleSmall)
                AsclepioTextField(
                    value = state.answers[index].orEmpty(),
                    onValueChange = { onEditAnswer(index, it) },
                    label = { Text(stringResource(if (state.answers[index] == null) R.string.interview_dont_know else R.string.interview_answer)) },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        message?.let { UiNotice(it.text(), NoticeTone.Error) }
        AsclepioButton(onClick = onConfirm, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
            if (submitting) CircularProgressIndicator(Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.preregistration_submit))
        }
    }
}

/**
 * R12, R13: "Acude ya a urgencias o llama al 123". El prerregistro se envía de inmediato y se reintenta mientras esta
 * pantalla siga visible; la raíz lanza y cancela el envío. "Llamar al 123" abre el marcador (no llama solo).
 */
@Composable
fun RedFlagScreen(urgent: UrgentSubmission?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    ScreenColumn(modifier) {
        Text(
            stringResource(R.string.red_flag_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Assertive
            },
        )
        Text(stringResource(R.string.red_flag_body), style = MaterialTheme.typography.bodyLarge)
        AsclepioButton(onClick = { runCatching { uri.openUri("tel:123") } }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.red_flag_call))
        }
        urgent?.let { status ->
            val text = when (status) {
                is UrgentSubmission.Retrying -> stringResource(urgentStatusText(status), status.seconds)
                is UrgentSubmission.Failed -> stringResource(urgentStatusText(status)) + " " + status.message.text()
                else -> stringResource(urgentStatusText(status))
            }
            UiNotice(text, if (status == UrgentSubmission.Sent) NoticeTone.Success else if (status is UrgentSubmission.Sending) NoticeTone.Info else NoticeTone.Warning)
            if (status == UrgentSubmission.Sending) CircularProgressIndicator()
            if (status == UrgentSubmission.Sent || status is UrgentSubmission.Failed) {
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.red_flag_home)) }
            }
        }
    }
}
