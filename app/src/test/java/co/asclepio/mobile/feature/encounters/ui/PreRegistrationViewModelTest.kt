package co.asclepio.mobile.feature.encounters.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.PatientTestEnv.Companion.jsonBody
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.tenants.PublicSiteDto
import co.asclepio.mobile.core.ui.PaneMode
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.encounters.data.ComplaintSource
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreRegistrationViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv

    @Before fun setUp() {
        env = PatientTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    private val norte = PublicSiteDto(PatientTestEnv.SITE_NORTE, "Sede Norte")
    private val sur = PublicSiteDto(PatientTestEnv.SITE_SUR, "Sede Sur")

    private suspend fun TestScope.opened(tenants: String = PatientTestEnv.tenants()): PreRegistrationViewModel {
        env.login()
        env.enqueue(200, tenants)
        val vm = PreRegistrationViewModel(env.preRegistrationRepository, env.sessionManager)
        vm.state.first { it.sites !is SitesUiState.Loading }
        val recorded = env.server.takeRequest()
        assertEquals("/api/v1/public/tenants", recorded.path)
        return vm
    }

    private suspend fun PreRegistrationViewModel.settled() = state.first { !it.submitting }

    private suspend fun TestScope.filled(): PreRegistrationViewModel = opened().apply { updateComplaint("  Dolor de cabeza  ") }

    @Test fun `R8 muestra las sedes de la IPS recordada en el orden recibido con la recordada preseleccionada`() = runTest {
        val s = opened().state.value
        assertEquals(SitesUiState.Loaded(listOf(norte, sur)), s.sites)
        assertEquals(PatientTestEnv.SITE_NORTE, s.form.siteId)
    }

    @Test fun `R8 si la sede recordada no esta en la lista no hay sede seleccionada`() = runTest {
        val s = opened(PatientTestEnv.tenants("""[{"id":"${PatientTestEnv.SITE_SUR}","name":"Sede Sur"}]""")).state.value
        assertEquals(SitesUiState.Loaded(listOf(sur)), s.sites)
        assertNull(s.form.siteId)
    }

    @Test fun `R8 elegir otra sede no cambia la sede recordada`() = runTest {
        val vm = opened()
        vm.selectSite(PatientTestEnv.SITE_SUR)
        assertEquals(PatientTestEnv.SITE_SUR, vm.state.value.form.siteId)
        vm.selectSite("sede-de-otra-ips")
        assertEquals(PatientTestEnv.SITE_SUR, vm.state.value.form.siteId)
        assertEquals(env.tenant, env.store.tenant())
        assertEquals(env.tenant, env.sessionManager.rememberedTenant())
    }

    @Test fun `R9 sin la IPS recordada o sin sedes muestra No hay sedes con reintentar y no permite enviar`() = runTest {
        val noSites = SitesUiState.Error(UiMessage(R.string.error_no_sites), retryEnabled = true)
        assertEquals(noSites, opened(PatientTestEnv.tenants("[]")).state.value.sites)
        env.enqueue(200, """[{"id":"otra","name":"Otra IPS","sites":[{"id":"x","name":"X"}]}]""")
        val vm = PreRegistrationViewModel(env.preRegistrationRepository, env.sessionManager)
        assertEquals(noSites, vm.state.first { it.sites !is SitesUiState.Loading }.sites)
        vm.updateComplaint("Fiebre")
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        runCurrent()
        assertEquals(2, env.server.requestCount)
    }

    @Test fun `R9 si la lista de sedes falla muestra el error con reintentar y reintentar vuelve a pedirla`() = runTest {
        env.login()
        env.enqueueDisconnect()
        val vm = PreRegistrationViewModel(env.preRegistrationRepository, env.sessionManager)
        val s = vm.state.first { it.sites !is SitesUiState.Loading }
        assertEquals(SitesUiState.Error(UiMessage(R.string.error_network), retryEnabled = true), s.sites)
        assertFalse(s.canSubmit)
        env.enqueue(200, PatientTestEnv.tenants())
        vm.retrySites()
        assertTrue(vm.state.first { it.sites !is SitesUiState.Loading }.sites is SitesUiState.Loaded)
    }

    @Test fun `R10 enviar manda POST con siteId, motivo recortado y complaintSource TYPED`() = runTest {
        val vm = filled()
        vm.selectSite(PatientTestEnv.SITE_SUR)
        env.enqueue(201, PatientTestEnv.preRegistration(siteName = "Sede Sur"))
        vm.submit()
        vm.settled()
        val recorded = env.server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/patient/pre-registrations", recorded.path)
        assertEquals(
            mapOf(
                "siteId" to JsonPrimitive(PatientTestEnv.SITE_SUR),
                "chiefComplaintText" to JsonPrimitive("Dolor de cabeza"),
                "complaintSource" to JsonPrimitive("TYPED"),
            ),
            recorded.jsonBody(),
        )
        assertEquals(ComplaintSource.TYPED, vm.state.value.form.complaintSource)
    }

    @Test fun `R22 el POST del formulario no lleva campos de hora`() = runTest {
        val vm = filled()
        env.enqueue(201, PatientTestEnv.preRegistration())
        vm.submit()
        vm.settled()
        val keys = env.server.takeRequest().jsonBody().keys
        assertEquals(setOf("siteId", "chiefComplaintText", "complaintSource"), keys)
    }

    @Test fun `R11 sin sede o con motivo invalido marca los campos y no envia, y el contador sigue la longitud`() = runTest {
        val vm = opened(PatientTestEnv.tenants("""[{"id":"${PatientTestEnv.SITE_SUR}","name":"Sede Sur"}]"""))
        assertEquals(0, vm.state.value.complaintLength)
        assertEquals(CHIEF_COMPLAINT_MAX, 2000)
        vm.updateComplaint("   ")
        assertEquals(3, vm.state.value.complaintLength)
        vm.submit()
        runCurrent()
        assertEquals(
            mapOf(
                PreRegistrationField.Site to UiMessage(R.string.error_site_required),
                PreRegistrationField.ChiefComplaint to UiMessage(R.string.error_required),
            ),
            vm.state.value.fieldErrors,
        )
        vm.selectSite(PatientTestEnv.SITE_SUR)
        vm.updateComplaint("a".repeat(2001))
        assertEquals(2001, vm.state.value.complaintLength)
        assertTrue(vm.state.value.fieldErrors.isEmpty())
        vm.submit()
        runCurrent()
        assertEquals(
            mapOf(PreRegistrationField.ChiefComplaint to UiMessage(R.string.error_max_length, listOf(2000))),
            vm.state.value.fieldErrors,
        )
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R12 un 201 marca el envio como hecho, descarta el motivo y publica el pre-registro de la respuesta`() = runTest {
        val vm = filled()
        val published = async { env.preRegistrationRepository.created.first() }
        runCurrent()
        env.enqueue(201, PatientTestEnv.preRegistration())
        vm.submit()
        val s = vm.state.first { it.created }
        assertEquals("", s.form.chiefComplaintText)
        assertEquals("PENDING", published.await().status)
        assertEquals("Sede Norte", published.await().siteName)
        vm.onCreatedHandled()
        assertFalse(vm.state.value.created)
    }

    @Test fun `R13 un 409 muestra el aviso con Ver mi estado`() = runTest {
        val vm = filled()
        env.enqueue(409, env.problem(409))
        vm.submit()
        val s = vm.settled()
        assertEquals(UiMessage(R.string.error_preregistration_conflict), s.message)
        assertTrue(s.showViewStatus)
        assertFalse(s.created)
    }

    @Test fun `R14 un 422 avisa, quita la sede, vuelve a pedir las sedes y conserva el motivo`() = runTest {
        val vm = filled()
        env.enqueue(422, env.problem(422))
        env.enqueue(200, PatientTestEnv.tenants("""[{"id":"${PatientTestEnv.SITE_SUR}","name":"Sede Sur"}]"""))
        vm.submit()
        val s = vm.state.first { !it.submitting && it.sites == SitesUiState.Loaded(listOf(sur)) }
        assertEquals("POST", env.server.takeRequest().method)
        assertEquals("/api/v1/public/tenants", env.server.takeRequest().path)
        assertEquals(UiMessage(R.string.error_site_unavailable), s.message)
        assertNull(s.form.siteId)
        assertEquals("  Dolor de cabeza  ", s.form.chiefComplaintText)
    }

    @Test fun `R15 un 400 marca sede y motivo o muestra el error general, conservando lo escrito`() = runTest {
        val vm = filled()
        env.enqueue(400, env.problem(400, "siteId", "chiefComplaintText"))
        vm.submit()
        var s = vm.settled()
        assertEquals(setOf(PreRegistrationField.Site, PreRegistrationField.ChiefComplaint), s.fieldErrors.keys)
        assertEquals("  Dolor de cabeza  ", s.form.chiefComplaintText)
        assertEquals(PatientTestEnv.SITE_NORTE, s.form.siteId)
        env.enqueue(400, env.problem(400, "complaintSource"))
        vm.submit()
        s = vm.settled()
        assertEquals(UiMessage(R.string.error_general), s.message)
        assertEquals("  Dolor de cabeza  ", s.form.chiefComplaintText)
    }

    @Test fun `R24 un 429 deshabilita el envio durante Retry-After y 5xx o sin red dan sus mensajes`() = runTest {
        val vm = filled()
        env.enqueue(429, env.problem(429), retryAfter = "15")
        vm.submit()
        assertEquals(UiMessage(R.string.error_too_many_requests, listOf(15)), vm.settled().message)
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        advanceTimeBy(14_000)
        runCurrent()
        assertEquals(2, env.server.requestCount)
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(vm.state.value.canSubmit)
        env.enqueue(503, env.problem(503))
        vm.submit()
        assertEquals(UiMessage(R.string.error_general), vm.settled().message)
        env.enqueue(403, env.problem(403))
        vm.submit()
        assertEquals(UiMessage(R.string.error_general), vm.settled().message)
        env.server.shutdown() // sin respuesta HTTP
        vm.submit()
        assertEquals(UiMessage(R.string.error_network), vm.settled().message)
    }

    @Test fun `R24 un 429 al pedir las sedes deshabilita reintentar 60 s si falta Retry-After`() = runTest {
        env.login()
        env.enqueue(429, env.problem(429))
        val vm = PreRegistrationViewModel(env.preRegistrationRepository, env.sessionManager)
        val s = vm.state.first { it.sites !is SitesUiState.Loading }
        assertEquals(SitesUiState.Error(UiMessage(R.string.error_too_many_requests, listOf(60)), retryEnabled = false), s.sites)
        advanceTimeBy(60_001)
        runCurrent()
        assertEquals(true, (vm.state.value.sites as SitesUiState.Error).retryEnabled)
    }

    @Test fun `R23 un 401 del POST borra la sesion`() = runTest {
        val vm = filled()
        env.enqueue(401, env.problem(401))
        vm.submit()
        vm.settled()
        assertEquals(SessionState.LoggedOut(env.tenant), env.sessionManager.state.value)
    }

    @Test fun `R25 mientras el POST esta en curso muestra carga y una segunda pulsacion no envia otra peticion`() = runTest {
        val vm = filled()
        env.enqueue(201, PatientTestEnv.preRegistration())
        vm.submit()
        assertTrue(vm.state.value.submitting)
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        vm.state.first { it.created }
        assertEquals(2, env.server.requestCount)
    }

    @Test fun `R27 el estado del formulario enmascara el motivo en toString`() = runTest {
        val vm = opened()
        vm.updateComplaint("Dolor toracico intenso")
        assertFalse(vm.state.value.toString().contains("Dolor"))
    }

    @Test fun `R29 el formulario usa dos paneles en Medium y Expanded y una columna en Compact`() {
        assertEquals(PaneMode.Single, preRegistrationPaneMode(WindowWidthSizeClass.Compact))
        assertEquals(PaneMode.Dual, preRegistrationPaneMode(WindowWidthSizeClass.Medium))
        assertEquals(PaneMode.Dual, preRegistrationPaneMode(WindowWidthSizeClass.Expanded))
    }
}
