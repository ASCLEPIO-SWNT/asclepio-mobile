package co.asclepio.mobile

import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.PENDING
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.SITE
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.TENANT
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.encounters.DashboardDataRepository
import co.asclepio.mobile.core.encounters.EncounterCount
import co.asclepio.mobile.core.encounters.deriveWaitingPage
import co.asclepio.mobile.core.encounters.ui.EncounterListRemote
import co.asclepio.mobile.core.network.appJson
import co.asclepio.mobile.feature.reception.ui.ReceptionNotice
import co.asclepio.mobile.feature.reception.ui.ReceptionRemote
import co.asclepio.mobile.feature.reception.ui.ReceptionViewModel
import co.asclepio.mobile.feature.triage.ui.TriageTrayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Retención/HTTP reales, sin avanzar el reloj del polling mientras llega I/O. El reloj tiene su suite propia. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardRetentionViewModelTest {
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffEncountersTestEnv
    @Volatile private var pendingStatus = 200
    @Volatile private var pendingIds = listOf(PENDING, SECOND)
    @Volatile private var heldSiteRead: HeldResponse? = null
    @Volatile private var heldReset: HeldResponse? = null
    @Volatile private var heldPost: HeldResponse? = null
    @Volatile private var postStatus: Int? = null
    @Volatile private var retryAfter: String? = null
    private val holds = CopyOnWriteArrayList<HeldResponse>()
    private val siteQueries = CopyOnWriteArrayList<String?>()
    private val trayQueries = CopyOnWriteArrayList<Map<String, String?>>()
    private val writes = CopyOnWriteArrayList<String>()
    private val firstPages = AtomicInteger()

    private class HeldResponse(val site: String?, val body: String, val code: Int = 200) {
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        fun await() {
            received.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "Respuesta sintética no liberada" }
        }
    }
    private fun held(site: String?, body: String, code: Int = 200) = HeldResponse(site, body, code).also { holds += it }
    private suspend fun HeldResponse.waitForRequest() {
        assertTrue(withContext(Dispatchers.IO) { received.await(5, TimeUnit.SECONDS) })
    }
    private fun pending(id: String): JsonObject {
        val second = id == SECOND
        return JsonObject(appJson.parseToJsonElement(StaffEncountersTestEnv.pending()).jsonObject + mapOf(
            "id" to JsonPrimitive(id),
            "siteId" to JsonPrimitive(if (second) SECOND_SITE else SITE),
            "siteName" to JsonPrimitive(if (second) "Sede Sur" else "Sede Norte"),
        ))
    }
    private fun pendingBody(site: String? = null): String = JsonArray(pendingIds.map(::pending).filter {
        site == null || it.getValue("siteId").jsonPrimitive.content == site
    }).toString()
    private fun trayBody(size: Int) = "{\"items\":[${List(size) { StaffEncountersTestEnv.summary() }.joinToString(",")}]}"

    @Before fun setup() {
        env = StaffEncountersTestEnv(tmp.root)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = requireNotNull(request.requestUrl)
                val path = url.encodedPath
                var hold: HeldResponse? = null
                val status: Int
                val body: String
                when {
                    path == "/api/v1/public/tenants" -> {
                        status = 200
                        body = """[{"id":"$TENANT","name":"IPS","sites":[{"id":"$SITE","name":"Sede Norte"},{"id":"$SECOND_SITE","name":"Sede Sur"}]}]"""
                    }
                    request.method == "GET" && path == "/api/v1/pre-registrations" -> {
                        val site = url.queryParameter("siteId")
                        siteQueries += site
                        hold = heldSiteRead?.takeIf { it.site == site }?.also { heldSiteRead = null }
                        status = pendingStatus
                        body = hold?.body ?: if (status == 200) pendingBody(site) else env.problem(status)
                    }
                    request.method == "GET" && path == "/api/v1/encounters" -> {
                        status = 200
                        if (url.queryParameter("limit") == "50") {
                            trayQueries += listOf("status", "siteId", "limit", "offset").associateWith { url.queryParameter(it) }
                            val offset = url.queryParameter("offset").orEmpty().toInt()
                            val count = if (offset == 0 && firstPages.incrementAndGet() == 1) 50 else if (offset == 0) 12 else 0
                            hold = if (offset == 0 && count == 12) heldReset.also { heldReset = null } else null
                            body = hold?.body ?: trayBody(count)
                        } else body = trayBody(0) // Indicador independiente de recepción límite100.
                    }
                    request.method == "POST" -> {
                        writes += path
                        hold = heldPost.also { heldPost = null }
                        status = hold?.code ?: postStatus ?: if (path.endsWith("/confirm")) 201 else 200
                        body = hold?.body ?: if (status >= 400) env.problem(status)
                        else if (path.endsWith("/confirm")) StaffEncountersTestEnv.encounter() else StaffEncountersTestEnv.rejected()
                    }
                    else -> { status = 404; body = env.problem(404) }
                }
                hold?.await()
                return MockResponse().setResponseCode(status).setHeader("Content-Type", if (status >= 400) "application/problem+json" else "application/json").setHeader("Connection", "close").setBody(body).apply {
                    if (status == 429) retryAfter?.let { setHeader("Retry-After", it) }
                }
            }
        }
    }
    @After fun close() {
        holds.forEach { it.release.countDown() }
        env.close()
    }
    private fun scenario(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(10_000, block) }
    private fun reception() = ReceptionViewModel(env.reception, env.sites, env.changes, DashboardDataRepository(env.encounters, env.sites))
    private suspend fun ready(vm: ReceptionViewModel) = vm.state.first { it.remote is ReceptionRemote.Ready && !it.refreshing && !it.sites.loading }
    private fun draft(vm: ReceptionViewModel, id: String, reason: String) {
        vm.setIdentity(id, true); vm.setRejecting(id, true); vm.setReason(id, reason)
    }

    @Test fun `R4 pagina de enfermeria vacia en offset cincuenta vuelve cero antes de publicar cifra`() = scenario {
        env.login(StaffRole.NURSE)
        val vm = TriageTrayViewModel(env.encounters, env.sites, env.changes)
        val published = CopyOnWriteArrayList<Pair<Int, Int>>()
        val collector = launch {
            vm.state.collect { state ->
                val rows = (state.remote as? EncounterListRemote.Ready)?.items
                if (rows != null && !state.refreshing) published += state.filters.offset to rows.size
            }
        }
        vm.setActive(true)
        vm.state.first { it.remote is EncounterListRemote.Ready && !it.refreshing && !it.sites.loading }
        val reset = held(null, trayBody(12)); heldReset = reset
        vm.nextPage(); reset.waitForRequest()
        assertEquals(0, vm.state.value.filters.offset)
        assertTrue(vm.state.value.refreshing)
        assertFalse(published.any { it.first == 50 && it.second == 0 })
        reset.release.countDown()
        val last = vm.state.first { (it.remote as? EncounterListRemote.Ready)?.items?.size == 12 && !it.refreshing }
        assertEquals(EncounterCount(12, false), deriveWaitingPage(last.filters.offset, 12).count)
        assertEquals(listOf("0", "50", "0"), trayQueries.map { it["offset"] })
        assertTrue(trayQueries.all { it["limit"] == "50" && it["status"] == "WAITING_TRIAGE" })
        assertFalse(published.any { it.first == 50 && it.second == 0 })
        assertTrue(writes.isEmpty())
        vm.setActive(false); collector.cancel()
    }

    @Test fun `R18 lectura fallida conserva casilla y motivo pero bloquea acciones hasta recuperar datos vigentes`() = scenario {
        env.login()
        val vm = reception(); vm.setActive(true); ready(vm)
        draft(vm, PENDING, " Motivo RAM literal ")
        val previous = vm.state.value.drafts.getValue(PENDING)
        pendingStatus = 503
        vm.refresh()
        vm.state.first { it.remote is ReceptionRemote.Error && !it.refreshing }
        assertEquals(previous, vm.state.value.drafts[PENDING])
        vm.confirm(PENDING); vm.reject(PENDING)
        assertTrue(writes.isEmpty())
        pendingStatus = 200
        vm.refresh(); ready(vm)
        assertEquals(previous, vm.state.value.drafts[PENDING])
        vm.confirm(PENDING)
        vm.state.first { it.notice is ReceptionNotice.Confirmed }
        assertEquals(listOf("/api/v1/pre-registrations/$PENDING/confirm"), writes)
        vm.setActive(false)
    }

    @Test fun `R19 filtro sede y retorno conservan borrador propio sin aplicarlo a otro preregistro`() = scenario {
        env.login()
        val vm = reception(); vm.setActive(true); ready(vm)
        vm.selectSite(SITE); ready(vm)
        draft(vm, PENDING, " Borrador Norte ")
        vm.selectSite(SECOND_SITE); ready(vm)
        assertEquals(listOf(SECOND), (vm.state.value.remote as ReceptionRemote.Ready).items.map { it.id })
        assertFalse(vm.state.value.drafts[SECOND]?.identityChecked == true)
        assertTrue(vm.state.value.drafts[SECOND]?.reason.orEmpty().isEmpty())
        vm.setRejecting(SECOND, true); vm.setReason(SECOND, " Borrador Sur ")
        vm.selectSite(SITE); ready(vm)
        assertTrue(vm.state.value.drafts[PENDING]?.identityChecked == true)
        assertEquals(" Borrador Norte ", vm.state.value.drafts[PENDING]?.reason)
        vm.setActive(false); vm.setActive(true); ready(vm)
        assertEquals(SITE, vm.state.value.siteId)
        assertEquals(" Borrador Norte ", vm.state.value.drafts[PENDING]?.reason)
        vm.selectSite(SECOND_SITE); ready(vm)
        assertEquals(" Borrador Sur ", vm.state.value.drafts[SECOND]?.reason)
        assertFalse(vm.state.value.drafts[SECOND]?.identityChecked == true)
        assertTrue(writes.isEmpty())
        vm.setActive(false)
    }

    @Test fun `R17 refresco conserva fila existente y una fila nueva no hereda controles de la desaparecida`() = scenario {
        env.login()
        val vm = reception(); vm.setActive(true); ready(vm)
        draft(vm, PENDING, " Borrador retirado "); draft(vm, SECOND, " Borrador conservado ")
        pendingIds = listOf(SECOND, THIRD)
        vm.refresh(); ready(vm)
        assertEquals(listOf(SECOND, THIRD), (vm.state.value.remote as ReceptionRemote.Ready).items.map { it.id })
        assertEquals(" Borrador conservado ", vm.state.value.drafts[SECOND]?.reason)
        assertTrue(vm.state.value.drafts[SECOND]?.identityChecked == true)
        assertFalse(vm.state.value.drafts[THIRD]?.identityChecked == true)
        assertTrue(vm.state.value.drafts[THIRD]?.reason.orEmpty().isEmpty())
        vm.confirm(PENDING); vm.reject(PENDING)
        assertTrue(writes.isEmpty())
        vm.setActive(false)
    }

    @Test fun `R19 respuesta de filtro anterior no sustituye filas ni borrador de la sede actual`() = scenario {
        env.login()
        val vm = reception(); vm.setActive(true); ready(vm)
        val pending = held(SITE, pendingBody(SITE)); heldSiteRead = pending
        val existingJobs = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()
        vm.selectSite(SITE); pending.waitForRequest()
        val oldJobs = vm.viewModelScope.coroutineContext[Job]!!.children.filterNot { it in existingJobs }.toList()
        vm.selectSite(SECOND_SITE); ready(vm)
        draft(vm, SECOND, " Texto de sede vigente ")
        pending.release.countDown(); oldJobs.joinAll()
        assertEquals(SECOND_SITE, vm.state.value.siteId)
        assertEquals(listOf(SECOND), (vm.state.value.remote as ReceptionRemote.Ready).items.map { it.id })
        assertEquals(" Texto de sede vigente ", vm.state.value.drafts[SECOND]?.reason)
        assertEquals(SECOND_SITE, siteQueries.last())
        assertTrue(writes.isEmpty())
        vm.setActive(false)
    }

    @Test fun `R19 POST tardio libera la accion original y conserva motivo sin publicar error en otra sede`() = scenario {
        env.login()
        val vm = reception(); vm.setActive(true); ready(vm)
        vm.selectSite(SITE); ready(vm)
        draft(vm, PENDING, " Borrador pendiente Norte ")
        val pending = held(SITE, env.problem(503), code = 503); heldPost = pending
        val existingJobs = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()
        vm.confirm(PENDING); pending.waitForRequest()
        assertNotNull(vm.state.value.drafts[PENDING]?.action)
        val postJobs = vm.viewModelScope.coroutineContext[Job]!!.children.filterNot { it in existingJobs }.toList()
        vm.selectSite(SECOND_SITE); ready(vm)
        pending.release.countDown(); postJobs.joinAll()
        assertEquals(SECOND_SITE, vm.state.value.siteId)
        assertNull(vm.state.value.message)
        assertNull(vm.state.value.notice)
        assertEquals(listOf(SECOND), (vm.state.value.remote as ReceptionRemote.Ready).items.map { it.id })
        vm.selectSite(SITE); ready(vm)
        val row = vm.state.value.drafts.getValue(PENDING)
        assertNull(row.action)
        assertEquals(" Borrador pendiente Norte ", row.reason)
        assertTrue(row.identityChecked)
        assertNotNull(row.message)
        assertEquals(1, writes.size)
        vm.setActive(false)
    }

    @Test fun `R19 RetryAfter termina aunque cambie sede sin reenviar POST ni perder borrador original`() = scenario {
        env.login()
        val vm = reception(); vm.setActive(true); ready(vm)
        vm.selectSite(SITE); ready(vm)
        draft(vm, PENDING, " Motivo retenido durante bloqueo ")
        postStatus = 429; retryAfter = "3"
        vm.confirm(PENDING)
        vm.state.first { it.drafts[PENDING]?.blocked == true && it.drafts[PENDING]?.action == null }
        vm.selectSite(SECOND_SITE); ready(vm)
        main.dispatcher.scheduler.advanceTimeBy(3_000)
        main.dispatcher.scheduler.runCurrent()
        assertEquals(1, writes.size)
        vm.selectSite(SITE); ready(vm)
        val row = vm.state.value.drafts.getValue(PENDING)
        assertFalse(row.blocked)
        assertNull(row.action)
        assertEquals(" Motivo retenido durante bloqueo ", row.reason)
        assertTrue(row.identityChecked)
        assertEquals(1, writes.size)
        vm.setActive(false)
    }

    companion object {
        private const val SECOND_SITE = "22222222-2222-4222-8222-222222222223"
        private const val SECOND = "66666666-6666-4666-8666-666666666667"
        private const val THIRD = "66666666-6666-4666-8666-666666666668"
    }
}
