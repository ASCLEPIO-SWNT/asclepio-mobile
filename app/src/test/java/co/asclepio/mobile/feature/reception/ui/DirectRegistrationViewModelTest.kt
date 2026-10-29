package co.asclepio.mobile.feature.reception.ui

import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.R
import co.asclepio.mobile.StaffEncountersTestEnv
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.encounters.StaffDataChange
import co.asclepio.mobile.core.encounters.StaffDocumentType
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.network.appJson
import co.asclepio.mobile.core.ui.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DirectRegistrationViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffEncountersTestEnv
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    @Volatile private var searchBody = "[${StaffEncountersTestEnv.patient()}]"
    @Volatile private var createStatus = 201
    @Volatile private var createBody = StaffEncountersTestEnv.patient()
    @Volatile private var arrivalStatus = 201
    @Volatile private var arrivalBody = StaffEncountersTestEnv.encounter()
    @Volatile private var blockedPath: String? = null
    private var received = CountDownLatch(1)
    private var release = CountDownLatch(1)
    private val clock = Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC)

    @Before fun setup() {
        env = StaffEncountersTestEnv(tmp.root)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val path = request.requestUrl!!.encodedPath
                val (status, body) = when {
                    path == "/api/v1/public/tenants" -> 200 to """[{"id":"${StaffEncountersTestEnv.TENANT}","name":"IPS STAFF","sites":[{"id":"${StaffEncountersTestEnv.SITE}","name":"Sede Norte"}]}]"""
                    path == "/api/v1/patients" && request.method == "GET" -> 200 to searchBody
                    path == "/api/v1/patients" && request.method == "POST" -> createStatus to createBody
                    path == "/api/v1/encounters" && request.method == "POST" -> arrivalStatus to arrivalBody
                    else -> 404 to env.problem(404)
                }
                if (request.method == "POST" && path == blockedPath) {
                    received.countDown(); check(release.await(5, TimeUnit.SECONDS))
                }
                return MockResponse().setResponseCode(status).setBody(body)
                    .setHeader("Content-Type", if (status >= 400) "application/problem+json" else "application/json")
                    .setHeader("Connection", "close")
                    .apply { if (status == 429) setHeader("Retry-After", "3") }
            }
        }
    }
    @After fun close() { release.countDown(); env.close() }
    private suspend fun opened(): DirectRegistrationViewModel {
        env.login()
        return DirectRegistrationViewModel(env.reception, env.encounters, env.sites, clock).also {
            it.state.first { s -> !s.sites.loading }
        }
    }
    private suspend fun selected(): DirectRegistrationViewModel = opened().also {
        it.updateSearch(StaffSearchForm(StaffDocumentType.TI, " DEV-123 ")); it.search()
        it.state.first { s -> s.searchStatus == PatientSearchStatus.Found }
    }
    private suspend fun documented(): DirectRegistrationViewModel {
        searchBody = "[]"
        return opened().also {
            it.updateSearch(StaffSearchForm(StaffDocumentType.TI, " DEV-123 ")); it.search()
            it.state.first { s -> s.searchStatus == PatientSearchStatus.Empty }
            it.startDocumentedPatient()
            it.updatePatient(it.state.value.patientForm!!.copy(fullName = " Menor José Müller ", birthDate = LocalDate.of(2020, 1, 1), sex = Sex.X))
        }
    }
    private fun posts(path: String) = requests.filter { it.method == "POST" && it.requestUrl!!.encodedPath == path }
    private fun body(request: RecordedRequest) = appJson.parseToJsonElement(request.body.clone().readUtf8()).jsonObject
    private fun errors(status: Int, vararg fields: String) = buildJsonObject {
        put("type", "about:blank"); put("title", "NO MOSTRAR"); put("status", status); put("detail", "PRIVADO")
        putJsonArray("errors") { fields.forEach { field -> addJsonObject { put("field", field); put("message", "NO MOSTRAR") } } }
    }.toString()

    @Test fun `R9 busqueda invalida no hace HTTP y valida envia filtros exactos recortados`() = runTest {
        val vm = opened()
        for (form in listOf(StaffSearchForm(), StaffSearchForm(StaffDocumentType.CC, " "), StaffSearchForm(StaffDocumentType.RC, "x".repeat(65)))) {
            vm.updateSearch(form); vm.search(); runCurrent()
            assertTrue(vm.state.value.searchErrors.isNotEmpty())
        }
        assertFalse(requests.any { it.requestUrl!!.encodedPath == "/api/v1/patients" })
        vm.updateSearch(StaffSearchForm(StaffDocumentType.RC, " DEV-123 ")); vm.search()
        vm.state.first { it.searchStatus == PatientSearchStatus.Found }
        val request = requests.single { it.requestUrl!!.encodedPath == "/api/v1/patients" }
        assertEquals(setOf("documentType", "documentNumber"), request.requestUrl!!.queryParameterNames)
        assertEquals("RC", request.requestUrl!!.queryParameter("documentType"))
        assertEquals("DEV-123", request.requestUrl!!.queryParameter("documentNumber"))
    }

    @Test fun `R10 cambiar documento invalida paciente seleccionado y llegada anterior sin reutilizarla`() = runTest {
        val vm = selected()
        assertNotNull(vm.state.value.selected)
        vm.updateArrival(StaffArrivalForm(StaffEncountersTestEnv.SITE, " Motivo reservado "))
        vm.updateSearch(StaffSearchForm(StaffDocumentType.CE, "OTRO"))
        assertNull(vm.state.value.selected)
        assertNull(vm.state.value.patientForm)
        assertEquals(StaffArrivalForm(), vm.state.value.arrival)
        assertEquals(PatientSearchStatus.Idle, vm.state.value.searchStatus)
        vm.registerArrival(); runCurrent()
        assertTrue(posts("/api/v1/encounters").isEmpty())
    }

    @Test fun `R13 busqueda vacia fija documento alta acepta menor y selecciona respuesta servidor`() = runTest {
        val vm = documented()
        assertEquals(StaffPatientMode.Document(StaffDocumentType.TI, "DEV-123"), vm.state.value.patientForm!!.mode)
        vm.createPatient()
        vm.state.first { it.selected != null && !it.creating }
        assertNull(vm.state.value.patientForm)
        assertEquals("DEV José Müller", vm.state.value.selected!!.fullName)
        val json = body(posts("/api/v1/patients").single())
        assertEquals(setOf("sex", "documentType", "documentNumber", "fullName", "birthDate"), json.keys)
        assertEquals("2020-01-01", json["birthDate"]!!.jsonPrimitive.content)
        assertEquals("DEV-123", json["documentNumber"]!!.jsonPrimitive.content)
        assertEquals("Menor José Müller", json["fullName"]!!.jsonPrimitive.content)
    }

    @Test fun `R15 NN edad cero alta y llegada usan respuesta servidor cuerpo exacto y limpian solo exito`() = runTest {
        val nn = JsonObject(appJson.parseToJsonElement(StaffEncountersTestEnv.patient()).jsonObject + mapOf(
            "documentType" to JsonNull, "documentNumber" to JsonNull, "fullName" to JsonNull,
            "birthDate" to JsonNull, "estimatedAgeYears" to JsonPrimitive(0), "sex" to JsonPrimitive("X"), "isNN" to JsonPrimitive(true),
        ))
        createBody = nn.toString()
        val vm = opened()
        vm.startNnPatient()
        vm.updatePatient(vm.state.value.patientForm!!.copy(sex = Sex.X, ageMode = NnAgeMode.EstimatedAge, estimatedAge = "0"))
        vm.createPatient(); vm.state.first { it.selected != null && !it.creating }
        val patientJson = body(posts("/api/v1/patients").single())
        assertEquals(setOf("sex", "estimatedAgeYears"), patientJson.keys)
        assertEquals(0, patientJson["estimatedAgeYears"]!!.jsonPrimitive.int)
        assertTrue(vm.state.value.selected!!.isNN)
        vm.updateArrival(StaffArrivalForm(StaffEncountersTestEnv.SITE, " Motivo literal reservado "))
        vm.registerArrival(); vm.state.first { it.createdEncounterId != null }
        val arrival = body(posts("/api/v1/encounters").single())
        assertEquals(setOf("patientId", "siteId", "chiefComplaintText", "complaintSource"), arrival.keys)
        assertEquals(StaffEncountersTestEnv.PATIENT, arrival["patientId"]!!.jsonPrimitive.content)
        assertEquals("Motivo literal reservado", arrival["chiefComplaintText"]!!.jsonPrimitive.content)
        assertEquals("TYPED", arrival["complaintSource"]!!.jsonPrimitive.content)
        assertEquals(StaffEncountersTestEnv.ENCOUNTER, vm.state.value.createdEncounterId)
        assertNull(vm.state.value.selected)
        assertNull(vm.state.value.patientForm)
        assertEquals(StaffArrivalForm(), vm.state.value.arrival)
        assertFalse(requests.any { it.method == "GET" && it.requestUrl!!.encodedPath == "/api/v1/patients" })
    }

    @Test fun `R13 alta fallida 400 409 422 conserva documento formulario y errores locales`() = runTest {
        val vm = documented()
        val original = vm.state.value.patientForm
        for (status in listOf(400, 409, 422)) {
            createStatus = status
            createBody = if (status == 400) errors(status, "fullName", "documentType", "documentNumber") else errors(status, "fullName")
            vm.createPatient(); vm.state.first { !it.creating && it.patientMessage != null }
            assertEquals(original, vm.state.value.patientForm)
            assertNull(vm.state.value.selected)
            assertFalse(vm.state.value.toString().contains("NO MOSTRAR"))
            when(status) {
                400 -> for (field in listOf(StaffPatientField.FullName, StaffPatientField.DocumentType, StaffPatientField.DocumentNumber)) {
                    assertEquals(UiMessage(R.string.staff_error_field), vm.state.value.patientErrors[field])
                }
                409 -> assertEquals(UiMessage(R.string.staff_patient_conflict), vm.state.value.patientMessage)
                422 -> assertEquals(UiMessage(R.string.staff_birth_future), vm.state.value.patientErrors[StaffPatientField.BirthDate])
            }
        }
        assertEquals(3, posts("/api/v1/patients").size)
    }

    @Test fun `R20 llegada fallida 400 409 422 conserva paciente sede y motivo para correccion explicita`() = runTest {
        val vm = selected()
        val patient = vm.state.value.selected
        val draft = StaffArrivalForm(StaffEncountersTestEnv.SITE, " Motivo literal reservado ")
        vm.updateArrival(draft)
        for (status in listOf(400, 422, 409)) {
            val field = if (status == 422) "siteId" else "chiefComplaintText"
            arrivalStatus = status; arrivalBody = errors(status, field)
            vm.registerArrival(); vm.state.first { !it.registering && it.arrivalMessage != null && !it.sites.loading }
            assertEquals(patient, vm.state.value.selected)
            assertEquals(draft, vm.state.value.arrival)
            assertNull(vm.state.value.createdEncounterId)
            assertEquals(UiMessage(R.string.staff_error_field), vm.state.value.arrivalErrors[if (status == 422) StaffArrivalField.Site else StaffArrivalField.ChiefComplaint])
            assertFalse(vm.state.value.toString().contains("PRIVADO"))
        }
        assertEquals(3, posts("/api/v1/encounters").size)
        assertEquals(2, requests.count { it.requestUrl!!.encodedPath == "/api/v1/public/tenants" })
        assertTrue(vm.state.value.arrivalBlocked)
        vm.updateArrival(draft.copy(chiefComplaintText = "Motivo corregido"))
        vm.registerArrival(); runCurrent()
        assertEquals(3, posts("/api/v1/encounters").size)
        assertTrue(vm.state.value.arrivalBlocked)
        assertEquals(UiMessage(R.string.staff_arrival_conflict), vm.state.value.arrivalMessage)
        vm.search()
        vm.state.first { it.searchStatus == PatientSearchStatus.Found }
        assertEquals(patient, vm.state.value.selected)
        assertTrue(vm.state.value.arrivalBlocked)
        vm.updateArrival(draft); vm.registerArrival(); runCurrent()
        assertEquals(3, posts("/api/v1/encounters").size)

        val other = "44444444-4444-4444-4444-444444444445"
        searchBody = "[${StaffEncountersTestEnv.patient().replace(StaffEncountersTestEnv.PATIENT, other)}]"
        vm.updateSearch(StaffSearchForm(StaffDocumentType.TI, "OTRO")); vm.search()
        vm.state.first { it.selected?.id == other }
        assertFalse(vm.state.value.arrivalBlocked)
    }

    @Test fun `R22 red fallida en alta conserva borrador y permite solo reintento explicito`() = runTest {
        val vm = documented()
        val original = vm.state.value.patientForm
        env.server.shutdown()
        vm.createPatient(); vm.state.first { !it.creating && it.patientMessage != null }
        assertEquals(original, vm.state.value.patientForm)
        assertNull(vm.state.value.selected)
        assertEquals(UiMessage(R.string.error_network), vm.state.value.patientMessage)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `R22 red fallida en llegada conserva seleccion y motivo sin simular exito`() = runTest {
        val vm = selected()
        val patient = vm.state.value.selected
        val draft = StaffArrivalForm(StaffEncountersTestEnv.SITE, " Motivo literal ")
        vm.updateArrival(draft)
        env.server.shutdown()
        vm.registerArrival(); vm.state.first { !it.registering && it.arrivalMessage != null }
        assertEquals(patient, vm.state.value.selected)
        assertEquals(draft, vm.state.value.arrival)
        assertNull(vm.state.value.createdEncounterId)
        assertEquals(UiMessage(R.string.error_network), vm.state.value.arrivalMessage)
    }

    @Test fun `R19 alta y llegada bloquean doble POST mientras su respuesta sigue pendiente`() = runTest {
        val vm = documented()
        blockedPath = "/api/v1/patients"
        vm.createPatient()
        assertTrue(withContext(Dispatchers.IO) { received.await(5, TimeUnit.SECONDS) })
        vm.createPatient(); runCurrent()
        assertEquals(1, posts("/api/v1/patients").size)
        release.countDown(); vm.state.first { !it.creating && it.selected != null }
        blockedPath = "/api/v1/encounters"; received = CountDownLatch(1); release = CountDownLatch(1)
        vm.updateArrival(StaffArrivalForm(StaffEncountersTestEnv.SITE, " Motivo literal "))
        vm.registerArrival()
        assertTrue(withContext(Dispatchers.IO) { received.await(5, TimeUnit.SECONDS) })
        vm.registerArrival(); runCurrent()
        assertEquals(1, posts("/api/v1/encounters").size)
        release.countDown(); vm.state.first { it.createdEncounterId != null }
    }

    @Test fun `R19 429 libera bloqueo tras cambiar modo conservando el nuevo borrador sin HTTP automatico`() = runTest {
        val vm = documented()
        createStatus = 429; createBody = env.problem(429)
        vm.createPatient(); vm.state.first { !it.creating && it.createBlocked }
        vm.startNnPatient()
        vm.updatePatient(vm.state.value.patientForm!!.copy(fullName = " Borrador nuevo ", sex = Sex.X, ageMode = NnAgeMode.EstimatedAge, estimatedAge = "0"))
        val newDraft = vm.state.value.patientForm
        vm.createPatient(); runCurrent()
        assertEquals(1, posts("/api/v1/patients").size)
        advanceTimeBy(2_999); runCurrent()
        assertTrue(vm.state.value.createBlocked)
        advanceTimeBy(1); runCurrent()
        assertFalse(vm.state.value.createBlocked)
        assertEquals(newDraft, vm.state.value.patientForm)
        assertNull(vm.state.value.selected)
        assertEquals(1, posts("/api/v1/patients").size)
    }

    @Test fun `R24 llegada antigua 201 o 401 no navega invalida ni cierra la nueva generacion`() = runTest {
        for (status in listOf(201, 401)) {
            blockedPath = null
            val vm = selected()
            vm.updateArrival(StaffArrivalForm(StaffEncountersTestEnv.SITE, " Motivo privado anterior "))
            val notices = mutableListOf<StaffDataChange>()
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.changes.changes.collect(notices::add) }
            arrivalStatus = status; arrivalBody = if (status == 201) StaffEncountersTestEnv.encounter() else env.problem(status)
            blockedPath = "/api/v1/encounters"; received = CountDownLatch(1); release = CountDownLatch(1)
            vm.registerArrival()
            assertTrue(withContext(Dispatchers.IO) { received.await(5, TimeUnit.SECONDS) })
            env.login(StaffRole.RECEPTION, "new-token")
            val current = env.sessionManager.state.value
            release.countDown()
            vm.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals(current, env.sessionManager.state.value)
            assertNull(vm.state.value.createdEncounterId)
            assertTrue(notices.isEmpty())
            collector.cancel()
        }
    }
}
