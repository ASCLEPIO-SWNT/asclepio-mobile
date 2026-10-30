package co.asclepio.mobile.feature.triage.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffEncountersTestEnv
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.encounters.EncounterStatus
import co.asclepio.mobile.core.encounters.ui.EncounterListRemote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TriageTrayViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    @Test fun `R2 tray fixes waiting status uses pages50 retains return filters and never polls`() = runTest {
        val env = StaffEncountersTestEnv(tmp.root)
        val reads = mutableListOf<Map<String, String?>>()
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = if (request.path!!.startsWith("/api/v1/public/tenants")) """[{"id":"${StaffEncountersTestEnv.TENANT}","name":"IPS","sites":[{"id":"${StaffEncountersTestEnv.SITE}","name":"Norte"}]}]"""
                else {
                    synchronized(reads) { reads += listOf("status", "siteId", "limit", "offset").associateWith { request.requestUrl!!.queryParameter(it) } }
                    val count = if (request.requestUrl!!.queryParameter("offset") == "0") 50 else 1
                    "{\"items\":[${List(count) { StaffEncountersTestEnv.summary() }.joinToString(",") }]}"
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        try {
            env.login(StaffRole.NURSE)
            val vm = TriageTrayViewModel(env.encounters, env.sites, env.changes)
            vm.setActive(true)
            vm.state.first { it.remote is EncounterListRemote.Ready && !it.refreshing && !it.sites.loading }
            assertEquals("WAITING_TRIAGE", reads.last()["status"])
            assertEquals("50", reads.last()["limit"])
            vm.selectStatus(EncounterStatus.CANCELLED)
            assertEquals(EncounterStatus.WAITING_TRIAGE, vm.state.value.filters.status)
            vm.nextPage(); vm.state.first { !it.refreshing && it.filters.offset == 50 }
            assertFalse(vm.state.value.canNext)
            vm.setActive(false); vm.setActive(true); vm.state.first { !it.refreshing }
            assertEquals(50, vm.state.value.filters.offset)
            vm.selectSite(StaffEncountersTestEnv.SITE); vm.state.first { !it.refreshing && it.filters.offset == 0 }
            assertEquals(StaffEncountersTestEnv.SITE, reads.last()["siteId"])
            val count = reads.size
            advanceTimeBy(120_000); runCurrent()
            assertEquals(count, reads.size)
        } finally { env.close() }
    }
}
