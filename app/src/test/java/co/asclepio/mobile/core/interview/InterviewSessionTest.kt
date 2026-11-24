package co.asclepio.mobile.core.interview

import co.asclepio.mobile.core.interview.FakeInterviewer.Companion.output
import co.asclepio.mobile.core.interview.FakeInterviewer.Companion.raw
import co.asclepio.mobile.core.interview.FakeInterviewer.Companion.slow
import co.asclepio.mobile.core.interview.FakeInterviewer.Companion.step
import co.asclepio.mobile.core.interview.FakeInterviewer.Companion.unavailable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sesión de la entrevista con un motor falso (F-11-mobile T4): toda la seguridad se prueba en la JVM. */
class InterviewSessionTest {
    private val fixed = OutputValidator.FIXED_QUESTIONS

    private fun asking(state: InterviewState) = state as InterviewState.Asking
    private fun ended(state: InterviewState) = (state as InterviewState.Ended).result

    @Test fun `R11 una senal en el motivo termina la entrevista sin llamar al motor`() = runTest {
        val fake = FakeInterviewer()
        val result = ended(InterviewSession("Me duele el pecho desde anoche", fake).advance())
        assertEquals(EndedBy.RED_FLAG, result.endedBy)
        assertEquals(listOf(RedFlag.CHEST_PAIN), result.redFlags)
        assertEquals(0, fake.calls.size)
    }

    @Test fun `R11 las reglas se aplican a la respuesta antes de pedir la siguiente pregunta`() = runTest {
        val fake = FakeInterviewer(script = arrayOf(output("¿Cuándo empezó?", Dimension.ONSET)))
        val session = InterviewSession("Me siento mal", fake)
        assertEquals("¿Cuándo empezó?", asking(session.advance()).question)
        val result = ended(session.answer("Hace una hora y me desmayé en el baño"))
        assertEquals(1, fake.calls.size)
        assertEquals(EndedBy.RED_FLAG, result.endedBy)
        assertEquals(listOf(RedFlag.LOSS_OF_CONSCIOUSNESS), result.redFlags)
        assertEquals(1, result.turns.size)
    }

    @Test fun `R9 una salida invalida se reintenta una vez y la segunda valida se muestra`() = runTest {
        val fake = FakeInterviewer(script = arrayOf(raw("no es JSON"), output("¿Cuándo empezó?", Dimension.ONSET)))
        val state = asking(InterviewSession("Mareo", fake).advance())
        assertEquals(2, fake.calls.size)
        assertEquals(InterviewState.Asking(Dimension.ONSET, "¿Cuándo empezó?", 1), state)
    }

    @Test fun `R10 dos salidas invalidas o prohibidas dan la pregunta fija y nunca el texto del modelo`() = runTest {
        val fake = FakeInterviewer(
            script = arrayOf(output("¿Tienes migraña?", Dimension.ONSET), output("Toma ibuprofeno de 400 mg?", Dimension.ONSET)),
        )
        val state = asking(InterviewSession("Dolor de cabeza", fake).advance())
        assertEquals(2, fake.calls.size)
        assertEquals(InterviewState.Asking(Dimension.ONSET, fixed.getValue(Dimension.ONSET), 1), state)
    }

    @Test fun `R9 una excepcion del motor local cuenta como salida invalida`() = runTest {
        val fake = FakeInterviewer(script = arrayOf({ error("native") }, { error("native") }))
        assertEquals(fixed.getValue(Dimension.ONSET), asking(InterviewSession("Tos", fake).advance()).question)
    }

    @Test fun `R9 una generacion de mas de 20 s da la pregunta fija sin reintentar`() = runTest {
        val fake = FakeInterviewer(script = arrayOf(slow(25_000)))
        val start = currentTime
        val state = asking(InterviewSession("Tos", fake).advance())
        assertEquals(20_000L, currentTime - start)
        assertEquals(1, fake.calls.size)
        assertEquals(fixed.getValue(Dimension.ONSET), state.question)
    }

    @Test fun `R12 redFlagSuspected con un turno respondido escala aunque la pregunta no pase el validador`() = runTest {
        val fake = FakeInterviewer(
            script = arrayOf(output("¿Cuándo empezó?", Dimension.ONSET), output("¿Tienes dolor?", Dimension.LOCATION, suspected = true)),
        )
        val session = InterviewSession("Me siento mal", fake)
        session.advance()
        val result = ended(session.answer("Hoy, me cuesta un poco"))
        assertEquals(EndedBy.RED_FLAG, result.endedBy)
        assertEquals(emptyList<RedFlag>(), result.redFlags)
        assertEquals(2, fake.calls.size)
    }

    @Test fun `R12 con cero turnos respondidos redFlagSuspected del modelo se ignora`() = runTest {
        val fake = FakeInterviewer(script = arrayOf(output("¿Cuándo empezó?", Dimension.ONSET, suspected = true)))
        assertEquals("¿Cuándo empezó?", asking(InterviewSession("Me caí", fake).advance()).question)
    }

    @Test fun `R12 RED_FLAG del motor del servidor corta la entrevista con sus senales`() = runTest {
        val fake = FakeInterviewer(InterviewEngine.SERVER, script = arrayOf(step(NextStep.RedFlagHit(listOf(RedFlag.SEIZURE)))))
        val result = ended(InterviewSession("Fiebre", fake).advance())
        assertEquals(EndedBy.RED_FLAG, result.endedBy)
        assertEquals(listOf(RedFlag.SEIZURE), result.redFlags)
        assertEquals(InterviewEngine.SERVER, result.engine)
    }

