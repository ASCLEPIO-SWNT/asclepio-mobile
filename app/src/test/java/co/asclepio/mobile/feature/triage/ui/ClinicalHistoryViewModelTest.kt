package co.asclepio.mobile.feature.triage.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffEncountersTestEnv
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.network.createRetrofit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClinicalHistoryViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    @Test fun `R1 clinical history forbids reception and admin without invoking endpoint`() = runTest {
        val env = StaffEncountersTestEnv(tmp.root)
        try {
            val repository = ClinicalCaptureRepository(createRetrofit(env.server.url("/").toString()).create(ClinicalCaptureApi::class.java), env.sessionManager, env.changes)
            for (role in listOf(StaffRole.RECEPTION, StaffRole.ADMIN)) {
                env.login(role)
                val vm = ClinicalHistoryViewModel(StaffEncountersTestEnv.ENCOUNTER, role, repository, env.changes)
                vm.setActive(true); runCurrent()
                assertEquals(ClinicalRead.Forbidden, vm.state.value)
            }
            assertEquals(0, env.server.requestCount)
        } finally { env.close() }
    }
    @Test fun `R3 history failure removes previous capture while literal complaint is preserved on successful read`() = runTest {
        val env = StaffEncountersTestEnv(tmp.root)
        try {
            env.login(StaffRole.PHYSICIAN)
            val repository = ClinicalCaptureRepository(createRetrofit(env.server.url("/").toString(), OkHttpClient.Builder().retryOnConnectionFailure(false).build()).create(ClinicalCaptureApi::class.java), env.sessionManager, env.changes)
            env.enqueue(200, """{"encounterId":"${StaffEncountersTestEnv.ENCOUNTER}","status":"WAITING_TRIAGE","chiefComplaintText":"  Literal Áß  ","complaintSource":"TYPED","vitalSigns":[],"symptomFindings":[]}""")
            val vm = ClinicalHistoryViewModel(StaffEncountersTestEnv.ENCOUNTER, StaffRole.PHYSICIAN, repository, env.changes)
            vm.setActive(true)
            val loaded = vm.state.first { it is ClinicalRead.Ready } as ClinicalRead.Ready
            assertEquals("  Literal Áß  ", loaded.data.chiefComplaintText)
            env.enqueue(503, env.problem(503)); vm.refresh()
            assertEquals(ClinicalRead.Loading, vm.state.value)
            vm.state.first { it is ClinicalRead.Error }
            vm.setActive(false)
        } finally { env.close() }
    }
}
