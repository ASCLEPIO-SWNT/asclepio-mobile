package co.asclepio.mobile.core.interview

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validador determinista y preguntas fijas (F-11-mobile T3). Sin mocks del modelo: salidas literales. */
class OutputValidatorTest {
    private fun out(question: String = "¿Desde cuándo te duele?", dimension: String = "ONSET", done: Boolean = false, suspected: Boolean = false) =
        """{"done":$done,"question":${Json.encodeToString(String.serializer(), question)},"dimension":"$dimension","redFlagSuspected":$suspected}"""

    private fun reason(raw: String, turns: List<Turn> = emptyList()) = (OutputValidator.validate(raw, turns) as? Validation.Invalid)?.reason

    @Test fun `R9 una salida con el esquema y una sola pregunta pasa el validador`() {
        val v = OutputValidator.validate(out("  ¿Desde cuándo te duele?  "), emptyList())
        assertEquals(Validation.Valid(QuestionOutput(false, "¿Desde cuándo te duele?", Dimension.ONSET, false)), v)
    }

    @Test fun `R9 JSON roto, no objeto, campos extra o faltantes y tipos incorrectos se rechazan`() {
        assertEquals("NOT_JSON", reason("""{"done":false,"""))
        assertEquals("NOT_JSON", reason("Claro, aquí va: {}"))
        assertEquals("NOT_OBJECT", reason("""["¿Cuándo?"]"""))
        assertEquals("NOT_OBJECT", reason("null"))
        assertEquals("KEYS", reason(out().dropLast(1) + ""","extra":1}"""))
        assertEquals("KEYS", reason("""{"done":false,"question":"¿Cuándo?","dimension":"ONSET"}"""))
        assertEquals("TYPES", reason("""{"done":"false","question":"¿Cuándo?","dimension":"ONSET","redFlagSuspected":false}"""))
        assertEquals("TYPES", reason("""{"done":false,"question":7,"dimension":"ONSET","redFlagSuspected":false}"""))
        assertEquals("DIMENSION", reason(out(dimension = "DIAGNOSIS")))
    }

    @Test fun `R10 mas de 140 caracteres, vacia, sin signo o con dos se rechazan`() {
        assertEquals("LENGTH", reason(out("¿" + "a".repeat(139) + "?")))
        assertEquals(null, reason(out("¿" + "a".repeat(138) + "?")))
        assertEquals("LENGTH", reason(out("   ")))
        assertEquals("QUESTION_MARK", reason(out("Cuéntame desde cuándo")))
        assertEquals("QUESTION_MARK", reason(out("¿Cuándo empezó? ¿Dónde?")))
    }

    @Test fun `R10 una dimension ya cubierta se rechaza`() {
        val turns = listOf(Turn(Dimension.ONSET, "¿Cuándo empezaron tus síntomas?", "Ayer"))
        assertEquals("REPEATED_DIMENSION", reason(out(dimension = "ONSET"), turns))
    }

    @Test fun `R10 cada patron prohibido se rechaza como palabra completa con tildes y mayusculas`() {
        val samples = mapOf(
            "tienes" to "¿Tienes fiebre?", "podria ser" to "¿Podría ser   migraña?", "diagnostico" to "¿Quieres un diagnóstico?",
            "es probable que" to "¿Es probable que sea gripa?", "toma" to "¿Toma agua?", "tomate" to "¿Tómate algo?",
            "mg" to "¿Usas 500 mg?", "pastilla" to "¿Una pastilla ayuda?", "medicamento para" to "¿Usas medicamento para eso?",
            "te recomiendo" to "¿Te recomiendo reposo?", "nivel" to "¿Qué nivel de dolor?", "triage" to "¿Ya pasaste por triage?",
            "no es grave" to "¿Sabes que no es grave?", "es grave" to "¿Crees que es grave?", "urgente" to "¿Es URGENTE?",
        )
        assertEquals(OutputValidator.FORBIDDEN_PATTERNS.toSet(), samples.keys)
        samples.forEach { (pattern, question) -> assertEquals(pattern, "FORBIDDEN", reason(out(question))) }
        // "síntomas" no contiene "toma" como palabra.
        assertEquals(null, reason(out("¿Cuándo empezaron tus síntomas?")))
    }

