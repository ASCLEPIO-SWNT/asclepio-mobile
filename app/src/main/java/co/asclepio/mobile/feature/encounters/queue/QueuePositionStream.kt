package co.asclepio.mobile.feature.encounters.queue

import co.asclepio.mobile.core.auth.SessionManager
import co.asclepio.mobile.core.auth.patientStream
import co.asclepio.mobile.core.network.appJson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.time.Duration

/** Lo que el stream entrega al ViewModel. Nunca lleva el texto del evento, solo la instantánea ya leída (R10). */
sealed interface StreamEvent {
    data class Snapshot(val position: QueuePositionDto) : StreamEvent

    /** El servidor cerró el stream (tras DONE o al vencer el token). */
    data object Closed : StreamEvent

    /** Corte de la conexión ([code] `null`) o respuesta de error antes de abrir el stream (401, 403, 5xx…). */
    data class Failed(val code: Int?) : StreamEvent
}

/**
 * `GET /api/v1/patient/queue-position/stream` (contrato 0.10.0, D1) sobre el `OkHttpClient` de la app. El Bearer se
 * captura de la generación vigente y un 401 cierra solo esa sesión (R9), como `patientCall`. Cancelar la recolección
 * cancela la llamada (R2). Solo se lee el evento `position`; el latido `: ping` lo descarta okhttp-sse. No hay logs (R10).
 */
class QueuePositionStream(client: OkHttpClient, baseUrl: String, private val sessionManager: SessionManager) {
    // El servidor envía un latido cada 15 s: sin datos durante READ_TIMEOUT la conexión se da por cortada (R8).
    private val factory: EventSource.Factory = EventSources.createFactory(client.newBuilder().readTimeout(READ_TIMEOUT).build())
    private val url = (if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/").toHttpUrl().resolve(PATH)!!

    fun stream(): Flow<StreamEvent> =
        sessionManager.patientStream(
            isTerminal = { it !is StreamEvent.Snapshot },
            isUnauthorized = { it is StreamEvent.Failed && it.code == 401 },
            open = ::open,
        )

    private fun open(authorization: String): Flow<StreamEvent> = callbackFlow {
        val request = Request.Builder().url(url).header("Authorization", authorization).header("Accept", EVENT_STREAM).build()
        val source = factory.newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (type != POSITION_EVENT) return
                    // Un evento ilegible se descarta sin registrar su contenido.
                    val snapshot = runCatching { appJson.decodeFromString(QueuePositionDto.serializer(), data) }.getOrNull() ?: return
                    trySend(StreamEvent.Snapshot(snapshot))
                }

                override fun onClosed(eventSource: EventSource) {
                    trySend(StreamEvent.Closed)
                    channel.close()
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    trySend(StreamEvent.Failed(response?.code?.takeUnless { it in 200..299 }))
                    channel.close()
                }
            },
        )
        awaitClose { source.cancel() }
    }

    private companion object {
        const val PATH = "api/v1/patient/queue-position/stream"
        const val POSITION_EVENT = "position"
        const val EVENT_STREAM = "text/event-stream"
        val READ_TIMEOUT: Duration = Duration.ofSeconds(45)
    }
}
