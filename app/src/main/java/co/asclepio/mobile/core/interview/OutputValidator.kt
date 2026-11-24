package co.asclepio.mobile.core.interview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Salida del modelo con el esquema de [OutputValidator.SCHEMA]. */
data class QuestionOutput(val done: Boolean, val question: String, val dimension: Dimension, val redFlagSuspected: Boolean) {
    override fun toString() = "QuestionOutput(done=$done, question=***, dimension=$dimension, redFlagSuspected=$redFlagSuspected)"
}

/** Resultado del validador. `redFlagSuspected` solo en un rechazo del texto con el esquema válido (R12 enmendado). */
sealed interface Validation {
    data class Valid(val output: QuestionOutput) : Validation

    data class Invalid(val reason: String, val redFlagSuspected: Boolean? = null) : Validation
}

/** Decisión tras validar una salida (`resolveOutput` de la API). */
sealed interface Resolved {
    data class Ask(val dimension: Dimension, val question: String) : Resolved {
        override fun toString() = "Ask(dimension=$dimension, question=***)"
    }

    data object Done : Resolved

    /** Sospecha del modelo: sin señales de las reglas (ya aplicadas antes). */
    data object RedFlag : Resolved
}

/**
 * Validador determinista de la salida del modelo (R9, R10, F-11.AC2). Port de `validateQuestion`, `FIXED_QUESTIONS`,
 * `nextFallback`, `answeredDimensions`, `missingMandatory` y `resolveOutput` de `interview.rules.ts` (F-11-api).
 * El motivo de rechazo nunca lleva el contenido.
 */
object OutputValidator {
    /** Esquema JSON de la salida, para `ResponseFormat.json` (igual a QUESTION_OUTPUT_SCHEMA). */
    val SCHEMA: String = """{"type":"object","additionalProperties":false,"required":["done","question","dimension","redFlagSuspected"],""" +
        """"properties":{"done":{"type":"boolean"},"question":{"type":"string"},"dimension":{"type":"string","enum":[""" +
        Dimension.entries.joinToString(",") { "\"${it.name}\"" } + """]},"redFlagSuspected":{"type":"boolean"}}}"""

    /** Diagnóstico, tratamiento o dosis y nivel o gravedad (glosario). Palabras completas sobre el texto normalizado. */
    val FORBIDDEN_PATTERNS: List<String> = listOf(
        "tienes", "podria ser", "diagnostico", "es probable que", "toma", "tomate", "mg", "pastilla",
        "medicamento para", "te recomiendo", "nivel", "triage", "no es grave", "es grave", "urgente",
    )

    private val FORBIDDEN = FORBIDDEN_PATTERNS.map { Regex("""\b${it.replace(" ", """\s+""")}\b""") }

    /** Una pregunta fija por dimensión: sencillas, en español y que pasan el validador. */
    val FIXED_QUESTIONS: Map<Dimension, String> = linkedMapOf(
        Dimension.ONSET to "¿Cuándo empezaron tus síntomas?",
        Dimension.DURATION to "¿Los síntomas son continuos o van y vienen?",
        Dimension.INTENSITY to "Del 0 al 10, ¿qué tan fuerte es la molestia?",
        Dimension.LOCATION to "¿En qué parte del cuerpo sientes la molestia?",
        Dimension.ASSOCIATED to "¿Has notado otros síntomas, como fiebre, vómito o mareo?",
        Dimension.HISTORY to "¿Sufres de alguna enfermedad crónica, como diabetes o hipertensión?",
        Dimension.MEDS_ALLERGIES to "¿Usas algún medicamento a diario o eres alérgico a algo?",
        Dimension.ALARM_SIGNS to "¿Has tenido desmayos, dificultad para respirar o dolor en el pecho?",
    )

    private val KEYS = listOf("dimension", "done", "question", "redFlagSuspected")

    private val json = Json

    private fun JsonPrimitive.strictBoolean(): Boolean? = if (isString) null else booleanOrNull

