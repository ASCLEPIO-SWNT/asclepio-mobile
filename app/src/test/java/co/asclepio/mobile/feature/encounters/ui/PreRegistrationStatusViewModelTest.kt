package co.asclepio.mobile.feature.encounters.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.ui.UiMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreRegistrationStatusViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv

    @Before fun setUp() {
        env = PatientTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    private fun list(vararg items: String) = items.joinToString(",", "[", "]")

    private suspend fun TestScope.opened(body: String): PreRegistrationStatusViewModel {
        env.login()
        env.enqueue(200, body)
        val vm = PreRegistrationStatusViewModel(env.preRegistrationRepository)
        vm.state.first { it !is PreRegistrationStatusUiState.Loading }
        assertEquals("/api/v1/patient/pre-registrations", env.server.takeRequest().path)
        return vm
    }

    // preRegisteredAt 14:30Z y expiresAt 20:30Z del entorno de pruebas, mostrados en America/Bogota (UTC-5).
    private val sent = "27/10/2026 09:30:00"
    private val expires = "27/10/2026 15:30:00"

    @Test fun `R17 al mostrarse el inicio pide la lista y muestra sede, estado, Enviado y vence del ultimo PENDING`() = runTest {
        val older = PatientTestEnv.preRegistration("EXPIRED", siteName = "Sede Vieja")
        val vm = opened(list(PatientTestEnv.preRegistration("PENDING"), older))
        assertEquals(
            PreRegistrationStatusUiState.Success("Sede Norte", R.string.status_pending, sent, expires, canCreate = false),
            vm.state.value,
        )
    }

    @Test fun `R17 Actualizar vuelve a pedir la lista y muestra el estado nuevo sin hora de vencimiento`() = runTest {
        val vm = opened(list(PatientTestEnv.preRegistration("PENDING")))
        env.enqueue(200, list(PatientTestEnv.preRegistration("CONFIRMED", "WAITING_TRIAGE")))
        vm.refresh()
        val s = vm.state.first { it is PreRegistrationStatusUiState.Success && it.statusRes != R.string.status_pending }
        assertEquals(PreRegistrationStatusUiState.Success("Sede Norte", R.string.status_waiting_triage, sent, null, false), s)
        assertEquals("/api/v1/patient/pre-registrations", env.server.takeRequest().path)
    }

    @Test fun `R17 solo PENDING muestra la hora de vencimiento`() {
        for (status in listOf("EXPIRED", "REJECTED", "CONFIRMED", "OTRO")) {
            val dto = PatientTestEnv.preRegistration(status)
            val ui = toStatusUi(kotlinx.serialization.json.Json.decodeFromString(dtoSerializer, dto))
            assertEquals(status, null, ui.expiresAt)
        }
    }

    @Test fun `R18 con la lista vacia muestra Aun no has enviado y la accion Nuevo pre-registro`() = runTest {
        val s = opened("[]").state.value
        assertEquals(PreRegistrationStatusUiState.Empty, s)
        assertTrue(s.canCreate)
    }

    @Test fun `R19 Nuevo pre-registro solo se ofrece si el ultimo no esta en curso`() = runTest {
        env.login()
        val cases = listOf(
            PatientTestEnv.preRegistration("PENDING") to false,
            PatientTestEnv.preRegistration("CONFIRMED") to false,
            PatientTestEnv.preRegistration("CONFIRMED", "IN_CARE") to false,
            PatientTestEnv.preRegistration("CONFIRMED", "NUEVO") to false,
            PatientTestEnv.preRegistration("EXPIRED") to true,
            PatientTestEnv.preRegistration("REJECTED") to true,
            PatientTestEnv.preRegistration("CONFIRMED", "CLOSED") to true,
            PatientTestEnv.preRegistration("CONFIRMED", "CANCELLED") to true,
        )
        for ((body, canCreate) in cases) {
            env.enqueue(200, list(body))
            val vm = PreRegistrationStatusViewModel(env.preRegistrationRepository)
            val s = vm.state.first { it !is PreRegistrationStatusUiState.Loading }
            assertEquals(body, canCreate, s.canCreate)
        }
    }

    @Test fun `R20 si la lista falla muestra el error con Actualizar y sin Nuevo pre-registro`() = runTest {
        env.login()
        env.enqueueDisconnect()
        val vm = PreRegistrationStatusViewModel(env.preRegistrationRepository)
        val s = vm.state.first { it !is PreRegistrationStatusUiState.Loading }
        assertEquals(PreRegistrationStatusUiState.Error(UiMessage(R.string.error_network), refreshEnabled = true), s)
        assertFalse(s.canCreate)
        env.enqueue(500, env.problem(500))
        vm.refresh()
        assertEquals(
            PreRegistrationStatusUiState.Error(UiMessage(R.string.error_general), refreshEnabled = true),
            vm.state.first { it !is PreRegistrationStatusUiState.Loading },
        )
        env.enqueue(200, "[]")
        vm.refresh()
        assertEquals(PreRegistrationStatusUiState.Empty, vm.state.first { it !is PreRegistrationStatusUiState.Loading })
    }

    @Test fun `R20 una respuesta que no corresponde al contrato se muestra como error general`() = runTest {
        env.login()
        env.enqueue(200, list(PatientTestEnv.preRegistration().replace("2026-10-27T14:30:00.000Z", "ayer")))
        val vm = PreRegistrationStatusViewModel(env.preRegistrationRepository)
        assertEquals(
            PreRegistrationStatusUiState.Error(UiMessage(R.string.error_general), refreshEnabled = true),
            vm.state.first { it !is PreRegistrationStatusUiState.Loading },
        )
    }

    @Test fun `R21 el estado no contiene nivel, id de la atencion ni campos ajenos al contrato`() = runTest {
        val extra = ""","level":"ESI_2","arrivalAt":"2026-10-27T14:45:00.000Z","rejectionReason":"Motivo interno""""
        val vm = opened(list(PatientTestEnv.preRegistration("CONFIRMED", "TRIAGED", extra = extra)))
        val s = vm.state.value
        assertEquals(PreRegistrationStatusUiState.Success("Sede Norte", R.string.status_triaged, sent, null, false), s)
        val text = s.toString()
        for (forbidden in listOf("ESI", "level", "55555555", "66666666", "Motivo interno", "09:45")) {
            assertFalse(forbidden, text.contains(forbidden))
        }
        val fields = PreRegistrationStatusUiState.Success::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) || it.isSynthetic }.map { it.name }.toSet()
        assertEquals(setOf("siteName", "statusRes", "sentAt", "expiresAt", "canCreate"), fields)
    }

    @Test fun `R22 no muestra hora de llegada ni calcula horas con el reloj del dispositivo`() {
        val sources = File("src/main/java/co/asclepio/mobile/feature/encounters").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        val clockUse = Regex("""Instant\.now|Clock\b|System\.currentTimeMillis|LocalDateTime\.now|ZonedDateTime\.now|arrivalAt""")
        val offenders = sources.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l -> if (clockUse.containsMatchIn(l)) "${f.name}:${i + 1}" else null }
        }
        assertTrue(offenders.toString(), offenders.isEmpty())
    }

    @Test fun `R12 el pre-registro de la respuesta 201 se muestra en el inicio al volver`() = runTest {
        val status = opened("[]")
        env.enqueue(200, PatientTestEnv.tenants())
        val form = PreRegistrationViewModel(env.preRegistrationRepository, env.sessionManager)
        form.state.first { it.sites is SitesUiState.Loaded }
        form.updateComplaint("Fiebre")
        env.enqueue(201, PatientTestEnv.preRegistration("PENDING"))
        form.submit()
        val s = status.state.first { it is PreRegistrationStatusUiState.Success }
        assertEquals(PreRegistrationStatusUiState.Success("Sede Norte", R.string.status_pending, sent, expires, false), s)
        assertEquals(3, env.server.requestCount)
    }

    @Test fun `R24 un 429 deshabilita Actualizar durante Retry-After`() = runTest {
        env.login()
        env.enqueue(429, env.problem(429), retryAfter = "20")
        val vm = PreRegistrationStatusViewModel(env.preRegistrationRepository)
        assertEquals(
            PreRegistrationStatusUiState.Error(UiMessage(R.string.error_too_many_requests, listOf(20)), refreshEnabled = false),
            vm.state.first { it !is PreRegistrationStatusUiState.Loading },
        )
        vm.refresh()
        advanceTimeBy(19_000)
        runCurrent()
        assertEquals(1, env.server.requestCount)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(true, (vm.state.value as PreRegistrationStatusUiState.Error).refreshEnabled)
    }

    @Test fun `R23 un 401 de la lista borra la sesion`() = runTest {
        env.login()
        env.enqueue(401, env.problem(401))
        val vm = PreRegistrationStatusViewModel(env.preRegistrationRepository)
        vm.state.first { it !is PreRegistrationStatusUiState.Loading }
        assertEquals(SessionState.LoggedOut(env.tenant), env.sessionManager.state.value)
    }

    @Test fun `R17 dos actualizaciones seguidas no envian dos peticiones a la vez`() = runTest {
        env.login()
        env.enqueue(200, "[]")
        val vm = PreRegistrationStatusViewModel(env.preRegistrationRepository)
        vm.refresh()
        vm.refresh()
        vm.state.first { it !is PreRegistrationStatusUiState.Loading }
        runCurrent()
        assertEquals(1, env.server.requestCount)
    }

    private val dtoSerializer = co.asclepio.mobile.feature.encounters.data.PatientPreRegistrationDto.serializer()
}
