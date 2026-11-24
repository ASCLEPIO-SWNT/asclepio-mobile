package co.asclepio.mobile.core.interview

import kotlinx.coroutines.delay

/** Motor falso guionizado (D4): cada llamada a [next] consume la siguiente respuesta y registra los turnos recibidos. */
class FakeInterviewer(
    override val engine: InterviewEngine = InterviewEngine.ON_DEVICE,
    override val modelId: String = "fake-model",
    vararg script: suspend (List<Turn>) -> NextStep,
) : SymptomInterviewer {
    private val pending = ArrayDeque(script.toList())
    val calls = mutableListOf<List<Turn>>()
    var closed = false
        private set

    override suspend fun next(complaint: String, turns: List<Turn>): NextStep {
        calls += turns
        val step = pending.removeFirstOrNull() ?: error("FakeInterviewer sin guion para la llamada ${calls.size}")
        return step(turns)
    }

    override fun close() {
        closed = true
    }

    companion object {
        fun output(question: String, dimension: Dimension, done: Boolean = false, suspected: Boolean = false): suspend (List<Turn>) -> NextStep = {
            NextStep.ModelOutput(
                """{"done":$done,"question":"$question","dimension":"${dimension.name}","redFlagSuspected":$suspected}""",
            )
        }

        fun raw(text: String): suspend (List<Turn>) -> NextStep = { NextStep.ModelOutput(text) }

        fun step(step: NextStep): suspend (List<Turn>) -> NextStep = { step }

        fun slow(ms: Long): suspend (List<Turn>) -> NextStep = {
            delay(ms)
            NextStep.ModelOutput("{}")
        }

        val unavailable: suspend (List<Turn>) -> NextStep = { throw InterviewUnavailableException() }
    }
}
