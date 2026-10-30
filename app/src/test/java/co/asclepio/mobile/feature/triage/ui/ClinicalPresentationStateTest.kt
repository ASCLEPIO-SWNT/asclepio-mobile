package co.asclepio.mobile.feature.triage.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffEncountersTestEnv
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.network.createRetrofit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

/** Comprueba RAM y aislamiento; la recreación/teclado/scroll real se registra aparte en QA. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClinicalPresentationStateTest {
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffEncountersTestEnv
    private lateinit var clinical: ClinicalCaptureRepository
    @Before fun setup() {
        env = StaffEncountersTestEnv(tmp.root)
        clinical = ClinicalCaptureRepository(createRetrofit(env.server.url("/").toString(), OkHttpClient.Builder().retryOnConnectionFailure(false).build()).create(ClinicalCaptureApi::class.java), env.sessionManager, env.changes)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                val id = path.removePrefix("/api/v1/encounters/").substringBefore('/')
                val body = if (path.endsWith("/clinical-capture")) """{"encounterId":"$id","status":"WAITING_TRIAGE","chiefComplaintText":"Motivo literal","complaintSource":"TYPED","vitalSigns":[],"symptomFindings":[]}"""
                else StaffEncountersTestEnv.encounter().replace(StaffEncountersTestEnv.ENCOUNTER, id)
                return MockResponse().setHeader("Content-Type", "application/json").setHeader("Connection", "close").setBody(body)
            }
        }
    }
    @After fun close() = env.close()
    private suspend fun vm(id: String = StaffEncountersTestEnv.ENCOUNTER) = ClinicalCaptureViewModel(id, StaffRole.NURSE, env.encounters, clinical, env.changes).also {
        it.setActive(true)
        it.state.first { s -> s.hasLoaded && !s.checking }
    }

    @Test fun `R22 presentacion RAM conserva scroll y foco al revalidar sin repetir validacion consumida`() = runBlocking {
        withTimeout(10_000) {
            env.login(StaffRole.NURSE)
            val vm = vm()
            vm.saveVitals()
            vm.vitalsFocusHandled(vm.state.value.vitals.focusVersion)
            val key = vm.state.value.findings.rows.single().key
            vm.rememberFocus(ClinicalFocusTarget.Finding(key, FindingField.Description))
            vm.rememberScroll(2_400)
            val before = vm.state.value.presentation
            vm.refresh(); vm.state.first { !it.checking }
            assertEquals(before, vm.state.value.presentation)
            assertNull(vm.state.value.vitals.focusField)
            assertFalse(vm.state.value.findings.focusPending)
            vm.removeFinding(key)
            assertNull(vm.state.value.presentation.focusTarget)
            vm.rememberFocus(ClinicalFocusTarget.Finding(key, FindingField.Description))
            assertNull(vm.state.value.presentation.focusTarget)
            vm.rememberFocus(ClinicalFocusTarget.Vital(VitalField.HeartRate))
            assertEquals(ClinicalFocusTarget.Vital(VitalField.HeartRate), vm.state.value.presentation.focusTarget)
            vm.setActive(false)
        }
    }

    @Test fun `R19 otra atencion o generacion inicia presentacion vacia y callbacks antiguos no la modifican`() = runBlocking {
        withTimeout(10_000) {
            env.login(StaffRole.NURSE)
            val first = vm()
            first.rememberScroll(1_500)
            first.rememberFocus(ClinicalFocusTarget.Vital(VitalField.TemperatureC))
            val before = first.state.value.presentation
            val other = vm("55555555-5555-4555-8555-555555555556")
            assertEquals(ClinicalPresentationState(), other.state.value.presentation)
            env.login(StaffRole.NURSE, "new-generation-token")
            first.rememberScroll(9_999)
            first.rememberFocus(ClinicalFocusTarget.Vital(VitalField.GlucoseMgDl))
            assertEquals(before, first.state.value.presentation)
            val fresh = vm()
            assertEquals(ClinicalPresentationState(), fresh.state.value.presentation)
            first.setActive(false); other.setActive(false); fresh.setActive(false)
        }
    }
}
