package co.asclepio.mobile.feature.encounters.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.PatientTestEnv.Companion.jsonBody
import co.asclepio.mobile.core.interview.Dimension
import co.asclepio.mobile.core.interview.EndedBy
import co.asclepio.mobile.core.interview.InterviewEngine
import co.asclepio.mobile.core.interview.RedFlag
import co.asclepio.mobile.core.interview.SymptomInterviewDto
import co.asclepio.mobile.core.interview.Turn
import co.asclepio.mobile.core.interview.UrgentSubmission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Prerregistro con la entrevista (T10): `interview` en el POST real (MockWebServer, contrato 0.11.0) y envío de alarma. */
class PreRegistrationInterviewTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv

    @Before fun setUp() {
        env = PatientTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    private val interview = SymptomInterviewDto(
        InterviewEngine.ON_DEVICE, "gemma-test",
        listOf(Turn(Dimension.ONSET, "¿Cuándo empezó?", "Ayer"), Turn(Dimension.LOCATION, "¿Dónde?", null)),
        emptyList(), EndedBy.PATIENT,
    )
    private val urgent = interview.copy(redFlags = listOf(RedFlag.CHEST_PAIN), endedBy = EndedBy.RED_FLAG)

    private suspend fun TestScope.opened(): PreRegistrationViewModel {
        env.login()
        env.enqueue(200, PatientTestEnv.tenants())
        val vm = PreRegistrationViewModel(env.preRegistrationRepository, env.sessionManager)
        vm.state.first { it.sites !is SitesUiState.Loading }
        env.server.takeRequest()
        vm.updateComplaint("  Me duele la cabeza  ")
        return vm
    }

    private fun expectedInterviewJson(dto: SymptomInterviewDto) = Json.parseToJsonElement(
        """{"engine":"${dto.engine}","modelId":"${dto.modelId}","turns":[{"dimension":"ONSET","question":"¿Cuándo empezó?","answer":"Ayer"},""" +
            """{"dimension":"LOCATION","question":"¿Dónde?","answer":null}],"redFlags":${dto.redFlags.map { "\"$it\"" }},"endedBy":"${dto.endedBy}"}""",
    )

    @Test fun `R6 Continuar con el formulario valido abre la entrevista sin enviar nada`() = runTest {
        val vm = opened()
        vm.continueToAssistant()
        assertEquals(1L, vm.state.value.assistantAttempt)
        assertEquals("Me duele la cabeza", vm.assistantComplaint)
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R6 Continuar con el motivo vacio no abre la entrevista`() = runTest {
        val vm = opened()
        vm.updateComplaint("   ")
        vm.continueToAssistant()
        assertNull(vm.state.value.assistantAttempt)
        assertTrue(vm.state.value.fieldErrors.containsKey(PreRegistrationField.ChiefComplaint))
    }

    @Test fun `R14 confirmar el resumen envia el prerregistro con interview segun el contrato`() = runTest {
        val vm = opened()
        vm.continueToAssistant()
        env.enqueue(201, PatientTestEnv.preRegistration())
        vm.submitWithInterview(interview)
        vm.state.first { it.created }
        val body = env.server.takeRequest().jsonBody()
        assertEquals(setOf("siteId", "chiefComplaintText", "complaintSource", "interview"), body.keys)
        assertEquals(JsonPrimitive("Me duele la cabeza"), body["chiefComplaintText"])
        assertEquals(expectedInterviewJson(interview), body.getValue("interview").jsonObject)
    }

    @Test fun `R7 omitir el asistente envia como en 0_10_0 sin interview`() = runTest {
        val vm = opened()
        vm.continueToAssistant()
        env.enqueue(201, PatientTestEnv.preRegistration())
        vm.skipAssistant()
        vm.state.first { it.created }
        assertNull(vm.state.value.assistantAttempt)
        assertEquals(setOf("siteId", "chiefComplaintText", "complaintSource"), env.server.takeRequest().jsonBody().keys)
    }

    @Test fun `R12 la alarma envia de inmediato con endedBy RED_FLAG y las senales`() = runTest {
        val vm = opened()
        vm.continueToAssistant()
        env.enqueue(201, PatientTestEnv.preRegistration())
        vm.sendUrgent(urgent)
        assertEquals(UrgentSubmission.Sent, vm.state.value.urgent)
        assertEquals(expectedInterviewJson(urgent), env.server.takeRequest().jsonBody().getValue("interview").jsonObject)
    }

    @Test fun `R13 si el envio de alarma falla se reintenta con backoff hasta lograrlo`() = runTest {
        val vm = opened()
        vm.continueToAssistant()
        env.enqueue(503, env.problem(503))
        env.enqueueDisconnect()
        env.enqueue(201, PatientTestEnv.preRegistration())
        val seen = mutableListOf<UrgentSubmission?>()
        val watcher = launch { vm.state.collect { seen += it.urgent } }
        vm.sendUrgent(urgent)
        watcher.cancel()
        assertEquals(UrgentSubmission.Sent, vm.state.value.urgent)
        assertTrue(seen.toString(), UrgentSubmission.Retrying(1) in seen && UrgentSubmission.Retrying(2) in seen)
        assertEquals(4, env.server.requestCount)
    }

    @Test fun `R13 al salir de la pantalla de alarma se dejan de reintentar los envios`() = runTest {
        val vm = opened()
        vm.continueToAssistant()
        repeat(3) { env.enqueue(503, env.problem(503)) }
        val job = launch { vm.sendUrgent(urgent) }
        vm.state.first { it.urgent is UrgentSubmission.Retrying }
        job.cancel()
        job.join()
        val before = env.server.requestCount
        Thread.sleep(200)
        assertEquals(before, env.server.requestCount)
        assertFalse(vm.state.value.urgent == UrgentSubmission.Sent)
    }

    @Test fun `R13 un 409 no se reintenta y se informa`() = runTest {
        val vm = opened()
        vm.continueToAssistant()
        env.enqueue(409, env.problem(409))
        vm.sendUrgent(urgent)
        assertTrue(vm.state.value.urgent is UrgentSubmission.Failed)
        assertEquals(2, env.server.requestCount)
    }
}
