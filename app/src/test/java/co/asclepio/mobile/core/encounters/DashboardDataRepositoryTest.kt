package co.asclepio.mobile.core.encounters

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffEncountersTestEnv
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.SITE
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.TENANT
import co.asclepio.mobile.core.auth.SessionInvalidated
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.appJson
import co.asclepio.mobile.core.network.httpStatus
import co.asclepio.mobile.core.tenants.PublicSiteDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Modifier

/** Preparación externa UI-02-mobile: ejecutar solo tras activar el slice. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardDataRepositoryTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffEncountersTestEnv
    private lateinit var repository: DashboardDataRepository
    @Before fun setup() {
        env = StaffEncountersTestEnv(tmp.root)
        repository = DashboardDataRepository(env.encounters, env.sites)
    }
    @After fun close() = env.close()
    private fun list(count: Int) = "{\"items\":[${List(count) { StaffEncountersTestEnv.summary() }.joinToString(",")}]}"
    private fun query(request: RecordedRequest): Map<String, String?> {
        assertEquals("GET", request.method)
        assertEquals("/api/v1/encounters", request.requestUrl!!.encodedPath)
        assertEquals("Bearer staff-token", request.getHeader("Authorization"))
        return request.requestUrl!!.queryParameterNames.associateWith { request.requestUrl!!.queryParameter(it) }
    }
    private fun fields(type: Class<*>) = type.declaredFields.filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()

    @Test fun `R7 conteo de cero noventa y nueve o cien marca cota solo con pagina completa`() = runTest {
        env.login(StaffRole.PHYSICIAN)
        for (size in listOf(0, 99, 100)) {
            env.enqueue(200, list(size))
            val count = (repository.count(EncounterStatus.WAITING_TRIAGE) as ApiResult.Success).value
            assertEquals(EncounterCount(size, size == 100), count)
            assertEquals(mapOf("status" to "WAITING_TRIAGE", "limit" to "100", "offset" to "0"), query(env.server.takeRequest()))
        }
        assertEquals(3, env.server.requestCount)
    }

    @Test fun `R8 cada conteo realiza un GET y conserva solo dos metadatos sin recorrer paginas`() = runTest {
        env.login(StaffRole.ADMIN)
        env.enqueue(200, list(100))
        val count = (repository.count(EncounterStatus.IN_CARE) as ApiResult.Success).value
        assertEquals(setOf("quantity", "limited"), fields(count.javaClass))
        assertEquals(EncounterCount(100, true), count)
        assertFalse(count.toString().contains("DEV"))
        assertEquals(1, env.server.requestCount)
        query(env.server.takeRequest())
    }

    @Test fun `R3 pendientes se derivan sin HTTP y espera usa la misma sede con limite cien`() = runTest {
        assertEquals(EncounterCount(0, false), pendingCount(0))
        assertEquals(EncounterCount(12, false), pendingCount(12))
        assertEquals(0, env.server.requestCount)
        env.login(StaffRole.RECEPTION)
        env.enqueue(200, list(4))
        assertEquals(EncounterCount(4, false), (repository.count(EncounterStatus.WAITING_TRIAGE, SITE) as ApiResult.Success).value)
        assertEquals(mapOf("status" to "WAITING_TRIAGE", "siteId" to SITE, "limit" to "100", "offset" to "0"), query(env.server.takeRequest()))
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R4 enfermeria deriva offset y cincuenta sin publicar offset de pagina vacia como total`() {
        assertEquals(PageCount(EncounterCount(0, false), false), deriveWaitingPage(0, 0))
        assertEquals(PageCount(EncounterCount(50, true), false), deriveWaitingPage(0, 50))
        assertEquals(PageCount(EncounterCount(100, true), false), deriveWaitingPage(50, 50))
        assertEquals(PageCount(EncounterCount(62, false), false), deriveWaitingPage(50, 12))
        val empty = deriveWaitingPage(50, 0)
        assertTrue(empty.resetOffset)
        assertNull(empty.count)
        assertEquals(PageCount(EncounterCount(12, false), false), deriveWaitingPage(0, 12))
        assertEquals(0, env.server.requestCount)
    }

    @Test fun `R5 medicina usa tres conteos globales y ocho recientes minimos en orden sin documento`() = runTest {
        env.login(StaffRole.PHYSICIAN)
        for (status in listOf(EncounterStatus.WAITING_TRIAGE, EncounterStatus.TRIAGED, EncounterStatus.IN_CARE)) {
            env.enqueue(200, list(2))
            assertEquals(EncounterCount(2, false), (repository.count(status) as ApiResult.Success).value)
            assertEquals(mapOf("status" to status.name, "limit" to "100", "offset" to "0"), query(env.server.takeRequest()))
        }
        val order = listOf(5, 1, 7, 3, 8, 2, 6, 4)
        val items = order.map { number ->
            val source = appJson.parseToJsonElement(StaffEncountersTestEnv.summary()).jsonObject
            JsonObject(source + mapOf(
                "id" to JsonPrimitive("55555555-5555-4555-8555-${number.toString().padStart(12, '0')}"),
                "patient" to JsonObject(source.getValue("patient").jsonObject + mapOf(
                    "fullName" to JsonPrimitive("DEV José Müller $number"),
                    "documentNumber" to JsonPrimitive("DOCUMENTO-PRIVADO-$number"),
                )),
            ))
        }
        env.enqueue(200, buildJsonObject { put("items", JsonArray(items)) }.toString())
        val recent = (repository.recent() as ApiResult.Success).value
        assertEquals(order.map { "DEV José Müller $it" }, recent.map { it.fullName })
        assertEquals(items.map { it.getValue("id").jsonPrimitive.content }, recent.map { it.id })
        assertEquals(setOf("id", "fullName", "siteName", "arrivalAt", "status"), fields(recent.first().javaClass))
        assertEquals("Sede Norte", recent.first().siteName)
        assertEquals("2026-10-27T15:00:00.000Z", recent.first().arrivalAt)
        assertFalse(recent.toString().contains("DOCUMENTO-PRIVADO"))
        assertFalse(recent.toString().contains("DEV José"))
        assertEquals(mapOf("limit" to "8", "offset" to "0"), query(env.server.takeRequest()))
        assertEquals(4, env.server.requestCount)
    }

    @Test fun `R6 sedes del tenant y cada resumen tienen su propia peticion y resultado sin pacientes`() = runTest {
        env.login(StaffRole.ADMIN)
        val second = "22222222-2222-4222-8222-222222222223"
        env.enqueue(200, """[{"id":"$TENANT","name":"IPS","sites":[{"id":"$SITE","name":"Sede Norte"},{"id":"$second","name":"Sede Sur"}]}]""")
        val sites = (repository.listSites() as ApiResult.Success).value
        assertEquals(listOf(SITE, second), sites.map { it.id })
        assertEquals("/api/v1/public/tenants", env.server.takeRequest().path)
        env.enqueue(403, env.problem(403))
        val denied = repository.siteSummary(sites[0]) as ApiResult.Failure
        assertEquals(403, denied.error.httpStatus)
        assertEquals(mapOf("status" to "WAITING_TRIAGE", "siteId" to SITE, "limit" to "100", "offset" to "0"), query(env.server.takeRequest()))
        env.enqueue(200, list(3))
        val allowed = (repository.siteSummary(sites[1]) as ApiResult.Success).value
        assertEquals(SiteEncounterCount(second, "Sede Sur", EncounterCount(3, false)), allowed)
        assertEquals(setOf("siteId", "siteName", "count"), fields(allowed.javaClass))
        assertEquals(mapOf("status" to "WAITING_TRIAGE", "siteId" to second, "limit" to "100", "offset" to "0"), query(env.server.takeRequest()))
        assertEquals(3, env.server.requestCount)
    }

    @Test fun `R20 consultas de dashboard reutilizan scope STAFF y nunca llaman detalle ni clinica`() = runTest {
        for (role in StaffRole.entries) {
            env.login(role)
            env.enqueue(200, list(1)); repository.count(EncounterStatus.WAITING_TRIAGE)
            env.enqueue(200, list(1)); repository.recent()
            repeat(2) { assertEquals("/api/v1/encounters", env.server.takeRequest().requestUrl!!.encodedPath) }
        }
        val before = env.server.requestCount
        env.patientLogin()
        try { repository.count(EncounterStatus.WAITING_TRIAGE); fail("Paciente no puede consultar STAFF") } catch (_: SessionInvalidated) { }
        try { repository.recent(); fail("Paciente no puede consultar STAFF") } catch (_: SessionInvalidated) { }
        assertEquals(before, env.server.requestCount)
    }

    @Test fun `R24 limites cien cincuenta y ocho consumen GET existente del snapshot 040 sin ampliarlo`() {
        val source = listOf(File("../asclepio-api/openapi/asclepio-api.v1.json"), File("../../asclepio-api/openapi/asclepio-api.v1.json"))
            .firstOrNull { it.isFile } ?: error("Falta snapshot readonly de la API")
        val root = Json.parseToJsonElement(source.readText()).jsonObject
        assertEquals("0.4.0", root.getValue("info").jsonObject.getValue("version").jsonPrimitive.content)
        val parameters = root.getValue("paths").jsonObject.getValue("/api/v1/encounters").jsonObject.getValue("get").jsonObject.getValue("parameters").jsonArray
        val limit = parameters.map { it.jsonObject }.first { it.getValue("name").jsonPrimitive.content == "limit" }.getValue("schema").jsonObject
        assertEquals(limit.getValue("maximum").jsonPrimitive.int, DashboardLimits.COUNT)
        assertEquals(limit.getValue("default").jsonPrimitive.int, DashboardLimits.WAITING_PAGE)
        assertEquals(8, DashboardLimits.RECENT)
        assertTrue(DashboardLimits.RECENT in limit.getValue("minimum").jsonPrimitive.int..limit.getValue("maximum").jsonPrimitive.int)
    }
}
