package co.asclepio.mobile.feature.reception.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffEncountersTestEnv
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.ui.DashboardRead
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReceptionDashboardViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    @Test fun `R17 visible tick preserves identity rejection draft and scroll while deriving pending count without extra read`() = runTest {
        val env = StaffEncountersTestEnv(tmp.root)
        var pendingReads = 0
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.requestUrl!!.encodedPath) {
                    "/api/v1/public/tenants" -> """[{"id":"${StaffEncountersTestEnv.TENANT}","name":"IPS","sites":[{"id":"${StaffEncountersTestEnv.SITE}","name":"Norte"}]}]"""
                    "/api/v1/pre-registrations" -> { pendingReads++; "[${StaffEncountersTestEnv.pending()}]" }
                    else -> "{\"items\":[]}"
                }
                return MockResponse().setHeader("Content-Type","application/json").setBody(body)
            }
        }
        try {
            env.login()
            val vm = ReceptionViewModel(env.reception, env.sites, env.changes, DashboardDataRepository(env.encounters, env.sites))
            vm.setActive(true)
            vm.state.first { !it.refreshing && it.remote is ReceptionRemote.Ready && it.waitingCount is DashboardRead.Ready }
            vm.setIdentity(StaffEncountersTestEnv.PENDING, true); vm.setRejecting(StaffEncountersTestEnv.PENDING, true); vm.setReason(StaffEncountersTestEnv.PENDING, "Borrador literal"); vm.rememberScroll(2, 12)
            advanceTimeBy(30_000); runCurrent()
            vm.state.first { !it.refreshing && pendingReads == 2 }
            assertTrue(vm.state.value.drafts.getValue(StaffEncountersTestEnv.PENDING).identityChecked)
            assertEquals("Borrador literal",vm.state.value.drafts.getValue(StaffEncountersTestEnv.PENDING).reason)
            assertEquals(2,vm.state.value.scrollIndex); assertEquals(12,vm.state.value.scrollOffset)
            assertEquals(1,(vm.state.value.remote as ReceptionRemote.Ready).items.size)
            assertEquals(2,pendingReads)
            vm.setActive(false); advanceTimeBy(60_000); runCurrent(); assertEquals(2,pendingReads)
        } finally { env.close() }
    }
}