    @Test fun `R10 una pregunta del servidor tambien pasa el validador`() = runTest {
        val fake = FakeInterviewer(InterviewEngine.SERVER, script = arrayOf(step(NextStep.Question(Dimension.ONSET, "¿Es grave?"))))
        assertEquals(fixed.getValue(Dimension.ONSET), asking(InterviewSession("Fiebre", fake).advance()).question)
    }

    @Test fun `R14 termina por MAX_QUESTIONS tras seis preguntas sin llamar al motor por una septima`() = runTest {
        val dims = Dimension.entries.take(6)
        val fake = FakeInterviewer(script = dims.map { output("¿Pregunta ${it.ordinal}?", it) }.toTypedArray())
        val session = InterviewSession("Mareo", fake)
        var state = session.advance()
        repeat(6) { i ->
            assertEquals(i + 1, asking(state).number)
            state = session.answer("Respuesta $i")
        }
        val result = ended(state)
        assertEquals(EndedBy.MAX_QUESTIONS, result.endedBy)
        assertEquals(6, result.turns.size)
        assertEquals(6, fake.calls.size)
    }

    @Test fun `R14 done con tres respondidas y ONSET termina por COMPLETE`() = runTest {
        val fake = FakeInterviewer(
            script = arrayOf(
                output("¿Cuándo empezó?", Dimension.ONSET),
                output("¿Qué tan fuerte?", Dimension.INTENSITY),
                output("¿Algo más?", Dimension.ASSOCIATED),
                output("¿Antecedentes?", Dimension.HISTORY, done = true),
            ),
        )
        val session = InterviewSession("Fiebre", fake)
        session.advance()
        session.answer("Ayer")
        session.answer("Siete")
        val result = ended(session.answer("Escalofríos"))
        assertEquals(EndedBy.COMPLETE, result.endedBy)
        assertEquals(listOf(Dimension.ONSET, Dimension.INTENSITY, Dimension.ASSOCIATED), result.turns.map { it.dimension })
    }

    @Test fun `R14 done sin LOCATION cuando hay dolor da la pregunta fija de LOCATION`() = runTest {
        val fake = FakeInterviewer(
            script = arrayOf(
                output("¿Cuándo empezó?", Dimension.ONSET),
                output("¿Qué tan fuerte?", Dimension.INTENSITY),
                output("¿Algo más?", Dimension.ASSOCIATED),
                output("¿Antecedentes?", Dimension.HISTORY, done = true),
            ),
        )
        val session = InterviewSession("Me duele mucho", fake)
        session.advance()
        session.answer("Ayer")
        session.answer("Siete")
        assertEquals(InterviewState.Asking(Dimension.LOCATION, fixed.getValue(Dimension.LOCATION), 4), session.answer("Náuseas"))
    }

    @Test fun `R14 done del servidor con menos de tres respondidas sigue con la pregunta fija`() = runTest {
        val fake = FakeInterviewer(
            InterviewEngine.SERVER,
            script = arrayOf(step(NextStep.Question(Dimension.ONSET, "¿Cuándo empezó?")), step(NextStep.Done)),
        )
        val session = InterviewSession("Fiebre", fake)
        session.advance()
        assertEquals(InterviewState.Asking(Dimension.DURATION, fixed.getValue(Dimension.DURATION), 2), session.answer(null))
    }

    @Test fun `R8 No se guarda la respuesta como null y Terminar acaba por PATIENT`() = runTest {
        val fake = FakeInterviewer(script = arrayOf(output("¿Cuándo empezó?", Dimension.ONSET), output("¿Dónde?", Dimension.LOCATION)))
        val session = InterviewSession("Tos", fake)
        session.advance()
        session.answer(null)
        assertNull(fake.calls[1].single().answer)
        val result = ended(session.finish())
        assertEquals(EndedBy.PATIENT, result.endedBy)
        assertEquals(listOf(Turn(Dimension.ONSET, "¿Cuándo empezó?", null)), result.turns)
        assertEquals(listOf(Turn(Dimension.ONSET, "¿Cuándo empezó?", null)), result.toDto()!!.turns)
    }

    @Test fun `R7 el motor no disponible termina con lo respondido y sin turnos no hay interview`() = runTest {
        val first = ended(InterviewSession("Tos", FakeInterviewer(InterviewEngine.SERVER, script = arrayOf(unavailable))).advance())
        assertTrue(first.assistantUnavailable)
        assertNull(first.toDto())
        val fake = FakeInterviewer(InterviewEngine.SERVER, script = arrayOf(step(NextStep.Question(Dimension.ONSET, "¿Cuándo empezó?")), unavailable))
        val session = InterviewSession("Tos", fake)
        session.advance()
        val result = ended(session.answer("Ayer"))
        assertTrue(result.assistantUnavailable)
        assertEquals(1, result.toDto()!!.turns.size)
        assertEquals(InterviewEngine.SERVER, result.toDto()!!.engine)
    }

    @Test fun `R17 una generacion cancelada conserva los turnos y advance regenera la pregunta actual`() = runTest {
        val fake = FakeInterviewer(
            script = arrayOf(output("¿Cuándo empezó?", Dimension.ONSET), slow(10_000), output("¿Dónde?", Dimension.LOCATION)),
        )
        val session = InterviewSession("Tos", fake)
        session.advance()
        val pending = async { session.answer("Ayer") }
        runCurrent()
        advanceTimeBy(1_000)
        pending.cancel()
        runCurrent()
        assertEquals(InterviewState.Generating, session.state)
        assertEquals(1, session.turns.size)
        assertEquals(InterviewState.Asking(Dimension.LOCATION, "¿Dónde?", 2), session.advance())
        assertEquals(listOf(1, 1), fake.calls.drop(1).map { it.size })
    }
}
