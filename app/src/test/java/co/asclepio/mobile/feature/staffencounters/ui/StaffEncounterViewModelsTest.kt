package co.asclepio.mobile.feature.staffencounters.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffEncountersTestEnv
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.encounters.EncounterStatus
import co.asclepio.mobile.core.encounters.ui.EncounterListRemote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StaffEncounterViewModelsTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffEncountersTestEnv
    private var detailStatus = "WAITING_TRIAGE"
    private var detailHttp = 200
    private var cancelHttp = 200
    private val reads = mutableListOf<Map<String, String?>>()
    private val writes = mutableListOf<String>()
    private val detailReads = AtomicInteger()
    @Before fun setup() {
        env = StaffEncountersTestEnv(tmp.root)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val (status, body) = when {
                    path == "/api/v1/public/tenants" -> 200 to """[{"id":"${StaffEncountersTestEnv.TENANT}","name":"IPS","sites":[{"id":"${StaffEncountersTestEnv.SITE}","name":"Sede Norte"}]}]"""
                    path == "/api/v1/encounters" -> {
                        val query = listOf("limit", "offset", "siteId", "status").associateWith { request.requestUrl!!.queryParameter(it) }
                        synchronized(reads) { reads += query }
                        val count = if (query["offset"] == "0") 50 else 1
                        200 to "{\"items\":[${List(count) { StaffEncountersTestEnv.summary() }.joinToString(",") }]}"
                    }
                    path.endsWith("/cancel") -> {
                        synchronized(writes) { writes += request.body.readUtf8() }
                        if (cancelHttp == 200) { detailStatus = "CANCELLED"; 200 to StaffEncountersTestEnv.encounter(detailStatus) }
                        else { detailHttp = 503; cancelHttp to env.problem(cancelHttp) }
                    }
                    path == "/api/v1/encounters/${StaffEncountersTestEnv.ENCOUNTER}" -> {
                        detailReads.incrementAndGet()
                        detailHttp to if (detailHttp == 200) StaffEncountersTestEnv.encounter(detailStatus) else env.problem(detailHttp)
                    }
                    else -> 404 to env.problem(404)
                }
                return MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
            }
        }
    }
    @After fun close() = env.close()

    @Test fun `R16 list uses 50 rows preserves filters on return and resets offset when filter changes`() = runTest {
        env.login()
        val vm = StaffEncountersViewModel(env.encounters, env.sites, env.changes)
        vm.setActive(true)
        vm.state.first { it.remote is EncounterListRemote.Ready && !it.refreshing && !it.sites.loading }
        assertEquals("50", reads.last()["limit"])
        vm.nextPage()
        vm.state.first { it.filters.offset == 50 && !it.refreshing }
        assertFalse(vm.state.value.canNext)
        vm.setActive(false); vm.setActive(true)
        vm.state.first { !it.refreshing }
        assertEquals(50, vm.state.value.filters.offset)
        vm.selectStatus(EncounterStatus.IN_CARE)
        vm.state.first { it.filters.offset == 0 && !it.refreshing }
        assertEquals("IN_CARE", reads.last()["status"])
        vm.selectSite(StaffEncountersTestEnv.SITE)
        vm.state.first { !it.refreshing }
        assertEquals(StaffEncountersTestEnv.SITE, reads.last()["siteId"])
        vm.previousPage()
        assertEquals(0, vm.state.value.filters.offset)
    }
    @Test fun `R18 cancellation is role and known current state guarded and returns server timeline without extra clinical request`() = runTest {
        env.login(StaffRole.NURSE)
        val vm = StaffEncounterDetailViewModel(StaffEncountersTestEnv.ENCOUNTER, StaffRole.NURSE, env.encounters, env.changes)
        vm.setActive(true)
        vm.state.first { it.remote is StaffEncounterRemote.Ready && !it.refreshing }
        val encounter = (vm.state.value.remote as StaffEncounterRemote.Ready).encounter
        assertEquals(listOf("90071992547409931234", "90071992547409931235"), encounter.events.map { it.id })
        assertTrue(vm.state.value.canCancel)
        vm.setCancelling(true); vm.setReason(" Motivo sintético "); vm.cancel()
        vm.state.first { it.cancelled && !it.refreshing }
        assertEquals(listOf("{\"reason\":\"Motivo sintético\"}"), writes)
        assertFalse(vm.state.value.canCancel)
        assertEquals("CANCELLED", (vm.state.value.remote as StaffEncounterRemote.Ready).encounter.status)
    }
    @Test fun `R20 conflict followed by failed revalidation preserves reason but never reopens cached cancellation`() = runTest {
        env.login()
        val vm = StaffEncounterDetailViewModel(StaffEncountersTestEnv.ENCOUNTER, StaffRole.RECEPTION, env.encounters, env.changes)
        vm.setActive(true)
        vm.state.first { it.remote is StaffEncounterRemote.Ready && !it.refreshing }
        vm.setCancelling(true); vm.setReason("Borrador literal")
        cancelHttp = 409
        vm.cancel()
        vm.state.first { it.remote is StaffEncounterRemote.Error && !it.refreshing }
        assertEquals("Borrador literal", vm.state.value.reason)
        assertFalse(vm.state.value.canCancel)
        vm.cancel(); runCurrent()
        assertEquals(1, writes.size)
        detailHttp = 200; detailStatus = "CANCELLED"
        vm.refresh()
        vm.state.first { it.remote is StaffEncounterRemote.Ready && !it.refreshing }
        assertFalse(vm.state.value.canCancel)
        assertEquals("Borrador literal", vm.state.value.reason)
    }
    @Test fun `R1 physician and unknown states cannot submit cancellation and 403 hides prior data`() = runTest {
        env.login(StaffRole.PHYSICIAN)
        val vm = StaffEncounterDetailViewModel(StaffEncountersTestEnv.ENCOUNTER, StaffRole.PHYSICIAN, env.encounters, env.changes)
        vm.setActive(true)
        vm.state.first { it.remote is StaffEncounterRemote.Ready && !it.refreshing }
        assertFalse(vm.state.value.canCancel)
        vm.setCancelling(true); vm.setReason("x"); vm.cancel(); runCurrent()
        assertTrue(writes.isEmpty())
        detailHttp = 403
        vm.refresh()
        vm.state.first { !it.refreshing }
        assertEquals(StaffEncounterRemote.Forbidden, vm.state.value.remote)
    }

    @Test fun `R22 throttled detail read blocks manual refresh until retry interval without automatic request`() = runTest {
        env.login()
        detailHttp = 429
        val vm = StaffEncounterDetailViewModel(StaffEncountersTestEnv.ENCOUNTER, StaffRole.RECEPTION, env.encounters, env.changes)
        vm.setActive(true)
        vm.state.first { it.remote is StaffEncounterRemote.Error && !it.refreshing }
        assertFalse((vm.state.value.remote as StaffEncounterRemote.Error).retryEnabled)
        vm.refresh()
        assertFalse(vm.state.value.refreshing)
        advanceTimeBy(60_001); runCurrent()
        assertTrue((vm.state.value.remote as StaffEncounterRemote.Error).retryEnabled)
        assertEquals(1, detailReads.get())
        detailHttp = 200
        vm.refresh()
        vm.state.first { it.remote is StaffEncounterRemote.Ready && !it.refreshing }
        assertEquals(2, detailReads.get())
    }
}