    fun validate(raw: String, turns: List<Turn>): Validation {
        val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return Validation.Invalid("NOT_JSON")
        val o = parsed as? JsonObject ?: return Validation.Invalid("NOT_OBJECT")
        if (o.keys.sorted() != KEYS) return Validation.Invalid("KEYS")
        val done = (o["done"] as? JsonPrimitive)?.strictBoolean()
        val suspected = (o["redFlagSuspected"] as? JsonPrimitive)?.strictBoolean()
        if (done == null || suspected == null) return Validation.Invalid("TYPES")
        val dimensionValue = (o["dimension"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val dimension = Dimension.entries.firstOrNull { it.name == dimensionValue } ?: return Validation.Invalid("DIMENSION")
        val questionValue = (o["question"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return Validation.Invalid("TYPES")
        // Esquema válido: desde aquí los rechazos son del texto y conservan la sospecha (R12 enmendado).
        fun textFail(reason: String) = Validation.Invalid(reason, suspected)
        val question = questionValue.trim()
        if (question.isEmpty() || question.length > InterviewLimits.LLM_QUESTION_MAX) return textFail("LENGTH")
        if (!question.endsWith("?") || question.count { it == '?' } != 1) return textFail("QUESTION_MARK")
        if (turns.any { it.dimension == dimension }) return textFail("REPEATED_DIMENSION")
        val normalized = RedFlagRules.normalize(question)
        if (FORBIDDEN.any { it.containsMatchIn(normalized) }) return textFail("FORBIDDEN")
        return Validation.Valid(QuestionOutput(done, question, dimension, suspected))
    }

    /** Pregunta fija de la primera dimensión no cubierta o, si están todas, [Resolved.Done]. */
    fun nextFallback(turns: List<Turn>): Resolved {
        val covered = turns.map { it.dimension }.toSet()
        val d = Dimension.entries.firstOrNull { it !in covered } ?: return Resolved.Done
        return Resolved.Ask(d, FIXED_QUESTIONS.getValue(d))
    }

    /** Dimensiones distintas con respuesta distinta de null. */
    fun answeredDimensions(turns: List<Turn>): Int = turns.filter { it.answer != null }.map { it.dimension }.toSet().size

    /** ONSET siempre y LOCATION si el motivo o alguna respuesta mencionan dolor (enmienda del 2026-11-18). */
    fun missingMandatory(complaint: String, turns: List<Turn>): Dimension? {
        val covered = turns.map { it.dimension }.toSet()
        if (Dimension.ONSET !in covered) return Dimension.ONSET
        val pain = RedFlagRules.mentionsPain(complaint) || turns.any { it.answer != null && RedFlagRules.mentionsPain(it.answer) }
        return if (pain && Dimension.LOCATION !in covered) Dimension.LOCATION else null
    }

    /**
     * Decide a partir de una salida ya validada. `null` = inválida sin sospecha atendible (reintento o pregunta fija).
     * La sospecha escala aunque falle el texto, salvo con 0 turnos respondidos; `done` exige ≥3 respondidas y las
     * obligatorias, y si falta una se hace su pregunta fija.
     */
    fun resolve(complaint: String, turns: List<Turn>, checked: Validation): Resolved? {
        val suspected = when (checked) {
            is Validation.Valid -> checked.output.redFlagSuspected
            is Validation.Invalid -> checked.redFlagSuspected == true
        }
        if (suspected && answeredDimensions(turns) > 0) return Resolved.RedFlag
        val o = (checked as? Validation.Valid)?.output ?: return null
        if (o.done && answeredDimensions(turns) >= InterviewLimits.MIN_ANSWERED_FOR_DONE) {
            val missing = missingMandatory(complaint, turns) ?: return Resolved.Done
            return Resolved.Ask(missing, FIXED_QUESTIONS.getValue(missing))
        }
        return Resolved.Ask(o.dimension, o.question)
    }
}
