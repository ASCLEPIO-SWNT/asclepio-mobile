package co.asclepio.mobile.feature.interview

import co.asclepio.mobile.core.interview.Dimension
import co.asclepio.mobile.core.interview.RedFlagRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Conjunto de evaluación D11 (R19): forma del archivo y parte JVM (reglas). La corrida con el modelo es manual. */
class EvalCasesTest {
    private val cases = Json.parseToJsonElement(File("../docs/eval/interview-cases.json").readText()).jsonArray.map { it.jsonObject }

    @Test fun `R19 hay 14 casos, 9 ambiguos y 5 de alarma, con id, motivo, respuestas y expectativas`() {
        assertEquals(14, cases.size)
        assertEquals(14, cases.map { it.getValue("id").jsonPrimitive.content }.toSet().size)
        val red = cases.filter { it.getValue("expect").jsonObject.getValue("redFlag").jsonPrimitive.boolean }
        assertEquals(5, red.size)
        cases.forEach { c ->
            assertTrue(c.getValue("complaint").jsonPrimitive.content.isNotBlank())
            assertTrue(c.getValue("scriptedAnswers").jsonArray.size <= 6)
            c.getValue("expect").jsonObject.getValue("mustCover").jsonArray.forEach { Dimension.valueOf(it.jsonPrimitive.content) }
        }
        assertTrue(File("src/androidTest/java/co/asclepio/mobile/feature/interview/EvalInstrumentedTest.kt").isFile)
        assertTrue(File("build.gradle.kts").readText().contains("""sourceSets["androidTest"].assets.srcDir("../docs/eval")"""))
    }

    @Test fun `R19 las reglas escalan los 5 casos de alarma y ninguno de los ambiguos con sus respuestas`() {
        cases.forEach { c ->
            val expectRed = c.getValue("expect").jsonObject.getValue("redFlag").jsonPrimitive.boolean
            val texts = listOf(c.getValue("complaint").jsonPrimitive.content) + c.getValue("scriptedAnswers").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(c.getValue("id").jsonPrimitive.content, expectRed, RedFlagRules.detect(*texts.toTypedArray()).isNotEmpty())
        }
    }
}