    @Test fun `R12 un rechazo del texto con esquema valido conserva redFlagSuspected y uno de esquema no`() {
        assertEquals(Validation.Invalid("FORBIDDEN", true), OutputValidator.validate(out("¿Tienes dolor?", suspected = true), emptyList()))
        assertEquals(Validation.Invalid("KEYS", null), OutputValidator.validate("""{"redFlagSuspected":true}""", emptyList()))
    }

    @Test fun `R9 las preguntas fijas cubren cada dimension y pasan el validador`() {
        assertEquals(Dimension.entries.toList(), OutputValidator.FIXED_QUESTIONS.keys.toList())
        OutputValidator.FIXED_QUESTIONS.forEach { (d, q) ->
            assertTrue("$d", OutputValidator.validate(out(q, d.name), emptyList()) is Validation.Valid)
        }
    }

    @Test fun `R9 la pregunta fija es la de la primera dimension no cubierta y sin ninguna es DONE`() {
        val turns = listOf(Turn(Dimension.ONSET, "a?", "x"), Turn(Dimension.LOCATION, "b?", null))
        assertEquals(Resolved.Ask(Dimension.DURATION, OutputValidator.FIXED_QUESTIONS.getValue(Dimension.DURATION)), OutputValidator.nextFallback(turns))
        assertEquals(Resolved.Done, OutputValidator.nextFallback(Dimension.entries.map { Turn(it, "q?", "a") }))
    }

    @Test fun `R9 el esquema de ResponseFormat es el QUESTION_OUTPUT_SCHEMA de la API`() {
        val schema = Json.parseToJsonElement(OutputValidator.SCHEMA).jsonObject
        assertEquals("false", schema.getValue("additionalProperties").jsonPrimitive.content)
        assertEquals(listOf("done", "question", "dimension", "redFlagSuspected"), schema.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        val enum = schema.getValue("properties").jsonObject.getValue("dimension").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(Dimension.entries.map { it.name }, enum)
    }

    @Test fun `R12 resolve escala la sospecha con turnos respondidos y la ignora con cero`() {
        val answered = listOf(Turn(Dimension.ONSET, "¿Cuándo?", "Ayer"))
        val noSe = listOf(Turn(Dimension.ONSET, "¿Cuándo?", null))
        val rejected = OutputValidator.validate(out("¿Tienes dolor?", "LOCATION", suspected = true), answered)
        assertEquals(Resolved.RedFlag, OutputValidator.resolve("Me caí", answered, rejected))
        assertNull(OutputValidator.resolve("Me caí", noSe, OutputValidator.validate(out("¿Tienes dolor?", "LOCATION", suspected = true), noSe)))
        val valid = OutputValidator.validate(out("¿Dónde te duele?", "LOCATION", suspected = true), emptyList())
        assertEquals(Resolved.Ask(Dimension.LOCATION, "¿Dónde te duele?"), OutputValidator.resolve("Me caí", emptyList(), valid))
    }

    @Test fun `R14 done exige tres respondidas, ONSET y LOCATION si hay dolor`() {
        fun done(turns: List<Turn>) = OutputValidator.validate(out("¿Algo más?", "HISTORY", done = true), turns)
        val three = listOf(Turn(Dimension.ONSET, "a?", "Ayer"), Turn(Dimension.INTENSITY, "b?", "7"), Turn(Dimension.ASSOCIATED, "c?", "Fiebre"))
        assertEquals(Resolved.Done, OutputValidator.resolve("Fiebre", three, done(three)))
        assertEquals(
            Resolved.Ask(Dimension.LOCATION, OutputValidator.FIXED_QUESTIONS.getValue(Dimension.LOCATION)),
            OutputValidator.resolve("Me duele mucho", three, done(three)),
        )
        val noOnset = listOf(Turn(Dimension.DURATION, "a?", "Va y viene"), Turn(Dimension.INTENSITY, "b?", "7"), Turn(Dimension.ASSOCIATED, "c?", "Fiebre"))
        assertEquals(Resolved.Ask(Dimension.ONSET, OutputValidator.FIXED_QUESTIONS.getValue(Dimension.ONSET)), OutputValidator.resolve("Fiebre", noOnset, done(noOnset)))
        val two = three.take(2) + Turn(Dimension.ASSOCIATED, "c?", null)
        assertEquals(Resolved.Ask(Dimension.HISTORY, "¿Algo más?"), OutputValidator.resolve("Fiebre", two, done(two)))
        assertEquals(Dimension.LOCATION, OutputValidator.missingMandatory("Fiebre", three.take(1) + Turn(Dimension.ASSOCIATED, "q?", "me duelen las piernas")))
    }
}
