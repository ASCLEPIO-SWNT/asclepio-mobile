package co.asclepio.mobile.feature.interview

import co.asclepio.mobile.core.interview.Dimension
import co.asclepio.mobile.core.interview.Turn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Partes JVM del motor local (T8): prompt interview-v2, mensaje del usuario, esquema y métricas de depuración. */
class LiteRtInterviewerTest {
    private val asset = File("src/main/assets/$INTERVIEW_PROMPT_ASSET")
    private val source = File("src/main/java/co/asclepio/mobile/feature/interview/LiteRtInterviewer.kt").readText()

    @Test fun `R9 el prompt local es copia literal de interview-v2 de la API (SHA-256 fijado) sin el comentario`() {
        val hex = MessageDigest.getInstance("SHA-256").digest(asset.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("d11c3a4fda90271acd15d379e68db1ab2c64c6f514c200e80e11fbe5f2664515", hex)
        val prompt = stripPromptComment(asset.readText())
        assertTrue(prompt.startsWith("Eres un asistente que ayuda a un paciente"))
        assertFalse(prompt.contains("<!--") || prompt.contains("prompt_version"))
    }

    @Test fun `R9 el mensaje del usuario lleva el motivo y los turnos con answer null como el servidor`() {
        val message = Json.parseToJsonElement(interviewUserMessage("Tos", listOf(Turn(Dimension.ONSET, "¿Cuándo?", null))))
        assertEquals(
            JsonObject(
                mapOf(
                    "chief_complaint_text" to JsonPrimitive("Tos"),
                    "turns" to JsonArray(listOf(JsonObject(mapOf("dimension" to JsonPrimitive("ONSET"), "question" to JsonPrimitive("¿Cuándo?"), "answer" to JsonNull)))),
                ),
            ),
            message,
        )
    }

    @Test fun `R9 el adaptador genera con ResponseFormat json del esquema del validador y enableResponseFormat`() {
        assertTrue(source.contains("ResponseFormat.json(OutputValidator.SCHEMA)"))
        assertTrue(source.contains("enableResponseFormat = true"))
        assertTrue(source.contains("Backend.GPU()") && source.contains("Backend.CPU()"))
        assertTrue(source.contains("cancelProcess()"))
    }

    @Test fun `R18 las metricas de generacion solo guardan numeros y solo en depuracion`() {
        assertEquals(listOf("ttftMs", "durationMs"), GenerationSample::class.java.declaredFields.map { it.name }.filter { !it.startsWith("$") })
        assertTrue(source.contains("if (BuildConfig.DEBUG) samples += sample"))
        DebugGenerationMetrics.clear()
        DebugGenerationMetrics.record(GenerationSample(900, 4_000))
        assertEquals(listOf(GenerationSample(900, 4_000)), DebugGenerationMetrics.snapshot())
        DebugGenerationMetrics.clear()
    }
}
