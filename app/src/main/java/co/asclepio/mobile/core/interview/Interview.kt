package co.asclepio.mobile.core.interview

import kotlinx.serialization.Serializable

// Modelos de la entrevista de síntomas (F-11-mobile, design.md). Mismas definiciones que interview.rules.ts de
// F-11-api y que SymptomInterviewDto/InterviewTurnDto del contrato 0.11.0. Sin dependencias de Android.

/** Aspectos de la entrevista, en el orden de DIMENSIONS (la pregunta fija usa la primera no cubierta). */
@Serializable
enum class Dimension { ONSET, DURATION, INTENSITY, LOCATION, ASSOCIATED, HISTORY, MEDS_ALLERGIES, ALARM_SIGNS }

/** Señales de alarma (PA-F11-1, PROVISIONAL), en el orden de RED_FLAGS. */
@Serializable
enum class RedFlag {
    CHEST_PAIN,
    BREATHING_DIFFICULTY,
    LOSS_OF_CONSCIOUSNESS,
    SEIZURE,
    SEVERE_BLEEDING,
    STROKE_SIGNS,
    SUICIDAL_IDEATION,
}

/** Motor que generó las preguntas. */
@Serializable
enum class InterviewEngine { ON_DEVICE, SERVER }

/** Motivo de fin de la entrevista. */
@Serializable
enum class EndedBy { COMPLETE, MAX_QUESTIONS, RED_FLAG, PATIENT }

/** Límites del glosario (INTERVIEW_LIMITS de la API). */
object InterviewLimits {
    const val MAX_TURNS = 6
    const val MAX_REQUEST_TURNS = 5
    const val MIN_ANSWERED_FOR_DONE = 3
    const val MODEL_ID_MAX = 80
    const val TURN_QUESTION_MAX = 200
    const val TURN_ANSWER_MAX = 500
    const val LLM_QUESTION_MAX = 140
}

/** InterviewTurnDto: `answer = null` es "No sé / prefiero no responder". El `toString()` no lleva contenido (R16). */
@Serializable
data class Turn(val dimension: Dimension, val question: String, val answer: String?) {
    override fun toString(): String = "Turn(dimension=$dimension, question=***, answer=${if (answer == null) "null" else "***"})"
}

/** SymptomInterviewDto (contrato 0.11.0), el campo `interview` del pre-registro. Sin contenido en `toString()`. */
@Serializable
data class SymptomInterviewDto(
    val engine: InterviewEngine,
    val modelId: String,
    val turns: List<Turn>,
    val redFlags: List<RedFlag>,
    val endedBy: EndedBy,
) {
    override fun toString(): String =
        "SymptomInterviewDto(engine=$engine, modelId=$modelId, turns=${turns.size}, redFlags=$redFlags, endedBy=$endedBy)"
}

/** Paso que devuelve un motor. La sesión valida todo antes de mostrar nada (D4, R10). */
sealed interface NextStep {
    /** Salida cruda del modelo local (JSON según el esquema), aún sin validar. */
    class ModelOutput(val raw: String) : NextStep {
        override fun toString() = "ModelOutput(***)"
    }

    /** Pregunta del motor del servidor; la sesión la vuelve a pasar por el validador. */
    data class Question(val dimension: Dimension, val question: String) : NextStep {
        override fun toString() = "Question(dimension=$dimension, question=***)"
    }

    data object Done : NextStep

    data class RedFlagHit(val redFlags: List<RedFlag>) : NextStep
}

/** El motor no puede seguir (servidor: error, 429, 503 o red; R7). La entrevista termina con lo respondido. */
class InterviewUnavailableException : Exception("Interview engine unavailable")

/** Puerto del motor (design.md): local (LiteRT-LM) o del servidor (next-question). */
interface SymptomInterviewer {
    val engine: InterviewEngine
    val modelId: String

    /** Siguiente paso para el motivo y los turnos. Lanza [InterviewUnavailableException] si el motor no responde. */
    suspend fun next(complaint: String, turns: List<Turn>): NextStep

    /** Libera los recursos del motor (el `Engine` local, D7). */
    fun close() {}
}
