package co.asclepio.mobile.feature.triage.ui

import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffEncountersTestEnv
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.ENCOUNTER
import co.asclepio.mobile.StaffEncountersTestEnv.Companion.STAFF
import co.asclepio.mobile.core.auth.StaffRole
import co.asclepio.mobile.core.encounters.*
import co.asclepio.mobile.core.network.appJson
import co.asclepio.mobile.core.network.createRetrofit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClinicalCaptureViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffEncountersTestEnv
    private lateinit var clinical: ClinicalCaptureRepository
    @Volatile private var contextStatus = "WAITING_TRIAGE"
    @Volatile private var captureStatus = "WAITING_TRIAGE"
    @Volatile private var contextHttp = 200
    @Volatile private var captureHttp = 200
    @Volatile private var vitalHttp = 201
    @Volatile private var findingsHttp = 201
    @Volatile private var vitalErrors: String? = null
    @Volatile private var findingsErrors: String? = null
    @Volatile private var nextContext: HeldResponse? = null
    @Volatile private var nextCapture: HeldResponse? = null
    @Volatile private var nextVital: HeldResponse? = null
    @Volatile private var nextFindings: HeldResponse? = null
    private val holds = CopyOnWriteArrayList<HeldResponse>()
    private val contextReads = AtomicInteger()
    private val captureReads = AtomicInteger()
    private val vitalWrites = AtomicInteger()
    private val findingsWrites = AtomicInteger()
    private val vitalRecords = ConcurrentHashMap<String, List<String>>()
    private val findingRecords = ConcurrentHashMap<String, List<String>>()
    private val paths = CopyOnWriteArrayList<String>()
    private val valid = RawVitalSigns("18", "98", false, "120", "75", ConsciousnessLevel.A, "36,7")
    private val finding = RawSymptomFinding(SymptomFindingKind.COMPLAINT_NOTE, "  DEV precisión literal  ")

    private class HeldResponse(val code: Int, val body: String) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fun await() {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "No se liberó la respuesta sintética" }
        }
    }
    private fun hold(code: Int, body: String) = HeldResponse(code, body).also { holds += it }
    private fun problem(code: Int, fields: List<String> = emptyList()) = buildJsonObject {
        put("type", "about:blank")
        put("status", code)
        put("title", "Backend privado no presentable")
        put("detail", "Backend privado no presentable")
        if (fields.isNotEmpty()) putJsonArray("errors") {
            fields.forEach { field -> addJsonObject { put("field", field); put("message", "Backend privado no presentable") } }
        }
    }.toString()
    private fun vital(id: String) = """{"id":"88888888-8888-4888-8888-888888888881","encounterId":"$id","respiratoryRate":18,"oxygenSaturation":98,"supplementalOxygen":false,"systolicBp":120,"heartRate":75,"consciousness":"A","temperatureC":36.7,"diastolicBp":null,"glucoseMgDl":null,"recordedAt":"2026-10-27T15:00:00.000Z","recordedBy":{"id":"$STAFF","fullName":"DEV Personal"}}"""
    private fun savedFinding(id: String) = """{"id":"88888888-8888-4888-8888-888888888882","encounterId":"$id","kind":"COMPLAINT_NOTE","description":"DEV precisión literal","recordedAt":"2026-10-27T15:00:00.000Z","recordedBy":{"id":"$STAFF","fullName":"DEV Personal"}}"""
    private fun context(id: String = ENCOUNTER, status: String = contextStatus) = StaffEncountersTestEnv.encounter(status).replace(ENCOUNTER, id)
    private fun capture(id: String = ENCOUNTER, status: String = captureStatus) = """{"encounterId":"$id","status":"$status","chiefComplaintText":"Motivo privado literal","complaintSource":"TYPED","vitalSigns":[${vitalRecords[id].orEmpty().joinToString(",")}],"symptomFindings":[${findingRecords[id].orEmpty().joinToString(",") }]}"""

    @Before fun setup() {
        env = StaffEncountersTestEnv(tmp.root)
        clinical = ClinicalCaptureRepository(createRetrofit(env.server.url("/").toString(), OkHttpClient.Builder().retryOnConnectionFailure(false).build()).create(ClinicalCaptureApi::class.java), env.sessionManager, env.changes)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = requireNotNull(request.requestUrl).encodedPath
                paths += path
                val id = path.removePrefix("/api/v1/encounters/").substringBefore('/')
                val held: HeldResponse?
                val code: Int
                val body: String
                when {
                    request.method == "POST" && path.endsWith("/vital-signs") -> {
                        vitalWrites.incrementAndGet()
                        held = nextVital.also { nextVital = null }
                        code = held?.code ?: vitalHttp
                        body = held?.body ?: if (code == 201) vital(id) else vitalErrors ?: problem(code)
                    }
                    request.method == "POST" && path.endsWith("/symptom-findings") -> {
                        findingsWrites.incrementAndGet()
                        held = nextFindings.also { nextFindings = null }
                        code = held?.code ?: findingsHttp
                        body = held?.body ?: if (code == 201) "{\"items\":[${savedFinding(id)}]}" else findingsErrors ?: problem(code)
                    }
                    path.endsWith("/clinical-capture") -> {
                        captureReads.incrementAndGet()
                        held = nextCapture.also { nextCapture = null }
                        code = held?.code ?: captureHttp
                        body = held?.body ?: if (code == 200) capture(id) else problem(code)
                    }
                    else -> {
                        contextReads.incrementAndGet()
                        held = nextContext.also { nextContext = null }
                        code = held?.code ?: contextHttp
                        body = held?.body ?: if (code == 200) context(id) else problem(code)
                    }
                }
                held?.await()
                if (request.method == "POST" && code == 201) {
                    if (path.endsWith("/vital-signs")) vitalRecords.compute(id) { _, rows -> rows.orEmpty() + body }
                    else findingRecords.compute(id) { _, rows -> rows.orEmpty() + appJson.parseToJsonElement(body).jsonObject.getValue("items").jsonArray.map { it.toString() } }
                }
                return MockResponse().setResponseCode(code).setHeader("Content-Type", if (code >= 400) "application/problem+json" else "application/json").setHeader("Connection", "close").setBody(body)
            }
        }
    }
    @After fun close() {
        holds.forEach { it.release.countDown() }
        env.close()
    }
    private fun vm(id: String = ENCOUNTER, role: StaffRole = StaffRole.NURSE) = ClinicalCaptureViewModel(id, role, env.encounters, clinical, env.changes)
    private suspend fun ready(vm: ClinicalCaptureViewModel) = vm.state.first { it.hasLoaded && !it.checking }
    private fun fill(vm: ClinicalCaptureViewModel) {
        vm.updateVitals(valid)
        vm.updateFinding(vm.state.value.findings.rows.first().key, finding)
    }
    private suspend fun HeldResponse.waitForRequest() {
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
    }

    @Test fun `R6 validacion local impide POST y enfoca primer campo sin rellenar elecciones`() = runTest {
        env.login(StaffRole.NURSE)
        val vm = vm(); vm.setActive(true); ready(vm)
        assertTrue(vm.state.value.canWrite)
        vm.saveVitals(); runCurrent()
        assertEquals(7, vm.state.value.vitals.errors.size)
        assertEquals(VitalField.RespiratoryRate, vm.state.value.vitals.focusField)
        assertTrue(vm.state.value.vitals.focusVersion > 0)
        val vitalFocus = vm.state.value.vitals.focusVersion
        vm.vitalsFocusHandled(vitalFocus - 1)
        assertEquals(VitalField.RespiratoryRate, vm.state.value.vitals.focusField)
        vm.vitalsFocusHandled(vitalFocus)
        assertNull(vm.state.value.vitals.focusField)
        assertEquals(RawVitalSigns(), vm.state.value.vitals.raw)
        vm.saveFindings(); runCurrent()
        assertEquals(FindingField.Kind, vm.state.value.findings.focusField)
        assertEquals(vm.state.value.findings.rows.single().key, vm.state.value.findings.focusRowKey)
        assertTrue(vm.state.value.findings.focusPending)
        val findingFocus = vm.state.value.findings.focusVersion
        vm.findingsFocusHandled(findingFocus - 1)
        assertTrue(vm.state.value.findings.focusPending)
        vm.findingsFocusHandled(findingFocus)
        assertFalse(vm.state.value.findings.focusPending)
        assertNull(vm.state.value.findings.focusRowKey)
        assertNull(vm.state.value.findings.focusField)
        assertEquals(0, vitalWrites.get())
        assertEquals(0, findingsWrites.get())
        vm.setActive(false)
    }

    @Test fun `R10 envios independientes bloquean duplicados y cada exito limpia solo su formulario`() = runTest {
        env.login(StaffRole.NURSE)
        val vm = vm(); vm.setActive(true); ready(vm); fill(vm)
        val first = hold(201, vital(ENCOUNTER)); nextVital = first
        val second = hold(201, "{\"items\":[${savedFinding(ENCOUNTER)}]}"); nextFindings = second
        vm.saveVitals()
        vm.state.first { it.vitals.saving }
        vm.saveVitals(); vm.updateVitals(valid.copy(heartRate = "90"))
        vm.saveFindings()
        vm.state.first { it.findings.saving }
        vm.saveFindings(); vm.addFinding()
        assertEquals(valid, vm.state.value.vitals.raw)
        assertEquals(listOf(finding), vm.state.value.findings.rows.map { it.raw })
        first.release.countDown()
        vm.state.first { it.vitals.success && !it.vitals.saving }
        assertEquals(RawVitalSigns(), vm.state.value.vitals.raw)
        assertTrue(vm.state.value.findings.saving)
        assertEquals(listOf(finding), vm.state.value.findings.rows.map { it.raw })
        second.release.countDown()
        vm.state.first { it.findings.success && !it.findings.saving && !it.checking }
        assertEquals(listOf(RawSymptomFinding()), vm.state.value.findings.rows.map { it.raw })
        assertEquals(1, vitalWrites.get())
        assertEquals(1, findingsWrites.get())
        assertTrue(contextReads.get() >= 2)
        assertTrue(captureReads.get() >= 2)
        val saved = (vm.state.value.capture as ClinicalRead.Ready).data
        assertEquals("Motivo privado literal", saved.chiefComplaintText)
        assertEquals(1, saved.vitalSigns.size)
        assertEquals("DEV precisión literal", saved.symptomFindings.single().description)
        vm.setActive(false)
    }

    @Test fun `R14 errores 400 y 422 mapean campos lote fila y controles sin mensajes backend ni perder borradores`() = runTest {
        env.login(StaffRole.NURSE)
        val vm = vm(); vm.setActive(true); ready(vm); fill(vm); vm.addFinding()
        val secondKey = vm.state.value.findings.rows.last().key
        vm.updateFinding(secondKey, RawSymptomFinding(SymptomFindingKind.FINDING, "DEV hallazgo literal"))
        val rows = vm.state.value.findings.rows.map { it.raw }
        for (code in listOf(400, 422)) {
            vitalHttp = code; findingsHttp = code
            vitalErrors = problem(code, listOf("heartRate", "unknown.private"))
            findingsErrors = problem(code, listOf("items", "items.0", "items.0.kind", "items.1.description", "items.99.description", "unknown.private"))
            vm.saveVitals()
            vm.state.first { !it.vitals.saving && it.vitals.message != null }
            assertEquals(setOf(VitalField.HeartRate), vm.state.value.vitals.errors.keys)
            assertNotNull(vm.state.value.vitals.message)
            assertEquals(VitalField.HeartRate, vm.state.value.vitals.focusField)
            vm.saveFindings()
            vm.state.first { !it.findings.saving && it.findings.message != null }
            val forms = vm.state.value
            assertNotNull(forms.findings.batchError)
            assertNotNull(forms.findings.message)
            assertNotNull(forms.findings.rows[0].rowError)
            assertTrue(forms.findings.rows[0].errors.containsKey(FindingField.Kind))
            assertTrue(forms.findings.rows[1].errors.containsKey(FindingField.Description))
            assertEquals(valid, forms.vitals.raw)
            assertEquals(rows, forms.findings.rows.map { it.raw })
            assertFalse(forms.vitals.message.toString().contains("Backend privado"))
            assertFalse(forms.findings.message.toString().contains("Backend privado"))
        }
        assertEquals(2, vitalWrites.get())
        assertEquals(2, findingsWrites.get())
        vm.setActive(false)
    }

    @Test fun `R13 lote pendiente fija filas y datos sin bloquear el borrador de signos`() = runTest {
        env.login(StaffRole.NURSE)
        val vm = vm(); vm.setActive(true); ready(vm); fill(vm)
        val pending = hold(201, "{\"items\":[${savedFinding(ENCOUNTER)}]}"); nextFindings = pending
        val rows = vm.state.value.findings.rows
        vm.saveFindings(); pending.waitForRequest()
        vm.saveFindings(); vm.removeFinding(rows.single().key); vm.addFinding()
        vm.updateFinding(rows.single().key, finding.copy(description = "Cambio rechazado"))
        vm.updateVitals(valid.copy(heartRate = "81"))
        assertEquals(rows, vm.state.value.findings.rows)
        assertEquals("81", vm.state.value.vitals.raw.heartRate)
        pending.release.countDown()
        vm.state.first { it.findings.success && !it.findings.saving && !it.checking }
        assertEquals(1, findingsWrites.get())
        assertEquals("81", vm.state.value.vitals.raw.heartRate)
        vm.setActive(false)
    }

    @Test fun `R15 conflicto y comprobacion fallida conservan borradores y no reabren escritura cacheada`() = runTest {
        env.login(StaffRole.NURSE)
        val vm = vm(); vm.setActive(true); ready(vm); fill(vm)
        val revision = vm.state.value.readRevision
        vitalHttp = 409; contextHttp = 503; captureHttp = 503
        vm.saveVitals()
        vm.state.first { !it.vitals.saving && it.readRevision > revision && !it.checking }
        assertFalse(vm.state.value.canWrite)
        assertEquals(valid, vm.state.value.vitals.raw)
        assertEquals(finding, vm.state.value.findings.rows.single().raw)
        vm.saveVitals(); vm.saveFindings(); runCurrent()
        assertEquals(1, vitalWrites.get())
        assertEquals(0, findingsWrites.get())
        contextHttp = 200; captureHttp = 200
        contextStatus = "TRIAGED"; captureStatus = "TRIAGED"
        vm.refresh(); ready(vm)
        assertFalse(vm.state.value.canWrite)
        assertEquals(valid, vm.state.value.vitals.raw)
        vm.setActive(false)
    }

    @Test fun `R16 fallo servidor conserva ambos borradores y exige reenvio explicito sin exito simulado`() = runTest {
        env.login(StaffRole.NURSE)
        val vm = vm(); vm.setActive(true); ready(vm); fill(vm)
        vitalHttp = 503
        vm.saveVitals()
        vm.state.first { !it.vitals.saving && it.vitals.message != null }
        assertFalse(vm.state.value.vitals.success)
        assertEquals(valid, vm.state.value.vitals.raw)
        assertEquals(finding, vm.state.value.findings.rows.single().raw)
        runCurrent()
        assertEquals(1, vitalWrites.get())
        vitalHttp = 201
        vm.saveVitals()
        vm.state.first { it.vitals.success && !it.checking }
        assertEquals(2, vitalWrites.get())
        assertEquals(finding, vm.state.value.findings.rows.single().raw)
        vm.setActive(false)
    }

    @Test fun `R15 lectura WAITING anterior al conflicto no satisface la nueva revision ni reabre escrituras`() = runTest {
        env.login(StaffRole.NURSE)
        val vm = vm(); vm.setActive(true); ready(vm); fill(vm)
        val post = hold(409, problem(409)); nextVital = post
        vm.saveVitals(); post.waitForRequest()
        val oldCapture = hold(200, capture()); nextCapture = oldCapture
        vm.refresh()
        oldCapture.waitForRequest()
        val oldRevision = vm.state.value.readRevision
        contextStatus = "TRIAGED"; captureStatus = "TRIAGED"
        post.release.countDown()
        vm.state.first { !it.vitals.saving && it.readRevision > oldRevision }
        assertFalse(vm.state.value.canWrite)
        oldCapture.release.countDown()
        ready(vm)
        val current = vm.state.value
        assertFalse(current.canWrite)
        assertEquals("TRIAGED", (current.context as ClinicalRead.Ready).data.status)
        assertEquals("TRIAGED", (current.capture as ClinicalRead.Ready).data.status)
        assertEquals(current.readRevision, (current.context as ClinicalRead.Ready).revision)
        assertEquals(current.readRevision, (current.capture as ClinicalRead.Ready).revision)
        assertEquals(valid, current.vitals.raw)
        assertEquals(finding, current.findings.rows.single().raw)
        assertTrue(contextReads.get() >= 3)
        assertTrue(captureReads.get() >= 3)
        vm.setActive(false)
    }

    @Test fun `R17 POST sin permiso o inexistente retira ambos paneles y bloquea el otro formulario`() = runTest {
        env.login(StaffRole.NURSE)
        for (code in listOf(403, 404)) {
            val vm = vm(); vm.setActive(true); ready(vm); fill(vm)
            vitalHttp = code
            vm.saveVitals()
            vm.state.first { it.unavailable && !it.vitals.saving }
            val expected = if (code == 403) ClinicalRead.Forbidden else ClinicalRead.NotFound
            assertEquals(expected, vm.state.value.context)
            assertEquals(expected, vm.state.value.capture)
            assertFalse(vm.state.value.canWrite)
            assertEquals(valid, vm.state.value.vitals.raw)
            vm.saveFindings(); runCurrent()
            assertEquals(0, findingsWrites.get())
            vm.setActive(false)
        }
    }

    @Test fun `R19 otra atencion y generacion no heredan borradores ni resultados 201 o 401 antiguos`() = runTest {
        val otherId = "55555555-5555-4555-8555-555555555556"
        for (code in listOf(201, 401)) {
            env.login(StaffRole.NURSE)
            val old = vm(); old.setActive(true); ready(old); fill(old)
            val pending = hold(code, if (code == 201) vital(ENCOUNTER) else problem(code)); nextVital = pending
            val existingJobs = old.viewModelScope.coroutineContext[Job]!!.children.toSet()
            old.saveVitals(); pending.waitForRequest()
            val writes = old.viewModelScope.coroutineContext[Job]!!.children.filterNot { it in existingJobs }.toList()
            env.login(StaffRole.NURSE, "new-token-$code")
            val session = env.sessionManager.state.value
            val current = vm(otherId); current.setActive(true); ready(current)
            assertEquals(RawVitalSigns(), current.state.value.vitals.raw)
            assertEquals(listOf(RawSymptomFinding()), current.state.value.findings.rows.map { it.raw })
            val notices = CopyOnWriteArrayList<StaffDataChange>()
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.changes.changes.collect { notices += it } }
            pending.release.countDown(); writes.joinAll()
            assertEquals(session, env.sessionManager.state.value)
            assertTrue(notices.isEmpty())
            assertFalse(current.state.value.vitals.success)
            assertTrue(current.state.value.canWrite)
            assertEquals(otherId, (current.state.value.context as ClinicalRead.Ready).data.id)
            assertFalse(current.state.value.toString().contains("literal"))
            collector.cancel(); old.setActive(false); current.setActive(false)
        }
    }

    @Test fun `R4 contexto literal pertenece al id abierto y una nueva generacion inicia borradores vacios`() = runTest {
        val otherId = "55555555-5555-4555-8555-555555555556"
        env.login(StaffRole.NURSE)
        val first = vm(); first.setActive(true); ready(first); fill(first)
        assertEquals(ENCOUNTER, (first.state.value.context as ClinicalRead.Ready).data.id)
        assertEquals("Motivo privado literal", (first.state.value.capture as ClinicalRead.Ready).data.chiefComplaintText)
        first.setActive(false)
        val other = vm(otherId); other.setActive(true); ready(other)
        assertEquals(otherId, (other.state.value.context as ClinicalRead.Ready).data.id)
        assertEquals(otherId, (other.state.value.capture as ClinicalRead.Ready).data.encounterId)
        assertEquals(RawVitalSigns(), other.state.value.vitals.raw)
        assertEquals(listOf(RawSymptomFinding()), other.state.value.findings.rows.map { it.raw })
        other.setActive(false)
        env.login(StaffRole.NURSE, "new-context-token")
        val restored = vm(); restored.setActive(true); ready(restored)
        assertEquals(ENCOUNTER, (restored.state.value.capture as ClinicalRead.Ready).data.encounterId)
        assertEquals(RawVitalSigns(), restored.state.value.vitals.raw)
        assertEquals(listOf(RawSymptomFinding()), restored.state.value.findings.rows.map { it.raw })
        restored.setActive(false)
    }

    @Test fun `R12 estado fuera de espera o discrepante mantiene lectura y bloquea ambos POST`() = runTest {
        env.login(StaffRole.NURSE)
        for ((encounterStatus, clinicalStatus) in listOf("TRIAGED" to "TRIAGED", "WAITING_TRIAGE" to "IN_CARE", "IN_CARE" to "WAITING_TRIAGE")) {
            contextStatus = encounterStatus; captureStatus = clinicalStatus
            val vm = vm(); vm.setActive(true); ready(vm); fill(vm)
            assertEquals(encounterStatus, (vm.state.value.context as ClinicalRead.Ready).data.status)
            assertEquals(clinicalStatus, (vm.state.value.capture as ClinicalRead.Ready).data.status)
            assertFalse(vm.state.value.canWrite)
            vm.saveVitals(); vm.saveFindings(); runCurrent()
            assertEquals(0, vitalWrites.get())
            assertEquals(0, findingsWrites.get())
            assertEquals(valid, vm.state.value.vitals.raw)
            assertEquals(finding, vm.state.value.findings.rows.single().raw)
            vm.setActive(false)
        }
    }

    @Test fun `R22 revalidar mismo id y generacion conserva ambos borradores y no repite foco consumido`() = runTest {
        env.login(StaffRole.NURSE)
        val vm = vm(); vm.setActive(true); ready(vm)
        vm.saveVitals(); vm.vitalsFocusHandled(vm.state.value.vitals.focusVersion)
        vm.saveFindings(); vm.findingsFocusHandled(vm.state.value.findings.focusVersion)
        fill(vm)
        val revision = vm.state.value.readRevision
        val rows = vm.state.value.findings.rows
        vm.refresh(); ready(vm)
        assertTrue(vm.state.value.readRevision > revision)
        assertEquals(valid, vm.state.value.vitals.raw)
        assertEquals(rows, vm.state.value.findings.rows)
        assertNull(vm.state.value.vitals.focusField)
        assertFalse(vm.state.value.findings.focusPending)
        assertNull(vm.state.value.findings.focusRowKey)
        assertNull(vm.state.value.findings.focusField)
        assertEquals(0, vitalWrites.get())
        assertEquals(0, findingsWrites.get())
        vm.setActive(false)
    }
}
