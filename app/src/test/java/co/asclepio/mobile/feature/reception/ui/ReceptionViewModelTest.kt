package co.asclepio.mobile.feature.reception.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffEncountersTestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReceptionViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffEncountersTestEnv
    private var pendingStatus = 200
    private var pendingBody = "[${StaffEncountersTestEnv.pending()}]"
    private val writes = mutableListOf<String>()
    private val siteQueries = CopyOnWriteArrayList<String?>()
    @Before fun setup() {
        env = StaffEncountersTestEnv(tmp.root)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val (status, body) = when {
                    path == "/api/v1/public/tenants" -> 200 to """[{"id":"${StaffEncountersTestEnv.TENANT}","name":"IPS","sites":[{"id":"${StaffEncountersTestEnv.SITE}","name":"Sede Norte"}]}]"""
                    request.method == "GET" && path == "/api/v1/pre-registrations" -> {
                        siteQueries.add(request.requestUrl!!.queryParameter("siteId"))
                        pendingStatus to pendingBody
                    }
                    path.endsWith("/confirm") -> { synchronized(writes) { writes += request.body.readUtf8() }; pendingBody = "[]"; 201 to StaffEncountersTestEnv.encounter() }
                    path.endsWith("/reject") -> { synchronized(writes) { writes += request.body.readUtf8() }; pendingBody = "[]"; 200 to StaffEncountersTestEnv.rejected() }
                    else -> 404 to env.problem(404)
                }
                return MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
            }
        }
    }
    @After fun close() = env.close()

    private suspend fun ready(): ReceptionViewModel {
        env.login()
        return ReceptionViewModel(env.reception, env.sites, env.changes).also { vm ->
            vm.setActive(true)
            vm.state.first { it.remote is ReceptionRemote.Ready && !it.refreshing && !it.sites.loading }
        }
    }

    @Test fun `R3 tray filters by selected site and reads only on entry filter or explicit refresh without polling`() = runTest {
        val vm = ready()
        assertEquals(listOf<String?>(null), siteQueries)
        vm.selectSite(StaffEncountersTestEnv.SITE)
        vm.state.first { !it.refreshing }
        assertEquals(StaffEncountersTestEnv.SITE, siteQueries.last())
        vm.refresh(); vm.state.first { !it.refreshing }
        val count = siteQueries.size
        advanceTimeBy(31_000); runCurrent()
        assertEquals(count, siteQueries.size)
    }

    @Test fun `R4 tray preserves literal patient complaint source and reference times as server data`() = runTest {
        val vm = ready()
        val row = (vm.state.value.remote as ReceptionRemote.Ready).items.single()
        assertEquals("DEV José Müller", row.patient.fullName)
        assertEquals("DEV-123", row.patient.documentNumber)
        assertEquals("Motivo privado literal", row.chiefComplaintText)
        assertEquals("VOICE_ON_DEVICE", row.complaintSource)
        assertEquals("2026-10-27T14:30:00.000Z", row.preRegisteredAt)
        assertEquals("2026-10-27T20:30:00.000Z", row.expiresAt)
    }

    @Test fun `R6 confirmed arrival notice uses encounter and legal timestamp returned by server`() = runTest {
        val vm = ready()
        vm.setIdentity(StaffEncountersTestEnv.PENDING, true)
        vm.confirm(StaffEncountersTestEnv.PENDING)
        vm.state.first { it.notice is ReceptionNotice.Confirmed }
        val notice = vm.state.value.notice as ReceptionNotice.Confirmed
        assertEquals(StaffEncountersTestEnv.ENCOUNTER, notice.encounterId)
        assertEquals("2026-10-27T15:00:00.000Z", notice.arrivalAt)
        assertFalse(notice.arrivalAt == "2026-10-27T14:30:00.000Z")
    }

    @Test fun `R5 account verification never prechecks confirmation and only explicit true submits`() = runTest {
        env.login()
        val vm = ReceptionViewModel(env.reception, env.sites, env.changes)
        vm.setActive(true)
        vm.state.first { it.remote is ReceptionRemote.Ready && !it.refreshing }
        assertFalse(vm.state.value.drafts[StaffEncountersTestEnv.PENDING]?.identityChecked == true)
        vm.confirm(StaffEncountersTestEnv.PENDING)
        runCurrent()
        assertTrue(writes.isEmpty())
        vm.setIdentity(StaffEncountersTestEnv.PENDING, true)
        vm.confirm(StaffEncountersTestEnv.PENDING)
        vm.state.first { it.notice is ReceptionNotice.Confirmed }
        assertEquals(listOf("{\"identityVerified\":true}"), writes)
    }
    @Test fun `R8 successful refresh retains per-id drafts while failed refresh hides remote and blocks writes`() = runTest {
        env.login()
        val vm = ReceptionViewModel(env.reception, env.sites, env.changes)
        vm.setActive(true)
        vm.state.first { it.remote is ReceptionRemote.Ready && !it.refreshing }
        vm.setIdentity(StaffEncountersTestEnv.PENDING, true)
        vm.setRejecting(StaffEncountersTestEnv.PENDING, true)
        vm.setReason(StaffEncountersTestEnv.PENDING, " Literal draft ")
        vm.refresh()
        vm.state.first { !it.refreshing }
        assertEquals(" Literal draft ", vm.state.value.drafts[StaffEncountersTestEnv.PENDING]?.reason)
        pendingStatus = 503; pendingBody = env.problem(503)
        vm.refresh()
        vm.state.first { it.remote is ReceptionRemote.Error }
        vm.confirm(StaffEncountersTestEnv.PENDING)
        vm.reject(StaffEncountersTestEnv.PENDING)
        runCurrent()
        assertTrue(writes.isEmpty())
        assertEquals(" Literal draft ", vm.state.value.drafts[StaffEncountersTestEnv.PENDING]?.reason)
        pendingStatus = 200; pendingBody = "[${StaffEncountersTestEnv.pending()}]"
        vm.refresh()
        vm.state.first { it.remote is ReceptionRemote.Ready && !it.refreshing }
        assertTrue(vm.state.value.drafts[StaffEncountersTestEnv.PENDING]!!.identityChecked)
        pendingBody = "[]"
        vm.refresh()
        vm.state.first { it.remote is ReceptionRemote.Ready && !it.refreshing }
        assertTrue(vm.state.value.drafts.isEmpty())
    }
    @Test fun `R7 reject reason validation and cancel editing do not write until explicit valid submission`() = runTest {
        env.login()
        val vm = ReceptionViewModel(env.reception, env.sites, env.changes)
        vm.setActive(true)
        vm.state.first { it.remote is ReceptionRemote.Ready && !it.refreshing }
        vm.setRejecting(StaffEncountersTestEnv.PENDING, true)
        for (reason in listOf(" ", "x".repeat(501))) {
            vm.setReason(StaffEncountersTestEnv.PENDING, reason)
            vm.reject(StaffEncountersTestEnv.PENDING)
            assertNotNull(vm.state.value.drafts[StaffEncountersTestEnv.PENDING]?.reasonError)
        }
        vm.setRejecting(StaffEncountersTestEnv.PENDING, false)
        assertTrue(writes.isEmpty())
        vm.setRejecting(StaffEncountersTestEnv.PENDING, true)
        vm.setReason(StaffEncountersTestEnv.PENDING, " Aclaración literal ")
        vm.reject(StaffEncountersTestEnv.PENDING)
        vm.state.first { it.notice == ReceptionNotice.Rejected }
        assertEquals(listOf("{\"reason\":\"Aclaración literal\"}"), writes)
    }
}
