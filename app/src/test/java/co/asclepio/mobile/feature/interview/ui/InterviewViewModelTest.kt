package co.asclepio.mobile.feature.interview.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.core.interview.Dimension
import co.asclepio.mobile.core.interview.EndedBy
import co.asclepio.mobile.core.interview.FakeInterviewer
import co.asclepio.mobile.core.interview.FakeInterviewer.Companion.output
import co.asclepio.mobile.core.interview.FakeInterviewer.Companion.slow
import co.asclepio.mobile.core.interview.FakeInterviewer.Companion.step
import co.asclepio.mobile.core.interview.FakeInterviewer.Companion.unavailable
import co.asclepio.mobile.core.interview.InterviewEngine
import co.asclepio.mobile.core.interview.NextStep
import co.asclepio.mobile.core.interview.RedFlag
import co.asclepio.mobile.core.interview.SymptomInterviewer
import co.asclepio.mobile.core.interview.Turn
import co.asclepio.mobile.core.interview.UrgentSubmission
import co.asclepio.mobile.feature.interview.DeviceCapability
import co.asclepio.mobile.feature.interview.PreparableInterviewer
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** ViewModel de la entrevista con motores falsos (T9, T10): selección del motor, botones, resumen, alarma y ciclo de vida. */
class InterviewViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeLocal(private val delegate: FakeInterviewer, private val initMs: Long = 0, private val fail: Boolean = false) :
        PreparableInterviewer, SymptomInterviewer by delegate {
        var initialized = false
        override suspend fun initialize() {
            delay(initMs)
            if (fail) error("GPU")
            initialized = true
        }
        val closed get() = delegate.closed
    }

    private var noticeSeen = false
    private var downloads = 0
    private val gib = 1024L * 1024 * 1024

    private fun vm(
        local: FakeLocal? = null,
        server: FakeInterviewer = FakeInterviewer(InterviewEngine.SERVER),
        mem: Long = 8 * gib,
        model: Boolean = local != null,
        complaint: String = "Me siento mal",
    ) = InterviewViewModel(
        complaint,
        InterviewDependencies(
            capability = DeviceCapability({ mem }, { model }),
            noticeSeen = { noticeSeen },
            markNoticeSeen = { noticeSeen = true },
            localInterviewer = { checkNotNull(local) },
            serverInterviewer = { server },
            downloadModel = { downloads++ },
            modelVerified = { model },
        ),
    )

    private fun InterviewViewModel.asking() = state.value as InterviewUiState.Asking

    @Test fun `R6 con equipo capaz usa el motor local y no llama al servidor`() = runTest {
        val local = FakeLocal(FakeInterviewer(script = arrayOf(output("¿Cuándo empezó?", Dimension.ONSET))))
        val server = FakeInterviewer(InterviewEngine.SERVER)
        val vm = vm(local, server)
        advanceUntilIdle()
        assertTrue(local.initialized)
        assertEquals("¿Cuándo empezó?", vm.asking().question)
        assertEquals(0, server.calls.size)
    }

    @Test fun `R6 si el Engine no arranca en 20 s se muestra el aviso del servidor y Continuar lo marca visto`() = runTest {
        val local = FakeLocal(FakeInterviewer(), initMs = 25_000)
        val server = FakeInterviewer(InterviewEngine.SERVER, script = arrayOf(step(NextStep.Question(Dimension.ONSET, "¿Cuándo empezó?"))))
        val vm = vm(local, server)
        advanceUntilIdle()
        assertEquals(InterviewUiState.ServerNotice(canDownload = false), vm.state.value)
        assertTrue(local.closed)
        vm.acceptServerNotice()
        advanceUntilIdle()
        assertTrue(noticeSeen)
        assertEquals("¿Cuándo empezó?", vm.asking().question)
    }

    @Test fun `R6 con el aviso ya visto se usa el servidor sin volver a mostrarlo`() = runTest {
        noticeSeen = true
        val server = FakeInterviewer(InterviewEngine.SERVER, script = arrayOf(step(NextStep.Question(Dimension.ONSET, "¿Cuándo empezó?"))))
        val vm = vm(server = server, mem = 4 * gib)
        advanceUntilIdle()
        assertEquals(1, server.calls.size)
        assertEquals(1, vm.asking().number)
    }

    @Test fun `R6 Omitir asistente no llama a ningun motor y R1 el aviso ofrece descargar con RAM suficiente`() = runTest {
        val server = FakeInterviewer(InterviewEngine.SERVER)
        val vm = vm(server = server, model = false)
        advanceUntilIdle()
        assertEquals(InterviewUiState.ServerNotice(canDownload = true), vm.state.value)
        vm.downloadModel()
        assertEquals(1, downloads)
        assertEquals(InterviewUiState.ServerNotice(canDownload = true, downloadRequested = true), vm.state.value)
        vm.skip()
        assertEquals(InterviewUiState.Skipped, vm.state.value)
        assertEquals(0, server.calls.size)
    }

    @Test fun `R8 Siguiente exige texto, No se envia null y la pregunta siguiente llega validada`() = runTest {
        noticeSeen = true
        val server = FakeInterviewer(
            InterviewEngine.SERVER,
            script = arrayOf(step(NextStep.Question(Dimension.ONSET, "¿Cuándo empezó?")), step(NextStep.Question(Dimension.LOCATION, "¿Dónde?")), step(NextStep.Question(Dimension.INTENSITY, "¿Es grave?"))),
        )
        val vm = vm(server = server)
        advanceUntilIdle()
        assertFalse(vm.asking().canSend)
        vm.next()
        assertEquals(1, server.calls.size)
        vm.updateAnswer("Ayer en la tarde")
        vm.next()
        advanceUntilIdle()
        assertEquals(InterviewUiState.Asking("¿Dónde?", 2), vm.state.value)
        vm.dontKnow()
        advanceUntilIdle()
        assertEquals(listOf(Turn(Dimension.ONSET, "¿Cuándo empezó?", "Ayer en la tarde"), Turn(Dimension.LOCATION, "¿Dónde?", null)), server.calls[2])
        // R10: "¿Es grave?" no pasa el validador: se muestra la pregunta fija.
        assertEquals("¿Los síntomas son continuos o van y vienen?", vm.asking().question)
    }

    @Test fun `R12 una respuesta con senal corta la entrevista sin llamar al motor y libera el Engine`() = runTest {
        val local = FakeLocal(FakeInterviewer(script = arrayOf(output("¿Cuándo empezó?", Dimension.ONSET))))
        val vm = vm(local)
        advanceUntilIdle()
        vm.updateAnswer("Hace una hora, y tengo dolor en el pecho")
        vm.next()
        advanceUntilIdle()
        val red = vm.state.value as InterviewUiState.RedFlag
        assertEquals(EndedBy.RED_FLAG, red.interview.endedBy)
        assertEquals(listOf(RedFlag.CHEST_PAIN), red.interview.redFlags)
        assertEquals(InterviewEngine.ON_DEVICE, red.interview.engine)
        assertTrue(local.closed)
    }

    @Test fun `R14 Terminar muestra el resumen editable y confirmar envia las respuestas editadas`() = runTest {
        val local = FakeLocal(FakeInterviewer(script = arrayOf(output("¿Cuándo empezó?", Dimension.ONSET), output("¿Dónde?", Dimension.LOCATION))))
        val vm = vm(local)
        advanceUntilIdle()
        vm.updateAnswer("Ayer")
        vm.next()
        advanceUntilIdle()
        vm.finish()
        val summary = vm.state.value as InterviewUiState.Summary
        assertEquals(EndedBy.PATIENT, summary.result.endedBy)
        assertTrue(local.closed)
        vm.editAnswer(0, "Antier en la noche")
        val decision = vm.confirmSummary() as SummaryDecision.Send
        assertEquals(listOf(Turn(Dimension.ONSET, "¿Cuándo empezó?", "Antier en la noche")), decision.interview!!.turns)
        assertEquals(EndedBy.PATIENT, decision.interview!!.endedBy)
    }

    @Test fun `R12 una respuesta editada en el resumen con senal escala a la pantalla de alarma`() = runTest {
        val local = FakeLocal(FakeInterviewer(script = arrayOf(output("¿Cuándo empezó?", Dimension.ONSET), output("¿Dónde?", Dimension.LOCATION))))
        val vm = vm(local)
        advanceUntilIdle()
        vm.updateAnswer("Ayer")
        vm.next()
        advanceUntilIdle()
        vm.finish()
        vm.editAnswer(0, "Ayer y me desmayé")
        assertEquals(SummaryDecision.Escalated, vm.confirmSummary())
        val red = vm.state.value as InterviewUiState.RedFlag
        assertEquals(listOf(RedFlag.LOSS_OF_CONSCIOUSNESS), red.interview.redFlags)
    }

    @Test fun `R7 el servidor no disponible sin turnos deja enviar el prerregistro sin entrevista`() = runTest {
        noticeSeen = true
        val vm = vm(server = FakeInterviewer(InterviewEngine.SERVER, script = arrayOf(unavailable)))
        advanceUntilIdle()
        val summary = vm.state.value as InterviewUiState.Summary
        assertTrue(summary.result.assistantUnavailable)
        assertEquals(SummaryDecision.Send(null), vm.confirmSummary())
    }

    @Test fun `R17 ON_STOP cancela la generacion, conserva los turnos y al volver se regenera la pregunta`() = runTest {
        val delegate = FakeInterviewer(script = arrayOf(output("¿Cuándo empezó?", Dimension.ONSET), slow(10_000), output("¿Dónde?", Dimension.LOCATION)))
        val vm = vm(FakeLocal(delegate))
        advanceUntilIdle()
        vm.updateAnswer("Ayer")
        vm.next()
        runCurrent()
        advanceTimeBy(1_000)
        vm.onStop()
        advanceUntilIdle()
        assertTrue(vm.asking().generating)
        vm.onStart()
        advanceUntilIdle()
        assertEquals(InterviewUiState.Asking("¿Dónde?", 2), vm.state.value)
        assertEquals(listOf(1, 1), delegate.calls.drop(1).map { it.size })
    }

    @Test fun `R17 al salir de la entrevista se libera el Engine`() = runTest {
        val local = FakeLocal(FakeInterviewer(script = arrayOf(output("¿Cuándo empezó?", Dimension.ONSET))))
        val vm = vm(local)
        advanceUntilIdle()
        assertFalse(local.closed)
        vm.clearForTest()
        assertTrue(local.closed)
    }

    @Test fun `R12 el texto del estado del envio de alarma sale del catalogo`() {
        assertEquals(co.asclepio.mobile.R.string.red_flag_sending, urgentStatusText(UrgentSubmission.Sending))
        assertEquals(co.asclepio.mobile.R.string.red_flag_retrying, urgentStatusText(UrgentSubmission.Retrying(4)))
        assertEquals(co.asclepio.mobile.R.string.red_flag_sent, urgentStatusText(UrgentSubmission.Sent))
    }
}

/** Limpia el ViewModel como el ViewModelStore de la entrada al salir de la pantalla. */
private fun InterviewViewModel.clearForTest() {
    val store = androidx.lifecycle.ViewModelStore()
    val created = this
    val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = created as T
    }
    androidx.lifecycle.ViewModelProvider(store, factory)[InterviewViewModel::class.java]
    store.clear()
}
