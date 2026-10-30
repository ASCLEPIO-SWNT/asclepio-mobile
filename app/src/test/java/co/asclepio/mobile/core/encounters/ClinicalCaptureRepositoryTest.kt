package co.asclepio.mobile.core.encounters

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffEncountersTestEnv
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.ENCOUNTER
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.STAFF
import co.asclepio.mobile.core.auth.AuthInterceptor
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.network.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClinicalCaptureRepositoryTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffEncountersTestEnv
    private lateinit var repository: ClinicalCaptureRepository
    private val valid get() = CreateVitalSignsRequest(16, 98, false, 120, 80, ConsciousnessLevel.A, 36.7)
    private val batch get() = CreateSymptomFindingsRequest(listOf(SymptomFindingInput(SymptomFindingKind.SYMPTOM, "  Síntoma literal  "), SymptomFindingInput(SymptomFindingKind.COMPLAINT_NOTE, " Nota literal ")))
    @Before fun setup() {
        env = StaffEncountersTestEnv(tmp.root)
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(AuthInterceptor { env.sessionManager.currentToken() }).build()
        val api = createRetrofit(env.server.url("/").toString(), client).create(ClinicalCaptureApi::class.java)
        repository = ClinicalCaptureRepository(api, env.sessionManager, env.changes)
    }
    @After fun close() = env.close()
    private fun vital(id: String = "88888888-8888-4888-8888-888888888881") = """{"id":"$id","encounterId":"$ENCOUNTER","respiratoryRate":16,"oxygenSaturation":98,"supplementalOxygen":false,"systolicBp":120,"heartRate":80,"consciousness":"A","temperatureC":36.7,"diastolicBp":null,"glucoseMgDl":null,"recordedAt":"2026-10-27T15:00:00.000Z","recordedBy":{"id":"$STAFF","fullName":"DEV Personal"}}"""
    private fun finding(id: String = "88888888-8888-4888-8888-888888888881") = """{"id":"$id","encounterId":"$ENCOUNTER","kind":"SYMPTOM","description":"Texto clínico literal","recordedAt":"2026-10-27T15:00:00.000Z","recordedBy":{"id":"$STAFF","fullName":"DEV Personal"}}"""
    private fun capture() = """{"encounterId":"$ENCOUNTER","status":"WAITING_TRIAGE","chiefComplaintText":"Motivo original literal","complaintSource":"TYPED","vitalSigns":[${vital("88888888-8888-4888-8888-888888888881")},${vital("88888888-8888-4888-8888-888888888882")}],"symptomFindings":[${finding("88888888-8888-4888-8888-888888888881")},${finding("88888888-8888-4888-8888-888888888882")}]}"""

    @Test fun `R1 lectura clinica solo NURSE PHYSICIAN y escrituras solo NURSE antes de HTTP`() = runTest {
        for (role in StaffRole.entries) {
            env.login(role)
            val calls = listOf<Pair<Boolean, suspend () -> ApiResult<*>>>(
                (role in setOf(StaffRole.NURSE, StaffRole.PHYSICIAN)) to { repository.getCapture(ENCOUNTER) },
                (role == StaffRole.NURSE) to { repository.recordVitalSigns(ENCOUNTER, valid) },
                (role == StaffRole.NURSE) to { repository.recordFindings(ENCOUNTER, batch) },
            )
            for ((index, case) in calls.withIndex()) {
                val (allowed, call) = case
                val before = env.server.requestCount
                if (allowed) env.enqueue(200, when(index) { 0 -> capture(); 1 -> vital(); else -> "{\"items\":[${finding()}]}" })
                val result = call()
                if (allowed) {
                    assertTrue(result is ApiResult.Success)
                    assertEquals("Bearer staff-token", env.server.takeRequest().getHeader("Authorization"))
                    assertEquals(before + 1, env.server.requestCount)
                } else {
                    assertEquals(403, (result as ApiResult.Failure).error.httpStatus)
                    assertEquals(before, env.server.requestCount)
                }
            }
        }
    }

    @Test fun `R7 toma publica exactamente siete obligatorios y preserva false omitiendo opcionales ausentes`() = runTest {
        env.login(StaffRole.NURSE)
        env.enqueue(201, vital())
        repository.recordVitalSigns(ENCOUNTER, valid)
        val request = env.server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/encounters/$ENCOUNTER/vital-signs", request.path)
        val body = appJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("respiratoryRate", "oxygenSaturation", "supplementalOxygen", "systolicBp", "heartRate", "consciousness", "temperatureC"), body.keys)
        assertEquals(false, body["supplementalOxygen"]!!.jsonPrimitive.boolean)
        assertFalse(body.values.any { it is JsonNull })
    }

    @Test fun `R9 hallazgos publica solo kind description en orden sin ids locales ni metadatos legales`() = runTest {
        env.login(StaffRole.NURSE)
        env.enqueue(201, "{\"items\":[${finding()}]}")
        repository.recordFindings(ENCOUNTER, batch)
        val request = env.server.takeRequest()
        assertEquals("/api/v1/encounters/$ENCOUNTER/symptom-findings", request.path)
        val body = appJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("items"), body.keys)
        val items = body["items"]!!.jsonArray
        assertEquals(listOf("Síntoma literal", "Nota literal"), items.map { it.jsonObject["description"]!!.jsonPrimitive.content })
        assertTrue(items.all { it.jsonObject.keys == setOf("kind", "description") })
        assertEquals("COMPLAINT_NOTE", items.last().jsonObject["kind"]!!.jsonPrimitive.content)
    }

    @Test fun `R18 capturas exitosas y conflictos invalidan captura detalle y listas de la misma generacion`() = runTest {
        env.login(StaffRole.NURSE)
        val notices = mutableListOf<StaffDataChange>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.changes.changes.collect { notices.add(it) } }
        env.enqueue(201, vital()); repository.recordVitalSigns(ENCOUNTER, valid)
        assertEquals(StaffDataChange(env.sessionManager.generation, setOf(StaffResource.ClinicalCapture, StaffResource.Detail, StaffResource.Encounters), ENCOUNTER), notices.last())
        env.enqueue(409, env.problem(409)); repository.recordFindings(ENCOUNTER, batch)
        assertEquals(2, notices.size)
        assertEquals(notices.first(), notices.last())
        assertTrue(env.changes.changes.replayCache.isEmpty())
        collector.cancel()
    }

    @Test fun `R11 captura conserva orden servidor null y textos literales sin imprimirse en toString`() = runTest {
        env.login(StaffRole.PHYSICIAN)
        env.enqueue(200, capture())
        val result = (repository.getCapture(ENCOUNTER) as ApiResult.Success).value
        assertEquals(listOf("88888888-8888-4888-8888-888888888881", "88888888-8888-4888-8888-888888888882"), result.vitalSigns.map { it.id })
        assertEquals(listOf("88888888-8888-4888-8888-888888888881", "88888888-8888-4888-8888-888888888882"), result.symptomFindings.map { it.id })
        assertNull(result.vitalSigns.first().diastolicBp)
        assertEquals("Motivo original literal", result.chiefComplaintText)
        for (value in listOf(result, result.vitalSigns.first(), result.symptomFindings.first(), valid, batch)) {
            assertFalse(value.toString().contains("literal"))
            assertFalse(value.toString().contains("36.7"))
        }
        assertEquals("/api/v1/encounters/$ENCOUNTER/clinical-capture", env.server.takeRequest().path)
    }
}
