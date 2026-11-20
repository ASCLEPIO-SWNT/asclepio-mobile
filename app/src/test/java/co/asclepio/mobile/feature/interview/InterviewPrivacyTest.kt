package co.asclepio.mobile.feature.interview

import co.asclepio.mobile.core.interview.Dimension
import co.asclepio.mobile.core.interview.EndedBy
import co.asclepio.mobile.core.interview.InterviewEngine
import co.asclepio.mobile.core.interview.InterviewResult
import co.asclepio.mobile.core.interview.NextStep
import co.asclepio.mobile.core.interview.SymptomInterviewDto
import co.asclepio.mobile.core.interview.Turn
import co.asclepio.mobile.feature.encounters.data.ComplaintSource
import co.asclepio.mobile.feature.encounters.data.CreatePreRegistrationRequest
import co.asclepio.mobile.feature.interview.data.NextQuestionRequest
import co.asclepio.mobile.feature.interview.ui.InterviewUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Pruebas de fuente y de `toString()` de la entrevista (T11): sin red en el motor local y sin contenido en logs. */
class InterviewPrivacyTest {
    private val main = File("src/main/java/co/asclepio/mobile")

    private fun files(vararg dirs: String): Map<String, String> = dirs.flatMap { dir ->
        File(main, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.relativeTo(main).path to it.readText() }.toList()
    }.toMap()

    @Test fun `R15 el motor local y el nucleo de la entrevista no usan red`() {
        val local = files("core/interview") + ("feature/interview/LiteRtInterviewer.kt" to File(main, "feature/interview/LiteRtInterviewer.kt").readText()) +
            ("feature/interview/DeviceCapability.kt" to File(main, "feature/interview/DeviceCapability.kt").readText())
        assertTrue(local.keys.toString(), local.size >= 7)
        val network = listOf("okhttp3", "retrofit2", "java.net", "HttpURLConnection", "Socket", "http://", "https://", "SessionManager", "patientCall")
        val offenders = local.flatMap { (file, text) -> network.filter { text.contains(it) }.map { "$file -> $it" } }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test fun `R15 solo ServerInterviewer llama a next-question y el ViewModel solo lo crea fuera del motor local`() {
        val callers = files("core", "feature").filterValues { it.contains("nextQuestion(") }.keys
        assertEquals(setOf("feature/interview/data/SymptomInterviewApi.kt", "feature/interview/data/ServerInterviewer.kt"), callers)
        val vm = File(main, "feature/interview/ui/InterviewViewModel.kt").readText()
        assertEquals(2, Regex("""deps\.serverInterviewer\(\)""").findAll(vm).count())
    }

    @Test fun `R16 los archivos de la entrevista no escriben logs ni guardan el contenido en disco`() {
        val interview = files("core/interview", "feature/interview")
        assertTrue(interview.size >= 14)
        val forbidden = listOf(
            Regex("""\bLog\."""), Regex("""println\("""), Regex("""printStackTrace"""), Regex("""Timber"""),
            Regex("""SavedStateHandle"""), Regex("""rememberSaveable"""), Regex("""SharedPreferences"""),
            Regex("""openFileOutput|writeText\("""),
        )
        val offenders = interview.flatMap { (file, text) -> forbidden.filter { it.containsMatchIn(text) }.map { "$file -> ${it.pattern}" } }
        assertEquals(emptyList<String>(), offenders)
        // El único DataStore guarda la marca del aviso del servidor (un booleano), nunca contenido.
        val stores = interview.filterValues { it.contains("PreferencesKey(") }
        assertEquals(setOf("feature/interview/data/ServerNoticeStore.kt"), stores.keys)
        assertEquals(listOf("server_notice_seen"), Regex("""PreferencesKey\("([a-z_]+)"\)""").findAll(stores.values.single()).map { it.groupValues[1] }.toList())
    }

    @Test fun `R16 los toString de la entrevista no llevan el motivo, las preguntas ni las respuestas`() {
        val secret = "Dolor secreto"
        val turn = Turn(Dimension.ONSET, "¿$secret?", secret)
        val dto = SymptomInterviewDto(InterviewEngine.SERVER, "m", listOf(turn), emptyList(), EndedBy.PATIENT)
        val texts = listOf(
            turn.toString(), dto.toString(), NextQuestionRequest(secret, listOf(turn)).toString(),
            CreatePreRegistrationRequest("site", secret, ComplaintSource.TYPED, dto).toString(),
            NextStep.Question(Dimension.ONSET, secret).toString(), NextStep.ModelOutput(secret).toString(),
            InterviewResult(InterviewEngine.ON_DEVICE, "m", listOf(turn), emptyList(), EndedBy.PATIENT).toString(),
            InterviewUiState.Asking(secret, 1, secret).toString(),
        )
        texts.forEach { assertFalse(it, it.contains("secreto")) }
    }
}
