package co.asclepio.mobile.feature.encounters.queue

import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.core.auth.SessionInvalidated
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.auth.StoredSession
import co.asclepio.mobile.core.auth.assertPatientSession
import co.asclepio.mobile.core.network.appJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Cliente SSE real (okhttp-sse) contra MockWebServer, que responde como el contrato 0.10.0. */
class QueuePositionStreamTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv
    private lateinit var stream: QueuePositionStream

    @Before fun setUp() {
        env = PatientTestEnv(tmp.root)
        stream = QueuePositionStream(env.client, env.server.url("/").toString(), env.sessionManager)
    }

    @After fun tearDown() = env.close()

    private fun sse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(body)

    private fun event(json: String) = "event: position\ndata: $json\n\n"

    private fun <T> blocking(block: suspend CoroutineScope.() -> T): T = runBlocking { withTimeout(10_000) { block() } }

    private val waiting = snapshot("WAITING_TRIAGE", 4, estimate = """{"minMinutes":15,"maxMinutes":35}""", basis = "RECENT_THROUGHPUT")
    private val next = snapshot("WAITING_TRIAGE", 1, estimate = """{"minMinutes":0,"maxMinutes":5}""", basis = "RECENT_THROUGHPUT")

    @Test fun `R1 abre el stream del paciente con su Bearer y emite cada evento position como instantanea`() = blocking {
        env.login()
        env.server.enqueue(sse(event(waiting) + event(next)))
        val events = stream.stream().toList()
        assertEquals(
            listOf(
                StreamEvent.Snapshot(
                    QueuePositionDto(QueuePhase.WAITING_TRIAGE, 4, 3, WaitEstimateDto(15, 35), EstimateBasis.RECENT_THROUGHPUT, NOW),
                ),
                StreamEvent.Snapshot(
                    QueuePositionDto(QueuePhase.WAITING_TRIAGE, 1, 0, WaitEstimateDto(0, 5), EstimateBasis.RECENT_THROUGHPUT, NOW),
                ),
                StreamEvent.Closed,
            ),
            events,
        )
        val request = env.server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/patient/queue-position/stream", request.path)
        assertEquals("Bearer ${PatientTestEnv.TOKEN}", request.getHeader("Authorization"))
        assertEquals("text/event-stream", request.getHeader("Accept"))
    }

    @Test fun `R1 ignora el latido ping y los eventos de otro tipo`() = blocking {
        env.login()
        env.server.enqueue(sse(": ping\n\n" + "event: other\ndata: {}\n\n" + event(waiting) + ": ping\n\n"))
        val events = stream.stream().toList()
        assertEquals(2, events.size)
        assertTrue(events[0] is StreamEvent.Snapshot)
        assertEquals(StreamEvent.Closed, events[1])
    }

    @Test fun `R5 el cierre del servidor tras DONE termina el flow con Closed`() = blocking {
        env.login()
        env.server.enqueue(sse(event(waiting) + event(snapshot("DONE", null))))
        val events = stream.stream().toList()
        assertEquals(QueuePhase.DONE, (events[1] as StreamEvent.Snapshot).position.phase)
        assertEquals(StreamEvent.Closed, events.last())
    }

    @Test fun `R8 un corte de la conexion termina el flow con Failed sin codigo de error HTTP`() = blocking {
        env.login()
        env.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(listOf(StreamEvent.Failed(null)), stream.stream().toList())
        assertPatientSession(env.sessionManager, env.tenant)
    }

    @Test fun `R8 un corte durante el cuerpo conserva las instantaneas recibidas y termina con Failed`() = blocking {
        env.login()
        val body = event(waiting)
        env.server.enqueue(
            sse(body + event(next)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        val events = stream.stream().toList()
        assertTrue(events.last() is StreamEvent.Failed)
        assertTrue(events.dropLast(1).all { it is StreamEvent.Snapshot })
        assertPatientSession(env.sessionManager, env.tenant)
    }

    @Test fun `R8 un 5xx o un 403 terminan con Failed y su codigo y conservan la sesion`() = blocking {
        env.login()
        for (code in listOf(403, 500, 503)) {
            env.enqueue(code, env.problem(code))
            assertEquals(listOf(StreamEvent.Failed(code)), stream.stream().toList())
            assertPatientSession(env.sessionManager, env.tenant)
        }
    }

    @Test fun `R9 un 401 termina con Failed 401 y cierra la sesion conservando la IPS`() = blocking {
        env.login()
        env.enqueue(401, env.problem(401))
        assertEquals(listOf(StreamEvent.Failed(401)), stream.stream().toList())
        assertEquals(SessionState.LoggedOut(env.tenant), env.sessionManager.state.value)
        assertEquals(StoredSession.None, env.store.session())
        assertNull(env.sessionManager.currentToken())
    }

    @Test fun `R9 un 401 de un stream de una sesion anterior no cierra la sesion nueva`() = blocking {
        env.login()
        env.server.enqueue(MockResponse().setResponseCode(401).setBody(env.problem(401)).setHeadersDelay(1, TimeUnit.SECONDS))
        val stale = launch {
            try {
                stream.stream().toList()
                fail("El 401 de la generación anterior se descarta")
            } catch (_: SessionInvalidated) {
            }
        }
        // Mientras el 401 viaja, otro inicio de sesión abre una generación nueva.
        assertNotNull(withContext(Dispatchers.IO) { env.server.takeRequest(5, TimeUnit.SECONDS) })
        env.login()
        stale.join()
        assertPatientSession(env.sessionManager, env.tenant)
    }

    @Test fun `R2 cancelar la recoleccion cancela la llamada y no deja conexiones abiertas`() = blocking {
        env.login()
        val first = event(waiting)
        // Envía la primera instantánea y retiene el resto del cuerpo (stream abierto).
        env.server.enqueue(sse(first + event(next)).throttleBody(first.length.toLong(), 30, TimeUnit.SECONDS))
        val received = stream.stream().first()
        assertTrue(received is StreamEvent.Snapshot)
        val deadline = System.currentTimeMillis() + 5_000
        while (env.client.dispatcher.runningCallsCount() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(0, env.client.dispatcher.runningCallsCount())
    }

    @Test fun `R2 sin sesion autenticada no se abre ninguna conexion`() = blocking {
        try {
            stream.stream().toList()
            fail("Sin sesión no hay stream")
        } catch (_: SessionInvalidated) {
        }
        assertEquals(0, env.server.requestCount)
    }

    @Test fun `R10 la instantanea contiene solo los campos del contrato e ignora cualquier otro`() {
        val json = """{"phase":"WAITING_CARE","position":2,"aheadCount":1,"estimate":null,"estimateBasis":"INSUFFICIENT_DATA",""" +
            """"serverNow":"$NOW","triageLevel":"I","patientName":"DEV Paciente Prueba","encounterId":"55555555"}"""
        val dto = appJson.decodeFromString(QueuePositionDto.serializer(), json)
        assertEquals(QueuePositionDto(QueuePhase.WAITING_CARE, 2, 1, null, EstimateBasis.INSUFFICIENT_DATA, NOW), dto)
        val text = dto.toString()
        for (secret in listOf("triageLevel", "DEV Paciente", "55555555")) assertFalse(text, text.contains(secret))
        assertEquals(
            listOf("phase", "position", "aheadCount", "estimate", "estimateBasis", "serverNow"),
            QueuePositionDto.serializer().descriptor.let { d -> (0 until d.elementsCount).map(d::getElementName) },
        )
    }

    @Test fun `R10 una fase o una base desconocidas no rompen la instantanea`() {
        val json = """{"phase":"NUEVA","position":null,"aheadCount":null,"estimate":null,"estimateBasis":"OTRA","serverNow":"$NOW"}"""
        val dto = appJson.decodeFromString(QueuePositionDto.serializer(), json)
        assertEquals(QueuePhase.UNKNOWN, dto.phase)
        assertEquals(EstimateBasis.UNKNOWN, dto.estimateBasis)
    }

    @Test fun `R10 el cliente del stream no registra en logs el contenido de los eventos`() {
        val source = File("src/main/java/co/asclepio/mobile/feature/encounters/queue").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue(source.contains("EventSourceListener"))
        for (forbidden in listOf("Log.", "println(", "printStackTrace", "HttpLoggingInterceptor", "Timber")) {
            assertFalse(forbidden, source.contains(forbidden))
        }
    }

    companion object {
        const val NOW = "2026-10-27T15:00:00.000Z"

        fun snapshot(phase: String, position: Int?, estimate: String? = null, basis: String? = null): String {
            val ahead = position?.let { it - 1 }
            fun q(v: String?) = v?.let { "\"$it\"" } ?: "null"
            return """{"phase":"$phase","position":${position ?: "null"},"aheadCount":${ahead ?: "null"},""" +
                """"estimate":${estimate ?: "null"},"estimateBasis":${q(basis)},"serverNow":"$NOW"}"""
        }
    }
}
