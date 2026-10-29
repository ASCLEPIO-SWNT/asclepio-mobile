package co.asclepio.mobile.feature.encounters.ui

import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.AccessMode
import co.asclepio.mobile.core.auth.MeDto
import co.asclepio.mobile.core.auth.SessionInvalidated
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.auth.StoredSession
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.appJson
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.encounters.data.PatientPreRegistrationDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MyRequestsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv
    @Before fun setUp() { env = PatientTestEnv(tmp.root) }
    @After fun tearDown() = env.close()
    private fun list(vararg items: String) = items.joinToString(",", "[", "]")
    private fun dto(body: String) = appJson.decodeFromString(PatientPreRegistrationDto.serializer(), body)
    private suspend fun opened(body: String): MyRequestsViewModel {
        env.login()
        env.enqueue(200, body)
        return MyRequestsViewModel(env.preRegistrationRepository).also { it.state.first { state -> state !is MyRequestsUiState.Loading } }
    }

    @Test fun `R15 lista propia usa GET sin parametros y Bearer capturado conservando todas las filas recibidas`() = runTest {
        env.login()
        val newest = PatientTestEnv.preRegistration("PENDING", siteName = "Sede más reciente")
        val oldest = PatientTestEnv.preRegistration("EXPIRED", siteName = "Sede anterior")
            .replace("2026-10-27T14:30:00.000Z", "2026-10-26T14:30:00.000Z")
        env.enqueue(200, list(newest, oldest))
        val result = env.preRegistrationRepository.list() as ApiResult.Success
        assertEquals(listOf("Sede más reciente", "Sede anterior"), result.value.map { it.siteName })
        val request = env.server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/patient/pre-registrations", request.path)
        assertEquals("Bearer ${PatientTestEnv.TOKEN}", request.getHeader("Authorization"))
        assertEquals(0, request.bodySize)
    }

    @Test fun `R15 muestra todas las solicitudes sin invertir el orden descendente del servidor`() = runTest {
        val body = list(
            PatientTestEnv.preRegistration("CONFIRMED", "IN_CARE", "Sede reciente"),
            PatientTestEnv.preRegistration("EXPIRED", siteName = "Sede anterior"),
            PatientTestEnv.preRegistration("REJECTED", siteName = "Sede más antigua"),
        )
        val vm = opened(body)
        val state = vm.state.value as MyRequestsUiState.Success
        assertEquals(listOf("Sede reciente", "Sede anterior", "Sede más antigua"), state.items.map { it.siteName })
        assertEquals(3, state.items.size)
    }

    @Test fun `R16 filas muestran estado efectivo sede literal y horas Bogota con expiracion solo PENDING`() = runTest {
        val cases = listOf(
            Triple("PENDING", null, R.string.status_pending),
            Triple("EXPIRED", null, R.string.status_expired),
            Triple("REJECTED", null, R.string.status_rejected),
            Triple("CONFIRMED", null, R.string.status_confirmed),
            Triple("CONFIRMED", "WAITING_TRIAGE", R.string.status_waiting_triage),
            Triple("CONFIRMED", "TRIAGED", R.string.status_triaged),
            Triple("CONFIRMED", "IN_CARE", R.string.status_in_care),
            Triple("CONFIRMED", "CLOSED", R.string.status_closed),
            Triple("CONFIRMED", "CANCELLED", R.string.status_cancelled),
            Triple("UNKNOWN", null, R.string.status_unknown),
            Triple("CONFIRMED", "UNKNOWN", R.string.status_unknown),
        )
        val vm = opened(list(*cases.map { (status, encounter, _) -> PatientTestEnv.preRegistration(status, encounter, "Sede José Müller") }.toTypedArray()))
        val rows = (vm.state.value as MyRequestsUiState.Success).items
        for ((index, case) in cases.withIndex()) {
            val row = rows[index]
            assertEquals("Sede José Müller", row.siteName)
            assertEquals(case.third, row.statusRes)
            assertEquals("27/10/2026 09:30:00", row.sentAt)
            assertEquals(if (case.first == "PENDING") "27/10/2026 15:30:00" else null, row.expiresAt)
        }
    }

    @Test fun `R16 no incorpora nivel id de atencion motivo de rechazo ni hora legal a la lista visible`() = runTest {
        val extra = ""","level":"ESI_2","arrivalAt":"2026-10-27T14:45:00Z","rejectionReason":"Texto interno""""
        val vm = opened(list(PatientTestEnv.preRegistration("CONFIRMED", "TRIAGED", extra = extra)))
        val row = (vm.state.value as MyRequestsUiState.Success).items.single()
        val fields = row.javaClass.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) || it.isSynthetic }.map { it.name }.toSet()
        assertEquals(setOf("siteName", "statusRes", "sentAt", "expiresAt", "canCreate"), fields)
        for (forbidden in listOf("ESI_2", "55555555", "66666666", "Texto interno", "09:45")) assertFalse(row.toString().contains(forbidden))
    }

    @Test fun `R17 entrada foreground y Actualizar comparten una peticion activa y no hay sondeo posterior`() = runTest {
        env.login()
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received.countDown(); check(release.await(3, TimeUnit.SECONDS))
                return MockResponse().setBody("[]")
            }
        }
        val vm = MyRequestsViewModel(env.preRegistrationRepository)
        assertTrue(vm.state.value is MyRequestsUiState.Loading)
        assertTrue(withContext(Dispatchers.IO) { received.await(3, TimeUnit.SECONDS) })
        vm.refresh(); vm.refresh()
        assertEquals(1, env.server.requestCount)
        release.countDown()
        vm.state.first { it is MyRequestsUiState.Empty }
        advanceTimeBy(600_000); runCurrent()
        assertEquals(1, env.server.requestCount)
        vm.refresh()
        vm.state.first { it is MyRequestsUiState.Empty }
        assertEquals(2, env.server.requestCount)
    }

    @Test fun `R17 un error de refresco reemplaza la lista anterior sin presentarla como actual`() = runTest {
        val vm = opened(list(PatientTestEnv.preRegistration(siteName = "Sede privada anterior")))
        env.enqueue(503, env.problem(503))
        vm.refresh()
        assertTrue(vm.state.value is MyRequestsUiState.Loading)
        val state = vm.state.first { it is MyRequestsUiState.Error }
        assertEquals(MyRequestsUiState.Error(UiMessage(R.string.error_general), true), state)
        assertFalse(state.toString().contains("Sede privada anterior"))
    }

    @Test fun `R18 latest y canCreate conservan la regla del primer preregistro sin depender de los antiguos`() = runTest {
        env.login()
        val newest = PatientTestEnv.preRegistration("CONFIRMED", "CLOSED", "La más reciente")
        val oldPending = PatientTestEnv.preRegistration("PENDING", siteName = "Pendiente anterior")
        val body = list(newest, oldPending)
        env.enqueue(200, body)
        val latest = env.preRegistrationRepository.latest() as ApiResult.Success
        assertEquals(dto(newest), latest.value)
        env.enqueue(200, body)
        val home = PreRegistrationStatusViewModel(env.preRegistrationRepository)
        assertTrue(home.state.first { it !is PreRegistrationStatusUiState.Loading }.canCreate)
        env.enqueue(200, body)
        val requests = MyRequestsViewModel(env.preRegistrationRepository)
        assertEquals(2, (requests.state.first { it is MyRequestsUiState.Success } as MyRequestsUiState.Success).items.size)
    }

    @Test fun `R18 conserva 429 con bloqueo RetryAfter y solo permite reintento manual`() = runTest {
        env.login()
        env.enqueue(429, env.problem(429), "3")
        val vm = MyRequestsViewModel(env.preRegistrationRepository)
        assertEquals(MyRequestsUiState.Error(UiMessage(R.string.error_too_many_requests, listOf(3)), false), vm.state.first { it is MyRequestsUiState.Error })
        vm.refresh(); advanceTimeBy(2_999); runCurrent()
        assertFalse((vm.state.value as MyRequestsUiState.Error).refreshEnabled)
        assertEquals(1, env.server.requestCount)
        advanceTimeBy(1); runCurrent()
        assertTrue((vm.state.value as MyRequestsUiState.Error).refreshEnabled)
        assertEquals(1, env.server.requestCount)
        env.enqueue(200, "[]"); vm.refresh()
        assertEquals(MyRequestsUiState.Empty, vm.state.first { it !is MyRequestsUiState.Loading })
    }

    @Test fun `R20 lista es paciente only y no llama HTTP ni almacena filas para personal`() = runTest {
        env.sessionManager.selectAccess(AccessMode.STAFF)
        val attempt = env.sessionManager.beginLogin(AccessMode.STAFF)
        env.sessionManager.completeLogin(attempt, "staff", Instant.parse("2026-10-28T01:00:00Z"), MeDto("STAFF", "staff", "tenant", role = "NURSE", email = "nurse@ips.co", fullName = "Nombre"))
        try { env.preRegistrationRepository.list(); fail("El personal no usa lista paciente") } catch (_: SessionInvalidated) { }
        assertEquals(0, env.server.requestCount)
    }

    @Test fun `R20 un 401 vigente cierra sesion y resultados antiguos no reemplazan una nueva`() = runTest {
        env.login()
        env.enqueue(401, env.problem(401))
        val denied = MyRequestsViewModel(env.preRegistrationRepository)
        denied.state.first { it !is MyRequestsUiState.Loading }
        assertEquals(SessionState.LoggedOut(env.tenant), env.sessionManager.state.value)
        assertEquals(StoredSession.None, env.store.session())
        for (status in listOf(200, 401)) {
            env.login()
            val received = CountDownLatch(1)
            val release = CountDownLatch(1)
            env.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received.countDown(); check(release.await(3, TimeUnit.SECONDS))
                    return MockResponse().setResponseCode(status).setBody(if (status == 200) list(PatientTestEnv.preRegistration(siteName = "Anterior")) else env.problem(401))
                }
            }
            val previous = MyRequestsViewModel(env.preRegistrationRepository)
            assertTrue(withContext(Dispatchers.IO) { received.await(3, TimeUnit.SECONDS) })
            env.login()
            val newer = env.sessionManager.state.value
            release.countDown()
            previous.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals(newer, env.sessionManager.state.value)
            assertTrue(previous.state.value is MyRequestsUiState.Loading)
        }
    }

    @Test fun `R20 solicitudes solo viven en RAM y sus estados no imprimen el contenido privado`() = runTest {
        val literal = "Sede literal José Müller reservada"
        val vm = opened(list(PatientTestEnv.preRegistration(siteName = literal)))
        assertFalse(vm.state.value.toString().contains(literal))
        val raw = tmp.root.walkTopDown().filter { it.isFile }.joinToString { it.readBytes().toString(Charsets.ISO_8859_1) }
        assertFalse(raw.contains(literal))
        assertFalse(raw.contains("66666666-6666-6666-6666-666666666666"))
    }

    @Test fun `R21 carga vacio y error se representan explicitamente y no reproducen mensajes arbitrarios`() = runTest {
        val vm = opened("[]")
        assertEquals(MyRequestsUiState.Empty, vm.state.value)
        for (status in listOf(400, 403, 404, 500)) {
            env.enqueue(status, """{"type":"x","title":"SECRET","status":$status,"detail":"PRIVATE"}""")
            vm.refresh()
            val state = vm.state.first { it is MyRequestsUiState.Error }
            assertEquals(MyRequestsUiState.Error(UiMessage(R.string.error_general), true), state)
            assertFalse(state.toString().contains("SECRET"))
            assertFalse(state.toString().contains("PRIVATE"))
        }
    }

    @Test fun `R21 fecha invalida muestra error local sin filas parciales ni repeticion automatica`() = runTest {
        env.login()
        env.enqueue(200, list(PatientTestEnv.preRegistration().replace("2026-10-27T14:30:00.000Z", "not-a-date")))
        val vm = MyRequestsViewModel(env.preRegistrationRepository)
        assertEquals(MyRequestsUiState.Error(UiMessage(R.string.error_general), true), vm.state.first { it is MyRequestsUiState.Error })
        advanceTimeBy(600_000); runCurrent()
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R21 red fallida muestra mensaje local y espera actualizacion manual sin repetir la llamada`() = runTest {
        env.login()
        env.enqueueDisconnect()
        val vm = MyRequestsViewModel(env.preRegistrationRepository)
        assertEquals(MyRequestsUiState.Error(UiMessage(R.string.error_network), true), vm.state.first { it is MyRequestsUiState.Error })
        advanceTimeBy(600_000); runCurrent()
        assertEquals(1, env.server.requestCount)
    }
}
