package co.asclepio.mobile.core.encounters

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffEncountersTestEnv
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.ENCOUNTER
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.PATIENT
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.PENDING
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.SITE
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.TENANT
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.encounter
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.pending
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.rejected
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.summary
import co.asclepio.mobile.core.auth.*
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.network.*
import co.asclepio.mobile.feature.reception.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class StaffRepositoriesTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffEncountersTestEnv
    @Before fun setup() { env = StaffEncountersTestEnv(tmp.root) }
    @After fun close() = env.close()
    private fun json(request: RecordedRequest) = appJson.parseToJsonElement(request.body.readUtf8()).jsonObject
    private val patientRequest get() = CreateStaffPatientRequest(Sex.F, StaffDocumentType.TI, "DEV-123", "DEV José Müller", "2010-05-17")
    private val direct get() = CreateDirectEncounterRequest(PATIENT, SITE, "  Motivo privado literal  ")
    private data class Operation(val roles: Set<StaffRole>, val response: String, val method: String, val path: String, val call: suspend () -> ApiResult<*>)
    private fun operations(): List<Operation> {
        val reception = setOf(StaffRole.RECEPTION)
        return listOf(
            Operation(reception, "[${pending()}]", "GET", "/api/v1/pre-registrations") { env.reception.pending() },
            Operation(reception, encounter(), "POST", "/api/v1/pre-registrations/$PENDING/confirm") { env.reception.confirm(PENDING) },
            Operation(reception, rejected(), "POST", "/api/v1/pre-registrations/$PENDING/reject") { env.reception.reject(PENDING, "Motivo") },
            Operation(reception, "[${StaffEncountersTestEnv.patient()}]", "GET", "/api/v1/patients") { env.reception.search(StaffDocumentType.TI, "DEV-123") },
            Operation(reception, StaffEncountersTestEnv.patient(), "POST", "/api/v1/patients") { env.reception.createPatient(patientRequest) },
            Operation(reception, encounter(), "POST", "/api/v1/encounters") { env.encounters.createDirect(direct) },
            Operation(StaffRole.entries.toSet(), "{\"items\":[${summary()}]}", "GET", "/api/v1/encounters") { env.encounters.list() },
            Operation(StaffRole.entries.toSet(), encounter(), "GET", "/api/v1/encounters/$ENCOUNTER") { env.encounters.detail(ENCOUNTER) },
            Operation(setOf(StaffRole.RECEPTION, StaffRole.NURSE), encounter("CANCELLED"), "POST", "/api/v1/encounters/$ENCOUNTER/cancel") { env.encounters.cancel(ENCOUNTER, "Motivo") },
        )
    }

    @Test fun `R1 cada operacion usa solo roles STAFF autorizados antes de red`() = runTest {
        for (role in StaffRole.entries) {
            env.login(role)
            for (operation in operations()) {
                val before = env.server.requestCount
                if (role in operation.roles) env.enqueue(200, operation.response)
                val result = operation.call()
                if (role in operation.roles) {
                    assertTrue(result.toString(), result is ApiResult.Success)
                    val request = env.server.takeRequest()
                    assertEquals("Bearer staff-token", request.getHeader("Authorization"))
                    assertEquals(operation.method, request.method)
                    assertEquals(operation.path, request.requestUrl!!.encodedPath)
                    assertEquals(before + 1, env.server.requestCount)
                } else {
                    assertEquals(403, (result as ApiResult.Failure).error.httpStatus)
                    assertEquals(before, env.server.requestCount)
                }
            }
        }
    }

    @Test fun `R1 paciente y sesion no verificada nunca envian operaciones STAFF`() = runTest {
        for (authenticatedPatient in listOf(false, true)) {
            if (authenticatedPatient) env.patientLogin()
            for (operation in operations()) {
                try { operation.call(); fail("No debe abrir scope STAFF") } catch (_: SessionInvalidated) { }
            }
        }
        assertEquals(0, env.server.requestCount)
    }

    @Test fun `R2 sedes pertenecen al tenant STAFF y un error publico no cierra la sesion`() = runTest {
        env.patientLogin(); env.login()
        env.enqueue(200, """[{"id":"11111111-1111-1111-1111-111111111111","name":"IPS anterior","sites":[{"id":"old","name":"Sede anterior"}]},{"id":"$TENANT","name":"IPS STAFF","sites":[{"id":"$SITE","name":"Sede STAFF"}]}]""")
        assertEquals(listOf(SITE), (env.sites.list() as ApiResult.Success).value.map { it.id })
        val request = env.server.takeRequest()
        assertEquals("/api/v1/public/tenants", request.path)
        assertNull(request.getHeader("Authorization"))
        env.enqueue(401, env.problem(401))
        assertEquals(401, (env.sites.list() as ApiResult.Failure).error.httpStatus)
        assertTrue(env.sessionManager.state.value is SessionState.Authenticated)
    }

    @Test fun `R16 filtros solo envian sede estado limit y offset del contrato`() = runTest {
        env.login()
        env.enqueue(200, "{\"items\":[]}")
        env.encounters.list(EncounterFilters(EncounterStatus.WAITING_TRIAGE, SITE, 50, 50))
        val first = env.server.takeRequest().requestUrl!!
        assertEquals(setOf("status", "siteId", "limit", "offset"), first.queryParameterNames)
        assertEquals("50", first.queryParameter("offset"))
        assertEquals("WAITING_TRIAGE", first.queryParameter("status"))
        env.enqueue(200, "{\"items\":[]}"); env.encounters.list()
        assertEquals(setOf("limit", "offset"), env.server.takeRequest().requestUrl!!.queryParameterNames)
        for (site in listOf(null, SITE)) {
            env.enqueue(200, "[]"); env.reception.pending(site)
            val request = env.server.takeRequest()
            assertEquals("/api/v1/pre-registrations", request.requestUrl!!.encodedPath)
            assertEquals(site, request.requestUrl!!.queryParameter("siteId"))
            assertEquals(if (site == null) emptySet<String>() else setOf("siteId"), request.requestUrl!!.queryParameterNames)
        }
    }

    @Test fun `R26 confirmacion llegada directa rechazo y cancelacion omiten horas tenant actor y estado`() = runTest {
        env.login()
        env.enqueue(201, encounter()); env.reception.confirm(PENDING)
        assertEquals(JsonObject(mapOf("identityVerified" to JsonPrimitive(true))), json(env.server.takeRequest()))
        env.enqueue(201, encounter()); env.encounters.createDirect(direct)
        val arrival = json(env.server.takeRequest())
        assertEquals(setOf("patientId", "siteId", "chiefComplaintText", "complaintSource"), arrival.keys)
        assertEquals("TYPED", arrival["complaintSource"]!!.jsonPrimitive.content)
        assertEquals("Motivo privado literal", arrival["chiefComplaintText"]!!.jsonPrimitive.content)
        env.enqueue(200, rejected()); env.reception.reject(PENDING, "  Otro motivo  ")
        assertEquals(JsonObject(mapOf("reason" to JsonPrimitive("Otro motivo"))), json(env.server.takeRequest()))
        env.enqueue(200, encounter("CANCELLED")); env.encounters.cancel(ENCOUNTER, "  Otro motivo  ")
        assertEquals(JsonObject(mapOf("reason" to JsonPrimitive("Otro motivo"))), json(env.server.takeRequest()))
    }

    @Test fun `R26 alta con documento y NN con edad cero usan solo campos publicados`() = runTest {
        env.login()
        env.enqueue(201, StaffEncountersTestEnv.patient()); env.reception.createPatient(patientRequest)
        assertEquals(setOf("sex", "documentType", "documentNumber", "fullName", "birthDate"), json(env.server.takeRequest()).keys)
        env.enqueue(201, StaffEncountersTestEnv.patient()); env.reception.createPatient(CreateStaffPatientRequest(Sex.X, estimatedAgeYears = 0))
        val nn = json(env.server.takeRequest())
        assertEquals(setOf("sex", "estimatedAgeYears"), nn.keys)
        assertEquals(0, nn["estimatedAgeYears"]!!.jsonPrimitive.int)
        env.enqueue(200, "[]"); env.reception.search(StaffDocumentType.RC, "  DEV-123  ")
        val request = env.server.takeRequest().requestUrl!!
        assertEquals(setOf("documentType", "documentNumber"), request.queryParameterNames)
        assertEquals("DEV-123", request.queryParameter("documentNumber"))
        assertEquals("RC", request.queryParameter("documentType"))
    }

    @Test fun `R17 DTO detalle conserva eventos bigint y tipos nuevos sin reordenar ni volcar payload`() = runTest {
        env.login(StaffRole.ADMIN)
        env.enqueue(200, encounter("FUTURE_STATUS"))
        val detail = (env.encounters.detail(ENCOUNTER) as ApiResult.Success).value
        assertEquals(listOf("90071992547409931234", "90071992547409931235"), detail.events.map { it.id })
        assertEquals("FUTURE_EVENT", detail.events.last().type)
        assertFalse(canCancelEncounter(detail.status))
        assertFalse(detail.events.last().toString().contains("no imprimir"))
        assertFalse(detail.toString().contains("Motivo privado literal"))
        assertFalse(detail.patient.toString().contains("José"))
        assertFalse(detail.registeredBy.toString().contains("DEV Personal"))
    }

    @Test fun `R23 escrituras y conflictos notifican invalidacion minima sin datos privados ni replay`() = runTest {
        env.login()
        val notices = mutableListOf<StaffDataChange>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.changes.changes.collect(notices::add) }
        env.enqueue(201, encounter()); env.reception.confirm(PENDING)
        assertEquals(StaffDataChange(env.sessionManager.generation, setOf(StaffResource.Pending, StaffResource.Encounters, StaffResource.Detail), ENCOUNTER), notices.last())
        env.enqueue(200, rejected()); env.reception.reject(PENDING, "Motivo privado")
        assertEquals(setOf(StaffResource.Pending), notices.last().resources)
        env.enqueue(409, env.problem(409)); env.encounters.cancel(ENCOUNTER, "Motivo privado")
        assertEquals(setOf(StaffResource.Pending, StaffResource.Encounters, StaffResource.Detail), notices.last().resources)
        assertTrue(env.changes.changes.replayCache.isEmpty())
        assertFalse(notices.toString().contains("Motivo privado"))
        collector.cancel()
    }

    @Test fun `R24 respuesta antigua no publica invalidacion ni cierra la nueva sesion`() = runTest {
        for (status in listOf(200, 401)) {
            env.login()
            val received = CountDownLatch(1); val release = CountDownLatch(1)
            env.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received.countDown(); check(release.await(3, TimeUnit.SECONDS))
                    return MockResponse().setResponseCode(status).setBody(if (status == 200) encounter("CANCELLED") else env.problem(status))
                }
            }
            val notices = mutableListOf<StaffDataChange>()
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.changes.changes.collect(notices::add) }
            val old = async { runCatching { env.encounters.cancel(ENCOUNTER, "Motivo privado") } }
            assertTrue(withContext(Dispatchers.IO) { received.await(3, TimeUnit.SECONDS) })
            env.login(StaffRole.NURSE, "new-token")
            val newer = env.sessionManager.state.value
            release.countDown()
            assertTrue(old.await().exceptionOrNull() is SessionInvalidated)
            assertEquals(newer, env.sessionManager.state.value)
            assertTrue(notices.isEmpty())
            collector.cancel()
        }
    }

    @Test fun `R21 solo 401 protegido vigente cierra sesion y otros errores preservan token sin retry`() = runTest {
        for (status in listOf(403, 404, 409, 422, 429, 500, 401)) {
            env.login()
            env.enqueue(status, env.problem(status))
            val before = env.server.requestCount
            assertEquals(status, (env.encounters.detail(ENCOUNTER) as ApiResult.Failure).error.httpStatus)
            assertEquals(before + 1, env.server.requestCount)
            assertEquals(status != 401, env.sessionManager.state.value is SessionState.Authenticated)
        }
    }
}
