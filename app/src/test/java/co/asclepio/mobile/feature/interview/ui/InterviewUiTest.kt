package co.asclepio.mobile.feature.interview.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.core.interview.UrgentSubmission
import co.asclepio.mobile.feature.interview.model.ModelDownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** UI de la entrevista y del asistente (T9): textos del catálogo, estados de descarga y cableado de la raíz. */
class InterviewUiTest {
    private fun strings(folder: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/interview.xml"))
        val nodes = doc.documentElement.getElementsByTagName("string")
        return (0 until nodes.length).associate { (nodes.item(it) as Element).let { e -> e.getAttribute("name") to e.textContent } }
    }

    private val navigation = File("src/main/java/co/asclepio/mobile/AppNavigation.kt").readText()

    @Test fun `R12 la pantalla de alarma dice Acude ya a urgencias o llama al 123 en los cinco idiomas`() {
        assertEquals("Acude ya a urgencias o llama al 123", strings("values").getValue("red_flag_title"))
        for (folder in listOf("values-en", "values-pt", "values-fr", "values-de")) assertTrue(folder, strings(folder).getValue("red_flag_title").contains("123"))
        val screen = File("src/main/java/co/asclepio/mobile/feature/interview/ui/InterviewScreen.kt").readText()
        assertTrue(screen.contains("tel:123") && screen.contains("LiveRegionMode.Assertive"))
    }

    @Test fun `R6 el aviso del servidor es el texto de PA-F11-3 con Continuar y Omitir asistente`() {
        val es = strings("values")
        assertEquals(
            "En este teléfono el asistente funciona en nuestro servidor: tus respuestas se enviarán para generar las preguntas. No se guardan ni incluyen tu nombre ni tu documento.",
            es.getValue("server_notice_body"),
        )
        assertEquals("Continuar", es.getValue("server_notice_continue"))
        assertEquals("Omitir asistente", es.getValue("server_notice_skip"))
    }

    @Test fun `R8 la pregunta tiene No se, Terminar y Siguiente y el slot de dictado de F-08`() {
        val es = strings("values")
        assertEquals(listOf("No sé", "Terminar", "Siguiente"), listOf("interview_dont_know", "interview_finish", "interview_next").map(es::getValue))
        assertTrue(navigation.contains("DictationControls(") && navigation.contains("vm::startDictation"))
    }

    @Test fun `R5 el estado de la descarga muestra porcentaje y MB y permite cancelar y borrar`() {
        val downloading = assistantView(ModelDownloadState.Downloading(1_294_000_000, 2_588_000_000), enoughMemory = true)
        assertEquals(R.string.assistant_downloading, downloading.text)
        assertEquals(listOf(50L, 1_294L, 2_588L), downloading.args)
        assertTrue(downloading.canCancel && !downloading.canDownload)
        val ready = assistantView(ModelDownloadState.Verified, true)
        assertTrue(ready.canDelete && !ready.canCancel)
        assertTrue(assistantView(ModelDownloadState.Waiting(0, 10), true).canCancel)
        assertEquals(listOf(2L), assistantView(ModelDownloadState.NotEnoughSpace(1_500_000), true).args)
        assertTrue(assistantView(ModelDownloadState.Corrupted, true).canDownload)
        val noRam = assistantView(ModelDownloadState.NotDownloaded, enoughMemory = false)
        assertEquals(R.string.assistant_not_capable, noRam.text)
        assertFalse(noRam.canDownload)
        assertTrue(navigation.contains("assistant = { AssistantSection(container) }"))
    }

    @Test fun `R13 la raiz lanza el envio de alarma ligado a la pantalla y R17 cancela y regenera con el ciclo de vida`() {
        assertTrue(navigation.contains("is InterviewUiState.RedFlag -> LaunchedEffect(s.interview) { preRegistration.sendUrgent(s.interview) }"))
        assertTrue(navigation.contains("LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }"))
        assertTrue(navigation.contains("LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }"))
        assertTrue(navigation.contains("viewModel(key = \"interview-\$attempt\")"))
    }

    @Test fun `R14 el resumen confirma con el boton de envio y muestra el error del prerregistro`() {
        assertTrue(navigation.contains("preRegistration.submitWithInterview(decision.interview)"))
        assertTrue(navigation.contains("summaryMessage = form.message"))
        assertEquals("Revisa tus respuestas", strings("values").getValue("summary_title"))
    }

    private val redFlag = InterviewUiState.RedFlag(
        co.asclepio.mobile.core.interview.SymptomInterviewDto(
            co.asclepio.mobile.core.interview.InterviewEngine.ON_DEVICE, "m", emptyList(),
            listOf(co.asclepio.mobile.core.interview.RedFlag.CHEST_PAIN), co.asclepio.mobile.core.interview.EndedBy.RED_FLAG,
        ),
    )

    @Test fun `R13 en la alarma atras y Volver quedan bloqueados mientras el envio esta pendiente o reintentando`() {
        assertTrue(redFlagBlocksBack(redFlag, null))
        assertTrue(redFlagBlocksBack(redFlag, UrgentSubmission.Sending))
        assertTrue(redFlagBlocksBack(redFlag, UrgentSubmission.Retrying(4)))
        // Tras enviarse (o un error definitivo sin reintento) se puede salir con normalidad.
        assertFalse(redFlagBlocksBack(redFlag, UrgentSubmission.Sent))
        assertFalse(redFlagBlocksBack(redFlag, UrgentSubmission.Failed(co.asclepio.mobile.core.ui.UiMessage(R.string.error_general))))
        // Fuera de la alarma no se bloquea nada.
        assertFalse(redFlagBlocksBack(InterviewUiState.Preparing, UrgentSubmission.Sending))
        assertFalse(redFlagBlocksBack(InterviewUiState.Asking("¿Cuándo?", 1), null))
    }

    @Test fun `R12 la raiz intercepta el gesto atras y oculta Volver con la alarma pendiente sin cancelar el envio`() {
        assertTrue(navigation.contains("BackHandler(enabled = locked) {}"))
        assertTrue(navigation.contains("if (backStack.size > 1 && !backLocked)"))
        assertTrue(navigation.contains("onBack = { if (backStack.size > 1 && !backLocked) backStack.removeAt(backStack.lastIndex) }"))
        // El envío sigue ligado a la pantalla de alarma (no a la pulsación de atrás).
        assertTrue(navigation.contains("is InterviewUiState.RedFlag -> LaunchedEffect(s.interview) { preRegistration.sendUrgent(s.interview) }"))
    }
}
