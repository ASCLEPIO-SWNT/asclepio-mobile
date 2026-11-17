package co.asclepio.mobile.feature.encounters.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.PatientTestEnv.Companion.jsonBody
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.tenants.PublicSiteDto
import co.asclepio.mobile.core.ui.PaneMode
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.core.voice.DictationError
import co.asclepio.mobile.core.voice.DictationUiState
import co.asclepio.mobile.core.voice.FakeOnDeviceRecognizer
import co.asclepio.mobile.core.voice.OnDeviceAvailability
import co.asclepio.mobile.core.voice.OnDeviceRecognizer
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

    private suspend fun TestScope.opened(
        tenants: String = PatientTestEnv.tenants(),
        recognizer: OnDeviceRecognizer? = null,
        beforeListen: () -> Unit = {},
    ): PreRegistrationViewModel {
        env.login()
        env.enqueue(200, tenants)
        val vm = if (recognizer == null) {
            PreRegistrationViewModel(env.preRegistrationRepository, env.sessionManager)
        } else {
            PreRegistrationViewModel(env.preRegistrationRepository, env.sessionManager, recognizer, beforeListen)
        }
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

    // F-08-mobile: dictado en el dispositivo con un reconocedor falso (sin Android ni micrófono).

    @Test fun `R1 al abrir comprueba la disponibilidad sin iniciar escucha`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = opened(recognizer = recognizer)
        assertEquals(listOf("checkAvailability"), recognizer.calls)
        assertTrue(recognizer.listens.isEmpty())
        assertEquals(DictationUiState.Idle, vm.state.value.dictation)
        // Sin reconocedor (F-02) el dictado no está disponible.
        assertEquals(DictationUiState.Unavailable, opened().state.value.dictation)
    }

    @Test fun `R1 con espanol local disponible el dictado queda listo con ese idioma`() = runTest {
        val recognizer = FakeOnDeviceRecognizer(availability = null)
        val vm = opened(recognizer = recognizer)
        assertEquals(DictationUiState.Checking, vm.state.value.dictation)
        recognizer.answerAvailability(OnDeviceAvailability.Available("es-US"))
        assertEquals(DictationUiState.Idle, vm.state.value.dictation)
        assertTrue(recognizer.listens.isEmpty())
    }

    @Test fun `R16 sin reconocimiento local el dictado no esta disponible y se puede enviar por texto`() = runTest {
        val recognizer = FakeOnDeviceRecognizer(availability = OnDeviceAvailability.Unavailable)
        val vm = opened(recognizer = recognizer)
        assertEquals(DictationUiState.Unavailable, vm.state.value.dictation)
        vm.updateComplaint("Dolor de cabeza")
        assertTrue(vm.state.value.canSubmit)
        env.enqueue(201, PatientTestEnv.preRegistration())
        vm.submit()
        vm.state.first { it.created }
        assertEquals(JsonPrimitive("TYPED"), env.server.takeRequest().jsonBody()["complaintSource"])
        assertEquals(listOf("checkAvailability"), recognizer.calls)
    }

    @Test fun `R3 dictar con permiso inicia una escucha con el idioma elegido`() = runTest {
        val recognizer = FakeOnDeviceRecognizer(OnDeviceAvailability.Available("es-US"))
        val vm = opened(recognizer = recognizer)
        vm.startDictation()
        assertEquals(listOf("checkAvailability", "start:es-US"), recognizer.calls)
        assertEquals(DictationUiState.Listening, vm.state.value.dictation)
    }

    @Test fun `R4 permiso denegado muestra aviso conserva el motivo y no escucha`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = opened(recognizer = recognizer)
        vm.updateComplaint("Dolor de cabeza")
        vm.onDictationPermissionDenied()
        val s = vm.state.value
        assertEquals(UiMessage(R.string.dictation_permission_denied), s.dictationMessage)
        assertEquals("Dolor de cabeza", s.form.chiefComplaintText)
        assertEquals(ComplaintSource.TYPED, s.form.complaintSource)
        assertEquals(DictationUiState.Idle, s.dictation)
        assertTrue(s.canSubmit)
        assertTrue(recognizer.listens.isEmpty())
        // Una nueva pulsación con permiso vuelve a escuchar y quita el aviso.
        vm.startDictation()
        assertEquals(1, recognizer.listens.size)
        assertNull(vm.state.value.dictationMessage)
    }

    @Test fun `R5 durante la escucha no se inicia otra y no se puede enviar`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val dictating = opened(recognizer = recognizer)
        dictating.updateComplaint("Dolor de cabeza")
        assertTrue(dictating.state.value.canSubmit)
        dictating.startDictation()
        dictating.startDictation()
        assertEquals(1, recognizer.listens.size)
        assertFalse(dictating.state.value.canSubmit)
        val before = env.server.requestCount
        dictating.submit()
        runCurrent()
        assertEquals(before, env.server.requestCount)
        assertFalse(dictating.state.value.submitting)
    }

    @Test fun `R13 iniciar el dictado detiene la guia antes de escuchar`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = opened(recognizer = recognizer, beforeListen = { recognizer.calls += "guide.stop" })
        assertEquals(listOf("checkAvailability"), recognizer.calls)
        vm.startDictation()
        assertEquals(listOf("checkAvailability", "guide.stop", "start:es-CO"), recognizer.calls)
    }

    /** Formulario con "Dolor de cabeza" ya dictado (origen VOICE_ON_DEVICE). */
    private suspend fun TestScope.dictated(recognizer: FakeOnDeviceRecognizer): PreRegistrationViewModel =
        opened(recognizer = recognizer).apply {
            startDictation()
            recognizer.lastListen.onFinal("Dolor de cabeza")
        }

    @Test fun `R6 el resultado final se anade una sola vez y no se envia`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = opened(recognizer = recognizer)
        vm.updateComplaint("Dolor")
        vm.startDictation()
        val listen = recognizer.lastListen
        listen.onFinal("  de cabeza ")
        listen.onFinal("de cabeza")
        runCurrent()
        val s = vm.state.value
        assertEquals("Dolor de cabeza", s.form.chiefComplaintText)
        assertEquals(ComplaintSource.VOICE_ON_DEVICE, s.form.complaintSource)
        assertEquals(DictationUiState.Idle, s.dictation)
        assertNull(s.dictationMessage)
        assertFalse(s.created)
        assertFalse(s.submitting)
        assertEquals(1, env.server.requestCount) // solo las sedes: dictar no envía
        vm.updateComplaint("Dolor de cabeza fuerte")
        assertEquals("Dolor de cabeza fuerte", vm.state.value.form.chiefComplaintText)
    }

    @Test fun `R7 resultado vacio o error conserva motivo y origen con aviso`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = dictated(recognizer)
        val form = vm.state.value.form
        assertEquals(ComplaintSource.VOICE_ON_DEVICE, form.complaintSource)
        val cases = listOf<Pair<(FakeOnDeviceRecognizer.Listen) -> Unit, Int>>(
            { l: FakeOnDeviceRecognizer.Listen -> l.onFinal("   ") } to R.string.dictation_no_match,
            { l: FakeOnDeviceRecognizer.Listen -> l.onError(DictationError.NoMatch) } to R.string.dictation_no_match,
            { l: FakeOnDeviceRecognizer.Listen -> l.onError(DictationError.Busy) } to R.string.dictation_busy,
            { l: FakeOnDeviceRecognizer.Listen -> l.onError(DictationError.Other) } to R.string.dictation_error,
        )
        for ((finish, message) in cases) {
            vm.startDictation()
            finish(recognizer.lastListen)
            val s = vm.state.value
            assertEquals(form, s.form)
            assertEquals(UiMessage(message), s.dictationMessage)
            assertEquals(DictationUiState.Idle, s.dictation)
            assertTrue(s.canSubmit)
        }
    }

    @Test fun `R7 error de idioma deja el dictado no disponible`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = opened(recognizer = recognizer)
        vm.updateComplaint("Fiebre")
        vm.startDictation()
        recognizer.lastListen.onError(DictationError.LanguageUnavailable)
        val s = vm.state.value
        assertEquals(DictationUiState.Unavailable, s.dictation)
        assertEquals("Fiebre", s.form.chiefComplaintText)
        assertEquals(ComplaintSource.TYPED, s.form.complaintSource)
        assertTrue(s.canSubmit)
        vm.startDictation()
        assertEquals(1, recognizer.listens.size)
    }

    @Test fun `R4 error de permisos del reconocedor muestra el aviso de permiso`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = dictated(recognizer)
        val form = vm.state.value.form
        vm.startDictation()
        recognizer.lastListen.onError(DictationError.Permission)
        val s = vm.state.value
        assertEquals(UiMessage(R.string.dictation_permission_denied), s.dictationMessage)
        assertEquals(form, s.form)
        assertEquals(DictationUiState.Idle, s.dictation)
        assertTrue(s.canSubmit)
    }

    @Test fun `R5 detener pasa a procesando y espera el resultado`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = opened(recognizer = recognizer)
        vm.startDictation()
        vm.stopDictation()
        assertEquals("stop", recognizer.calls.last())
        assertEquals(DictationUiState.Processing, vm.state.value.dictation)
        assertFalse(vm.state.value.canSubmit)
        vm.startDictation()
        assertEquals(1, recognizer.listens.size)
        recognizer.lastListen.onFinal("tos seca")
        assertEquals("tos seca", vm.state.value.form.chiefComplaintText)
        assertEquals(DictationUiState.Idle, vm.state.value.dictation)
    }

    @Test fun `R10 tras cancelar se ignoran resultados y errores tardios`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = opened(recognizer = recognizer)
        vm.updateComplaint("Fiebre")
        vm.startDictation()
        val listen = recognizer.lastListen
        vm.cancelDictation()
        assertEquals("cancel", recognizer.calls.last())
        val cancelled = vm.state.value
        assertEquals(DictationUiState.Idle, cancelled.dictation)
        assertEquals(UiMessage(R.string.dictation_cancelled), cancelled.dictationMessage)
        listen.onFinal("texto tardio")
        listen.onError(DictationError.LanguageUnavailable)
        assertEquals(cancelled, vm.state.value)
        assertEquals("Fiebre", vm.state.value.form.chiefComplaintText)
        assertEquals(ComplaintSource.TYPED, vm.state.value.form.complaintSource)
        // Sin escucha vigente, cancelar no hace nada.
        val calls = recognizer.calls.size
        vm.cancelDictation()
        assertEquals(calls, recognizer.calls.size)
    }

    @Test fun `R10 un resultado de una escucha anterior no se aplica a la nueva`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = opened(recognizer = recognizer)
        vm.startDictation()
        val old = recognizer.lastListen
        vm.cancelDictation()
        vm.startDictation()
        old.onFinal("viejo")
        old.onError(DictationError.Other)
        assertEquals(DictationUiState.Listening, vm.state.value.dictation)
        assertEquals("", vm.state.value.form.chiefComplaintText)
        recognizer.lastListen.onFinal("nuevo")
        assertEquals("nuevo", vm.state.value.form.chiefComplaintText)
    }

    @Test fun `R8 tras el 201 el formulario nuevo vuelve a TYPED`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = dictated(recognizer)
        vm.updateComplaint("Dolor de cabeza y mareo")
        assertEquals(ComplaintSource.VOICE_ON_DEVICE, vm.state.value.form.complaintSource)
        env.enqueue(201, PatientTestEnv.preRegistration())
        vm.submit()
        val s = vm.state.first { it.created }
        assertEquals(
            mapOf(
                "siteId" to JsonPrimitive(PatientTestEnv.SITE_NORTE),
                "chiefComplaintText" to JsonPrimitive("Dolor de cabeza y mareo"),
                "complaintSource" to JsonPrimitive("VOICE_ON_DEVICE"),
            ),
            env.server.takeRequest().jsonBody(),
        )
        assertEquals(PreRegistrationForm(), s.form)
        assertEquals(ComplaintSource.TYPED, s.form.complaintSource)
    }

    @Test fun `R10 al limpiar el ViewModel se libera el reconocedor`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val created = opened(recognizer = recognizer)
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = created as T
        }
        val vm = ViewModelProvider(store, factory)[PreRegistrationViewModel::class.java]
        vm.updateComplaint("Fiebre")
        vm.startDictation()
        val listen = recognizer.lastListen
        store.clear()
        assertEquals("release", recognizer.calls.last())
        val after = vm.state.value
        listen.onFinal("texto tardio")
        assertEquals(after, vm.state.value)
        assertEquals("Fiebre", vm.state.value.form.chiefComplaintText)
    }

    @Test fun `R11 cancelar por rotacion conserva motivo y origen y exige pulsar Dictar de nuevo`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = dictated(recognizer)
        vm.updateComplaint("Dolor de cabeza intenso")
        vm.startDictation()
        // La raíz cancela en el onDispose de la entrada al rotar o cambiar idioma/tema; el VM sobrevive.
        vm.cancelDictation()
        val s = vm.state.value
        assertEquals("Dolor de cabeza intenso", s.form.chiefComplaintText)
        assertEquals(ComplaintSource.VOICE_ON_DEVICE, s.form.complaintSource)
        assertEquals(DictationUiState.Idle, s.dictation)
        assertEquals(2, recognizer.listens.size)
        runCurrent()
        assertEquals(2, recognizer.listens.size) // no vuelve a escuchar solo
        vm.startDictation()
        assertEquals(3, recognizer.listens.size)
    }

    @Test fun `R12 el estado de la pantalla enmascara el motivo dictado en toString`() = runTest {
        val recognizer = FakeOnDeviceRecognizer()
        val vm = opened(recognizer = recognizer)
        vm.startDictation()
        recognizer.lastListen.onFinal("me duele el pecho")
        assertEquals("me duele el pecho", vm.state.value.form.chiefComplaintText)
        assertFalse(vm.state.value.toString().contains("duele"))
        // Sin SavedStateHandle: el texto reconocido no puede acabar en el Bundle.
        val parameters = PreRegistrationViewModel::class.java.constructors.flatMap { it.parameterTypes.toList() }
        assertFalse(SavedStateHandle::class.java in parameters)
    }
}
