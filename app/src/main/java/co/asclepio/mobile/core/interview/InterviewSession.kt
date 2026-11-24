package co.asclepio.mobile.core.interview

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.cancellation.CancellationException

/** Resultado de la entrevista. [assistantUnavailable]: el motor del servidor falló (R7). */
data class InterviewResult(
    val engine: InterviewEngine,
    val modelId: String,
    val turns: List<Turn>,
    val redFlags: List<RedFlag>,
    val endedBy: EndedBy,
    val assistantUnavailable: Boolean = false,
) {
    /** Campo `interview` del pre-registro; `null` si el motor falló sin ningún turno (se envía como en 0.10.0, R7). */
    fun toDto(): SymptomInterviewDto? =
        if (assistantUnavailable && turns.isEmpty()) null else SymptomInterviewDto(engine, modelId.take(InterviewLimits.MODEL_ID_MAX), turns, redFlags, endedBy)

    override fun toString() = "InterviewResult(engine=$engine, turns=${turns.size}, redFlags=$redFlags, endedBy=$endedBy, unavailable=$assistantUnavailable)"
}

/** Estado de la sesión. Solo [Asking] lleva texto para el paciente, y siempre validado o fijo (R10). */
sealed interface InterviewState {
    data object Generating : InterviewState

    /** [number]: 1 a 6. */
    data class Asking(val dimension: Dimension, val question: String, val number: Int) : InterviewState {
        override fun toString() = "Asking(dimension=$dimension, question=***, number=$number)"
    }

    data class Ended(val result: InterviewResult) : InterviewState
}

/**
 * Máquina de estados pura de la entrevista (F-11-mobile D4): la seguridad vive aquí y no en los motores.
 * - Antes de cada paso, las reglas de alarma sobre el motivo y todas las respuestas (R11, R12).
 * - Toda salida pasa por [OutputValidator]: una salida local inválida se reintenta una vez; si vuelve a fallar, o tarda
 *   más de [generationTimeoutMs], se usa la pregunta fija (R9). Una pregunta del servidor también se valida (R10).
 * - Máximo [InterviewLimits.MAX_TURNS] preguntas; `done` solo con ≥3 respondidas y las obligatorias (R14).
 * - Si el motor no está disponible, termina con lo respondido (R7).
 */
class InterviewSession(
    val complaint: String,
    private val interviewer: SymptomInterviewer,
    initialTurns: List<Turn> = emptyList(),
    private val generationTimeoutMs: Long = GENERATION_TIMEOUT_MS,
) {
    private val _turns = initialTurns.toMutableList()
    val turns: List<Turn> get() = _turns.toList()

    var state: InterviewState = InterviewState.Generating
        private set

    /** Primer paso, o el de la pregunta actual tras una cancelación (R17). */
    suspend fun advance(): InterviewState {
        if (state is InterviewState.Ended) return state
        val flags = RedFlagRules.detect(complaint, _turns)
        if (flags.isNotEmpty()) return end(EndedBy.RED_FLAG, flags)
        if (_turns.size >= InterviewLimits.MAX_TURNS) return end(EndedBy.MAX_QUESTIONS)
        state = InterviewState.Generating
        val outcome = try {
            generate()
        } catch (e: InterviewUnavailableException) {
            return end(EndedBy.PATIENT, unavailable = true)
        }
        return when (outcome) {
            is Outcome.Ask -> InterviewState.Asking(outcome.dimension, outcome.question, _turns.size + 1).also { state = it }
            Outcome.Done -> end(EndedBy.COMPLETE)
            is Outcome.Flag -> end(EndedBy.RED_FLAG, outcome.redFlags)
        }
    }

    /** Respuesta a la pregunta actual: `null` es "No sé" (R8). Después, reglas y siguiente paso. */
    suspend fun answer(answer: String?): InterviewState {
        val asking = state as? InterviewState.Asking ?: return state
        val text = answer?.trim()?.take(InterviewLimits.TURN_ANSWER_MAX)?.takeIf { it.isNotEmpty() }
        _turns += Turn(asking.dimension, asking.question.take(InterviewLimits.TURN_QUESTION_MAX), text)
        state = InterviewState.Generating
        return advance()
    }

    /** "Terminar" (R8): el paciente acaba la entrevista. */
    fun finish(): InterviewState = if (state is InterviewState.Ended) state else end(EndedBy.PATIENT)

    private fun end(endedBy: EndedBy, redFlags: List<RedFlag> = emptyList(), unavailable: Boolean = false): InterviewState {
        state = InterviewState.Ended(InterviewResult(interviewer.engine, interviewer.modelId, turns, redFlags, endedBy, unavailable))
        return state
    }

    private sealed interface Outcome {
        data class Ask(val dimension: Dimension, val question: String) : Outcome

        data object Done : Outcome

        data class Flag(val redFlags: List<RedFlag>) : Outcome
    }

    private fun Resolved.toOutcome(): Outcome = when (this) {
        is Resolved.Ask -> Outcome.Ask(dimension, question)
        Resolved.Done -> Outcome.Done
        Resolved.RedFlag -> Outcome.Flag(emptyList())
    }

    private fun fallback(): Outcome = OutputValidator.nextFallback(_turns).toOutcome()

    private suspend fun generate(): Outcome {
        repeat(ATTEMPTS) {
            val snapshot = turns
            val step = try {
                withTimeoutOrNull(generationTimeoutMs) { interviewer.next(complaint, snapshot) } ?: return fallback()
            } catch (e: CancellationException) {
                throw e
            } catch (e: InterviewUnavailableException) {
                throw e
            } catch (e: Exception) {
                // Un fallo del motor local cuenta como salida inválida.
                null
            }
            when (step) {
                null -> Unit
                is NextStep.ModelOutput ->
                    OutputValidator.resolve(complaint, snapshot, OutputValidator.validate(step.raw, snapshot))?.let { return it.toOutcome() }
                is NextStep.Question -> {
                    val raw = appJsonQuestion(step)
                    return OutputValidator.resolve(complaint, snapshot, OutputValidator.validate(raw, snapshot))?.toOutcome() ?: fallback()
                }
                NextStep.Done -> return gateDone(snapshot)
                is NextStep.RedFlagHit -> return Outcome.Flag(step.redFlags)
            }
        }
        return fallback()
    }

    /** `DONE` de un motor que no da la salida cruda: misma regla que `resolveOutput` (R14 enmendado). */
    private fun gateDone(snapshot: List<Turn>): Outcome {
        if (OutputValidator.answeredDimensions(snapshot) < InterviewLimits.MIN_ANSWERED_FOR_DONE) return fallback()
        val missing = OutputValidator.missingMandatory(complaint, snapshot) ?: return Outcome.Done
        return Outcome.Ask(missing, OutputValidator.FIXED_QUESTIONS.getValue(missing))
    }

    private fun appJsonQuestion(step: NextStep.Question): String =
        buildJsonObject {
            put("done", JsonPrimitive(false))
            put("question", JsonPrimitive(step.question))
            put("dimension", JsonPrimitive(step.dimension.name))
            put("redFlagSuspected", JsonPrimitive(false))
        }.toString()

    companion object {
        /** R9: más de 20 s por generación → pregunta fija. */
        const val GENERATION_TIMEOUT_MS = 20_000L
        private const val ATTEMPTS = 2
    }
}
