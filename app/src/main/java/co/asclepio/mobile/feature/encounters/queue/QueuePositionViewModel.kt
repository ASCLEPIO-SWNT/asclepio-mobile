package co.asclepio.mobile.feature.encounters.queue

import androidx.lifecycle.ViewModel
import co.asclepio.mobile.core.auth.SessionInvalidated
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class QueueConnection { LIVE, RECONNECTING }

/**
 * Tarjeta de fila (F-10-mobile). [last] es el último dato, solo en memoria (D4), y [receivedAt] el instante local en que
 * llegó, en milisegundos de un reloj monótono (solo sirve para su antigüedad: no es una hora legal, R22 de F-02).
 * [ended] tras DONE o un 401/sesión invalidada: ya no se reconecta (R5, R9). [key] es el pre-registro que se sigue.
 */
data class QueueUiState(
    val last: QueuePositionDto? = null,
    val receivedAt: Long? = null,
    val connection: QueueConnection = QueueConnection.LIVE,
    val ended: Boolean = false,
    val key: String? = null,
)

/**
 * Bucle de reconexión (D2): backoff de 1 s que se duplica hasta 30 s y vuelve a 1 s tras un evento (R8). [follow] se
 * ejecuta en la corrutina de quien lo llama (`repeatOnLifecycle(STARTED)` en `AppNavigation`, D3), así que bajar de
 * STARTED lo cancela junto con la conexión (R2) y el ViewModel no mantiene trabajo propio.
 */
class QueuePositionViewModel(
    private val source: () -> Flow<StreamEvent>,
    private val elapsedMs: () -> Long,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) : ViewModel() {
    private val _state = MutableStateFlow(QueueUiState())
    val state: StateFlow<QueueUiState> = _state.asStateFlow()

    /**
     * Sigue el pre-registro [key]. Uno distinto (un pre-registro nuevo tras DONE en la misma sesión) empieza de cero y
     * vuelve a abrir el stream (R1); el mismo conserva su estado, así que tras DONE no se reconecta (R5).
     */
    fun track(key: String) {
        if (_state.value.key != key) _state.value = QueueUiState(key = key)
    }

    suspend fun follow() {
        var backoff = INITIAL_BACKOFF_MS
        while (!_state.value.ended) {
            var unauthorized = false
            try {
                source().collect { event ->
                    when (event) {
                        is StreamEvent.Snapshot -> {
                            backoff = INITIAL_BACKOFF_MS
                            val done = event.position.phase == QueuePhase.DONE
                            _state.update { QueueUiState(event.position, elapsedMs(), QueueConnection.LIVE, ended = done, key = it.key) }
                        }
                        is StreamEvent.Failed -> unauthorized = event.code == 401
                        StreamEvent.Closed -> Unit
                    }
                }
            } catch (_: SessionInvalidated) {
                unauthorized = true
            }
            if (unauthorized) _state.update { it.copy(ended = true) }
            if (_state.value.ended) return
            _state.update { it.copy(connection = QueueConnection.RECONNECTING) }
            wait(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
