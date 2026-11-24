package co.asclepio.mobile.feature.interview.data

import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.PatientTestEnv.Companion.jsonBody
import co.asclepio.mobile.core.interview.Dimension
import co.asclepio.mobile.core.interview.EndedBy
import co.asclepio.mobile.core.interview.InterviewEngine
import co.asclepio.mobile.core.interview.InterviewSession
import co.asclepio.mobile.core.interview.InterviewState
import co.asclepio.mobile.core.interview.InterviewUnavailableException
import co.asclepio.mobile.core.interview.NextStep
import co.asclepio.mobile.core.interview.RedFlag
import co.asclepio.mobile.core.interview.Turn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Motor del servidor con Retrofit real contra MockWebServer, que responde como el contrato 0.11.0 (T5). */
class ServerInterviewerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv
    private lateinit var interviewer: ServerInterviewer

    @Before fun setUp() {
        env = PatientTestEnv(tmp.root)
        interviewer = ServerInterviewer(env.retrofit.create(SymptomInterviewApi::class.java), env.sessionManager)
    }

    @After fun tearDown() = env.close()

    private suspend fun expectUnavailable() {
        try {
            interviewer.next("Tos", emptyList())
            fail("Se esperaba InterviewUnavailableException")
        } catch (e: InterviewUnavailableException) {
            // R7
        }
    }

    @Test fun `R6 el POST lleva el motivo y los turnos con answer null y el Bearer del paciente`() = runTest {
        env.login()
        env.enqueue(200, """{"kind":"QUESTION","dimension":"LOCATION","question":"¿Dónde te duele?"}""")
        val step = interviewer.next("Me duele", listOf(Turn(Dimension.ONSET, "¿Cuándo empezó?", null)))
        assertEquals(NextStep.Question(Dimension.LOCATION, "¿Dónde te duele?"), step)
        val recorded = env.server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/patient/symptom-interview/next-question", recorded.path)
        assertEquals("Bearer ${PatientTestEnv.TOKEN}", recorded.getHeader("Authorization"))
        assertEquals(
            mapOf(
                "chiefComplaintText" to JsonPrimitive("Me duele"),
                "turns" to JsonArray(
                    listOf(JsonObject(mapOf("dimension" to JsonPrimitive("ONSET"), "question" to JsonPrimitive("¿Cuándo empezó?"), "answer" to JsonNull))),
                ),
            ),
            recorded.jsonBody(),
        )
    }

    @Test fun `R6 DONE y RED_FLAG se traducen a sus pasos`() = runTest {
        env.login()
        env.enqueue(200, """{"kind":"DONE"}""")
        assertEquals(NextStep.Done, interviewer.next("Tos", emptyList()))
        env.enqueue(200, """{"kind":"RED_FLAG","redFlags":["CHEST_PAIN"]}""")
        assertEquals(NextStep.RedFlagHit(listOf(RedFlag.CHEST_PAIN)), interviewer.next("Tos", emptyList()))
        env.enqueue(200, """{"kind":"RED_FLAG","redFlags":[]}""")
        assertEquals(NextStep.RedFlagHit(emptyList()), interviewer.next("Tos", emptyList()))
    }

    @Test fun `R7 429, 503, 500, un corte de red o un cuerpo fuera del contrato dejan el motor no disponible`() = runTest {
        env.login()
        env.enqueue(429, env.problem(429), retryAfter = "30")
        expectUnavailable()
        env.enqueue(503, env.problem(503))
        expectUnavailable()
        env.enqueue(500, env.problem(500))
        expectUnavailable()
        env.enqueueDisconnect()
        expectUnavailable()
        env.enqueue(200, """{"kind":"QUESTION"}""")
        expectUnavailable()
        env.enqueue(200, """{"kind":"OTHER"}""")
        expectUnavailable()
    }

    @Test fun `R7 con 503 a mitad de la entrevista la sesion termina con los turnos respondidos`() = runTest {
        env.login()
        env.enqueue(200, """{"kind":"QUESTION","dimension":"ONSET","question":"¿Cuándo empezó?"}""")
        env.enqueue(503, env.problem(503))
        val session = InterviewSession("Tos seca", interviewer)
        // Tiempo real: el timeout de 20 s de la sesión no debe correr en el reloj virtual mientras espera la red.
        val result = withContext(Dispatchers.IO) {
            assertTrue(session.advance() is InterviewState.Asking)
            (session.answer("Hace dos días") as InterviewState.Ended).result
        }
        assertTrue(result.assistantUnavailable)
        val dto = result.toDto()!!
        assertEquals(InterviewEngine.SERVER, dto.engine)
        assertEquals(EndedBy.PATIENT, dto.endedBy)
        assertEquals(listOf(Turn(Dimension.ONSET, "¿Cuándo empezó?", "Hace dos días")), dto.turns)
    }
}
