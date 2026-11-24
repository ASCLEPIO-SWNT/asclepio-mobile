package co.asclepio.mobile.feature.interview

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.asclepio.mobile.core.interview.Dimension
import co.asclepio.mobile.core.interview.EndedBy
import co.asclepio.mobile.core.interview.InterviewEngine
import co.asclepio.mobile.core.interview.InterviewSession
import co.asclepio.mobile.core.interview.InterviewState
import co.asclepio.mobile.core.interview.NextStep
import co.asclepio.mobile.core.interview.OutputValidator
import co.asclepio.mobile.core.interview.SymptomInterviewer
import co.asclepio.mobile.core.interview.Turn
import co.asclepio.mobile.core.interview.Validation
import co.asclepio.mobile.feature.interview.model.BUILD_MODEL
import co.asclepio.mobile.feature.interview.model.ModelStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Evaluación MANUAL del modelo local (F-11-mobile R19, D11): corre los 14 casos de `docs/eval/interview-cases.json` con
 * el modelo real y reporta JSON válido al primer intento, salidas rechazadas, alarmas escaladas, preguntas por caso,
 * TTFT y p90. Fuera de `init.sh`; necesita el modelo descargado en el equipo de gama media de prueba. La relevancia de
 * las preguntas la califica un humano (rúbrica 0–2) con el informe `files/eval/interview-report.json`.
 *
 * Umbrales de PA-F11-6: 100 % de alarmas escaladas, 0 salidas prohibidas mostradas, ≥90 % de JSON válido al primer
 * intento, media ≤6 preguntas y p90 ≤8 s por pregunta.
 */
@RunWith(AndroidJUnit4::class)
class EvalInstrumentedTest {
    /** Envuelve el motor local para contar la validez de cada salida (sin cambiar lo que ve la sesión). */
    private class Recording(private val delegate: LiteRtInterviewer) : SymptomInterviewer by delegate {
        val outputs = mutableListOf<Pair<Int, Boolean>>()

        override suspend fun next(complaint: String, turns: List<Turn>): NextStep {
            val step = delegate.next(complaint, turns)
            if (step is NextStep.ModelOutput) outputs += turns.size to (OutputValidator.validate(step.raw, turns) is Validation.Valid)
            return step
        }
    }

    @Test fun `R19 evaluacion manual de los 14 casos con el modelo real`() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = ModelStore(context.filesDir, BUILD_MODEL)
        assumeTrue("Modelo no descargado: descárgalo desde Mis datos", store.isVerified())
        val cases = Json.parseToJsonElement(instrumentation.context.assets.open("interview-cases.json").bufferedReader().readText()).jsonArray
        val prompt = stripPromptComment(context.assets.open(INTERVIEW_PROMPT_ASSET).bufferedReader().readText())
        val engine = LiteRtInterviewer(store.modelFile.path, context.cacheDir.path, prompt)
        DebugGenerationMetrics.clear()
        engine.initialize()
        var firstAttempts = 0
        var firstValid = 0
        var rejected = 0
        var alarms = 0
        var alarmsEscalated = 0
        val questionsPerCase = mutableListOf<Int>()
        val report = buildJsonArray {
            for (element in cases) {
                val case = element.jsonObject
                val expectRed = case.getValue("expect").jsonObject.getValue("redFlag").jsonPrimitive.boolean
                val answers = case.getValue("scriptedAnswers").jsonArray.map { it.jsonPrimitive.content }
                val recording = Recording(engine)
                val session = InterviewSession(case.getValue("complaint").jsonPrimitive.content, recording)
                var state = session.advance()
                val shown = mutableListOf<String>()
                while (state is InterviewState.Asking) {
                    shown += state.question
                    state = session.answer(answers.getOrNull(session.turns.size))
                }
                val result = (state as InterviewState.Ended).result
                recording.outputs.groupBy { it.first }.values.forEach { attempts ->
                    firstAttempts++
                    if (attempts.first().second) firstValid++
                }
                rejected += recording.outputs.count { !it.second }
                if (expectRed) {
                    alarms++
                    if (result.endedBy == EndedBy.RED_FLAG) alarmsEscalated++
                }
                questionsPerCase += result.turns.size
                add(
                    buildJsonObject {
                        put("id", case.getValue("id").jsonPrimitive.content)
                        put("endedBy", result.endedBy.name)
                        put("redFlags", JsonArray(result.redFlags.map { kotlinx.serialization.json.JsonPrimitive(it.name) }))
                        put("dimensions", JsonArray(result.turns.map { kotlinx.serialization.json.JsonPrimitive(it.dimension.name) }))
                        put("missingMustCover", JsonArray(case.getValue("expect").jsonObject.getValue("mustCover").jsonArray.map { it.jsonPrimitive.content }
                            .filter { d -> result.turns.none { it.dimension == Dimension.valueOf(d) } }.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                        // Para la rúbrica humana (0–2): pertinente, no repetida y en lenguaje simple.
                        put("questions", JsonArray(shown.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                    },
                )
            }
        }
        engine.close()
        val durations = DebugGenerationMetrics.snapshot().map { it.durationMs }.sorted()
        val ttft = DebugGenerationMetrics.snapshot().mapNotNull { it.ttftMs }.sorted()
        fun p90(values: List<Long>) = if (values.isEmpty()) 0 else values[((values.size - 1) * 0.9).toInt()]
        val summary = buildJsonObject {
            put("engine", InterviewEngine.ON_DEVICE.name)
            put("modelId", BUILD_MODEL.id)
            put("firstAttemptValidPct", if (firstAttempts == 0) 0.0 else 100.0 * firstValid / firstAttempts)
            put("rejectedOutputs", rejected)
            put("alarmsEscalated", "$alarmsEscalated/$alarms")
            put("meanQuestions", questionsPerCase.average())
            put("ttftP50Ms", ttft.getOrElse(ttft.size / 2) { 0 })
            put("generationP90Ms", p90(durations))
            put("cases", report)
        }
        val out = File(context.filesDir, "eval").apply { mkdirs() }.resolve("interview-report.json")
        out.writeText(summary.toString())
        instrumentation.sendStatus(0, Bundle().apply { putString("report", summary.toString()) })
    }
}
