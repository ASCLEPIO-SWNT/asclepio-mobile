package co.asclepio.mobile.feature.encounters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.NoticeTone
import co.asclepio.mobile.core.ui.Panel
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.core.ui.UiNotice
import co.asclepio.mobile.core.ui.text
import co.asclepio.mobile.feature.encounters.queue.EstimateBasis
import co.asclepio.mobile.feature.encounters.queue.QueueConnection
import co.asclepio.mobile.feature.encounters.queue.QueuePhase
import co.asclepio.mobile.feature.encounters.queue.QueuePositionDto
import co.asclepio.mobile.feature.encounters.queue.QueueUiState
import kotlinx.coroutines.delay

/**
 * Lo que muestra la tarjeta de fila (R3–R8): líneas del catálogo con solo los campos de la instantánea (R10), el aviso
 * "Sin conexión" y si se está reconectando.
 */
data class QueueCardContent(val lines: List<UiMessage>, val offline: UiMessage?, val reconnecting: Boolean)

/**
 * `null` si no hay nada que mostrar (sesión terminada sin dato, o una fase que el contrato aún no publica).
 * [nowElapsedMs] es del mismo reloj monótono que `QueueUiState.receivedAt`.
 */
fun queueCardContent(state: QueueUiState, nowElapsedMs: Long): QueueCardContent? {
    val last = state.last
    val reconnecting = state.connection == QueueConnection.RECONNECTING && !state.ended
    val lines = when {
        last != null -> snapshotLines(last)
        state.ended -> return null
        reconnecting -> emptyList()
        else -> listOf(UiMessage(R.string.queue_connecting))
    }
    val offline = when {
        !reconnecting -> null
        last != null && state.receivedAt != null ->
            UiMessage(R.string.queue_offline_age, listOf(((nowElapsedMs - state.receivedAt) / MINUTE_MS).coerceAtLeast(0).toInt()))
        else -> UiMessage(R.string.queue_offline)
    }
    if (lines.isEmpty() && offline == null) return null
    return QueueCardContent(lines, offline, reconnecting)
}

/**
 * Si la entrada Home muestra la tarjeta. Con un pre-registro en curso ([queueKey]), solo si el estado es de ese
 * pre-registro: nunca "Atención finalizada" de uno anterior junto a uno nuevo (R5). Sin pre-registro en curso, solo
 * el final de la atención seguida (DONE).
 */
fun showsQueueCard(queueKey: String?, state: QueueUiState): Boolean =
    if (queueKey != null) state.key == queueKey else state.ended

private fun snapshotLines(s: QueuePositionDto): List<UiMessage> = when (s.phase) {
    QueuePhase.WAITING_TRIAGE, QueuePhase.WAITING_CARE -> buildList {
        add(UiMessage(if (s.phase == QueuePhase.WAITING_TRIAGE) R.string.queue_phase_waiting_triage else R.string.queue_phase_waiting_care))
        val position = s.position
        if (position != null) {
            if (position == 1) {
                add(UiMessage(R.string.queue_next))
            } else {
                add(UiMessage(R.string.queue_position, listOf(position)))
                val ahead = s.aheadCount ?: (position - 1)
                add(if (ahead == 1) UiMessage(R.string.queue_ahead_one) else UiMessage(R.string.queue_ahead_many, listOf(ahead)))
            }
            val estimate = s.estimate
            when {
                s.estimateBasis == EstimateBasis.INSUFFICIENT_DATA -> add(UiMessage(R.string.queue_estimate_insufficient))
                estimate != null -> {
                    add(UiMessage(R.string.queue_estimate_range, listOf(estimate.minMinutes, estimate.maxMinutes)))
                    add(UiMessage(R.string.queue_estimate_may_change))
                }
            }
        }
    }
    QueuePhase.CALLED -> listOf(UiMessage(R.string.queue_called))
    QueuePhase.DONE -> listOf(UiMessage(R.string.queue_done))
    QueuePhase.NO_ACTIVE -> listOf(UiMessage(R.string.queue_no_active))
    QueuePhase.UNKNOWN -> emptyList()
}

/**
 * Tarjeta de fila en la zona de estado del pre-registro. Se anuncia con `liveRegion` educado (R11). La antigüedad de
 * "Sin conexión" se recalcula cada [AGE_TICK_MS] solo mientras se reconecta y la pantalla está al menos en STARTED (R2).
 * [elapsedMs] es el reloj monótono con el que el ViewModel fechó el último dato (lo inyecta `AppNavigation`).
 */
@Composable
fun QueuePositionCard(state: QueueUiState, elapsedMs: () -> Long, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by produceState(elapsedMs(), state.connection, state.receivedAt) {
        value = elapsedMs()
        if (state.connection == QueueConnection.RECONNECTING) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = elapsedMs()
                    delay(AGE_TICK_MS)
                }
            }
        }
    }
    val content = queueCardContent(state, current) ?: return
    Panel(modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.titleMedium)
        content.lines.forEachIndexed { index, line ->
            Text(line.text(), style = if (index == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
        }
        content.offline?.let { UiNotice(it.text(), NoticeTone.Warning) }
        if (content.reconnecting) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.queue_reconnecting), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private const val AGE_TICK_MS = 30_000L
private const val MINUTE_MS = 60_000L
