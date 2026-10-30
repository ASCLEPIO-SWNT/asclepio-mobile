package co.asclepio.mobile.feature.dashboards.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffEncountersTestEnv
import co.asclepio.mobile.core.auth.*
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.ui.DashboardRead
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardOverviewViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    @Test fun `R14 independent forbidden counter does not replace recent data and stops only its automatic reads`() = runTest {
        val env = StaffEncountersTestEnv(tmp.root)
        val requests = mutableListOf<String>()
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request.path!! }
                val forbidden = request.requestUrl!!.queryParameter("status") == "TRIAGED"
                val body = if (forbidden) env.problem(403) else "{\"items\":[${StaffEncountersTestEnv.summary()}]}"
                return MockResponse().setHeader("Content-Type", "application/json").setResponseCode(if (forbidden) 403 else 200).setBody(body)
            }
        }
        try {
            env.login(StaffRole.PHYSICIAN)
            val vm = DashboardOverviewViewModel(StaffDestination.MEDICINE, StaffRole.PHYSICIAN, DashboardDataRepository(env.encounters, env.sites), env.changes)
            vm.setActive(true)
            vm.state.first { !it.refreshing && it.recent is DashboardRead.Ready && it.counts.values.none { row -> row == DashboardRead.Loading } }
            assertEquals(DashboardRead.Forbidden, vm.state.value.counts[EncounterStatus.TRIAGED])
            val recent = (vm.state.value.recent as DashboardRead.Ready).data.single()
            assertEquals(StaffEncountersTestEnv.ENCOUNTER, recent.id)
            assertFalse(recent.toString().contains("DEV-123"))
            val first = requests.count { "status=TRIAGED" in it }
            advanceTimeBy(30_000); runCurrent()
            vm.state.first { !it.refreshing }
            assertEquals(first, requests.count { "status=TRIAGED" in it })
            vm.setActive(false)
        } finally { env.close() }
    }
    @Test fun `R20 unsupported staff home cannot create overview reads`() = runTest {
        val env = StaffEncountersTestEnv(tmp.root)
        try {
            env.login(StaffRole.RECEPTION)
            val vm = DashboardOverviewViewModel(StaffDestination.ADMINISTRATION, StaffRole.RECEPTION, DashboardDataRepository(env.encounters, env.sites), env.changes)
            vm.setActive(true); runCurrent()
            assertEquals(0, env.server.requestCount)
            assertTrue(vm.state.value.counts.values.all { it == DashboardRead.Forbidden })
            vm.setActive(false)
        } finally { env.close() }
    }
}
