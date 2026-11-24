package co.asclepio.mobile.feature.encounters.queue

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.Route
import co.asclepio.mobile.core.auth.SessionInvalidated
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.rootRoute
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Bucle de reconexión del ViewModel con el reloj de corrutinas (tiempo virtual) y un origen falso del stream. */
class QueuePositionViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private var elapsed = 5_000_000L
    private val waits = mutableListOf<Long>()
    private var opened = 0

    private fun dto(phase: QueuePhase, position: Int? = null) = QueuePositionDto(
        phase, position, position?.let { it - 1 }, position?.let { WaitEstimateDto(15, 35) },
        position?.let { EstimateBasis.RECENT_THROUGHPUT }, "2026-10-27T15:00:00.000Z",
    )

    /** Cada apertura devuelve el siguiente guion; después de los guiones, cortes. Un guion que acaba en instantánea sigue abierto. */
    private fun vm(vararg scripts: List<StreamEvent>): QueuePositionViewModel =
        QueuePositionViewModel(
            source = {
                val script = scripts.getOrElse(opened) { listOf(StreamEvent.Failed(null)) }
                opened++
                flow {
                    script.forEach { emit(it) }
                    if (script.lastOrNull() is StreamEvent.Snapshot) awaitCancellation()
                }
            },
            elapsedMs = { elapsed },
            wait = { waits += it; delay(it) },
        )

    private fun TestScope.follow(vm: QueuePositionViewModel) = launch { vm.follow() }

    @Test fun `R8 sin conexion reintenta con backoff 1 2 4 8 16 y 30 s como maximo`() = runTest {
        val vm = vm()
        val job = follow(vm)
        advanceTimeBy(1_000L + 2_000 + 4_000 + 8_000 + 16_000 + 30_000 + 30_000 + 1)
        job.cancel()
        assertEquals(listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000, 30_000), waits)
        assertEquals(QueueConnection.RECONNECTING, vm.state.value.connection)
        assertNull(vm.state.value.last)
    }

    @Test fun `R8 el backoff vuelve a 1 s tras recibir un evento`() = runTest {
        val snapshot = StreamEvent.Snapshot(dto(QueuePhase.WAITING_TRIAGE, 3))
        val vm = vm(
            listOf(StreamEvent.Failed(null)),
            listOf(StreamEvent.Failed(500)),
            listOf(snapshot, StreamEvent.Closed),
            listOf(StreamEvent.Failed(null)),
        )
        val job = follow(vm)
        advanceTimeBy(1_000L + 2_000 + 1_000 + 2_000 + 1)
        job.cancel()
        assertEquals(listOf(1_000L, 2_000, 1_000, 2_000, 4_000), waits)
    }

    @Test fun `R8 al cortarse conserva el ultimo dato con su hora y muestra RECONNECTING`() = runTest {
        val first = dto(QueuePhase.WAITING_CARE, 2)
        val vm = vm(listOf(StreamEvent.Snapshot(first), StreamEvent.Failed(null)))
        val job = follow(vm)
        runCurrent()
        job.cancel()
        assertEquals(QueueUiState(first, elapsed, QueueConnection.RECONNECTING, ended = false), vm.state.value)
    }

    @Test fun `R8 la reconexion vuelve a LIVE con la instantanea nueva y su hora`() = runTest {
        val first = dto(QueuePhase.WAITING_TRIAGE, 4)
        val second = dto(QueuePhase.WAITING_TRIAGE, 2)
        val vm = vm(listOf(StreamEvent.Snapshot(first), StreamEvent.Failed(null)), listOf(StreamEvent.Snapshot(second)))
        val job = follow(vm)
        runCurrent()
        elapsed += 3 * 60_000L
        advanceTimeBy(1_001)
        job.cancel()
        assertEquals(QueueUiState(second, elapsed, QueueConnection.LIVE, ended = false), vm.state.value)
    }

    @Test fun `R1 el estado inicial no tiene dato y esta conectando en vivo`() {
        assertEquals(QueueUiState(null, null, QueueConnection.LIVE, ended = false), vm().state.value)
    }

    @Test fun `R5 DONE termina sin reconectar y un nuevo follow no vuelve a abrir el stream`() = runTest {
        val done = dto(QueuePhase.DONE)
        val vm = vm(listOf(StreamEvent.Snapshot(dto(QueuePhase.CALLED)), StreamEvent.Snapshot(done), StreamEvent.Closed))
        vm.follow()
        assertEquals(QueueUiState(done, elapsed, QueueConnection.LIVE, ended = true), vm.state.value)
        vm.follow()
        assertEquals(1, opened)
        assertEquals(emptyList<Long>(), waits)
    }

    @Test fun `R1 tras DONE un prerregistro nuevo vuelve a abrir el stream`() = runTest {
        val done = dto(QueuePhase.DONE)
        val vm = vm(listOf(StreamEvent.Snapshot(done), StreamEvent.Closed), listOf(StreamEvent.Snapshot(dto(QueuePhase.NO_ACTIVE))))
        vm.track("pre-1")
        vm.follow()
        assertTrue(vm.state.value.ended)
        vm.track("pre-2")
        assertEquals(QueueUiState(key = "pre-2"), vm.state.value)
        val job = follow(vm)
        runCurrent()
        job.cancel()
        assertEquals(2, opened)
        assertEquals(QueueUiState(dto(QueuePhase.NO_ACTIVE), elapsed, QueueConnection.LIVE, ended = false, key = "pre-2"), vm.state.value)
    }

    @Test fun `R5 tras DONE el mismo prerregistro no vuelve a abrir el stream`() = runTest {
        val vm = vm(listOf(StreamEvent.Snapshot(dto(QueuePhase.DONE)), StreamEvent.Closed))
        vm.track("pre-1")
        vm.follow()
        vm.track("pre-1")
        vm.follow()
        assertTrue(vm.state.value.ended)
        assertEquals(1, opened)
    }

    @Test fun `R9 un 401 termina sin reconectar`() = runTest {
        val vm = vm(listOf(StreamEvent.Failed(401)))
        vm.follow()
        assertTrue(vm.state.value.ended)
        assertEquals(1, opened)
        assertEquals(emptyList<Long>(), waits)
    }

    @Test fun `R9 una sesion invalidada termina sin reconectar`() = runTest {
        val vm = QueuePositionViewModel(source = { flow<StreamEvent> { throw SessionInvalidated() } }, elapsedMs = { elapsed }, wait = { waits += it })
        vm.follow()
        assertTrue(vm.state.value.ended)
        assertEquals(emptyList<Long>(), waits)
    }

    @Test fun `R9 con el stream real un 401 cierra la sesion y la raiz pasa al inicio de sesion`() {
        val env = PatientTestEnv(tmp.newFolder())
        try {
            runBlocking {
                withTimeout(10_000) {
                    env.login()
                    env.server.enqueue(MockResponse().setResponseCode(401).setHeader("Content-Type", "application/problem+json").setBody(env.problem(401)))
                    val stream = QueuePositionStream(env.client, env.server.url("/").toString(), env.sessionManager)
                    val vm = QueuePositionViewModel(stream::stream, { elapsed }, wait = { waits += it })
                    vm.follow()
                    assertTrue(vm.state.value.ended)
                    assertEquals(emptyList<Long>(), waits)
                    assertEquals(SessionState.LoggedOut(env.tenant), env.sessionManager.state.value)
                    assertEquals(Route.Login(), rootRoute(env.sessionManager.state.value))
                }
            }
        } finally {
            env.close()
        }
    }

    @Test fun `R2 cancelar follow detiene el bucle y no programa mas reintentos`() = runTest {
        val vm = vm()
        val job = follow(vm)
        advanceTimeBy(1_500)
        job.cancel()
        val count = opened
        advanceTimeBy(120_000)
        assertEquals(count, opened)
    }
}
