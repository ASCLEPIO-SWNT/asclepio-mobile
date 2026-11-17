package co.asclepio.mobile.feature.voice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADR 0006: el audio nunca sale del dispositivo. Prueba estática sobre las fuentes de producción (`src/main`). */
class OnDeviceOnlyTest {
    private val main = File("src/main/java/co/asclepio/mobile")
    private val adapter = "feature/voice/AndroidOnDeviceRecognizer.kt"

    private fun sources(): Map<String, String> {
        assertTrue("Falta $main", main.isDirectory)
        return main.walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { it.relativeTo(main).path to it.readText() }
    }

    private fun filesContaining(token: String): Set<String> = sources().filterValues { it.contains(token) }.keys

    @Test fun `R2 solo AndroidOnDeviceRecognizer crea el reconocedor local y usa RecognizerIntent`() {
        assertEquals(setOf(adapter), filesContaining("createOnDeviceSpeechRecognizer"))
        assertEquals(setOf(adapter), filesContaining("RecognizerIntent"))
        val text = sources().getValue(adapter)
        // Cada llamada a createOnDeviceSpeechRecognizer va detrás de la comprobación del servicio local en la misma expresión.
        val creations = text.lines().filter { it.contains("SpeechRecognizer.createOnDeviceSpeechRecognizer(") }
        assertTrue(creations.isNotEmpty() && creations.all { it.contains("if (!serviceAvailable()) null else") })
        assertTrue(Regex("""fun serviceAvailable\(\)[^\n]*SpeechRecognizer\.isOnDeviceRecognitionAvailable\(context\)""").containsMatchIn(text))
        for (required in listOf("setRecognitionListener", "destroy()", "RecognizerIntent.EXTRA_PARTIAL_RESULTS, false", "RecognizerIntent.EXTRA_MAX_RESULTS, 1", "mainExecutor")) {
            assertTrue("$adapter debe usar $required", text.contains(required))
        }
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""<action android:name="android.speech.RecognitionService" />"""))
    }

    @Test fun `R2 ninguna fuente usa createSpeechRecognizer actividades de reconocimiento grabacion ni descarga de modelos`() {
        val forbidden = listOf(
            "createSpeechRecognizer(", "startActivity", "registerForActivityResult(RecognizerIntent", "ACTION_RECOGNIZE_SPEECH)",
            "MediaRecorder", "AudioRecord", "synthesizeToFile", "triggerModelDownload", "EXTRA_PREFER_OFFLINE",
        )
        val offenders = sources().flatMap { (file, text) -> forbidden.filter { text.contains(it) && !(it == "ACTION_RECOGNIZE_SPEECH)" && file == adapter) }.map { "$file -> $it" } }
        assertEquals(emptyList<String>(), offenders)
        // El intent de reconocimiento solo se pasa al reconocedor local, nunca a una actividad.
        assertTrue(sources().values.none { Regex("""startActivity\w*\(\s*Intent\(RecognizerIntent""").containsMatchIn(it) })
    }

    @Test fun `R12 los archivos del dictado no usan SavedStateHandle DataStore ni Log`() {
        val dictation = sources().filterKeys {
            it.startsWith("core/voice/") || it.startsWith("feature/voice/") || it == "feature/encounters/ui/PreRegistrationViewModel.kt"
        }
        assertTrue(dictation.keys.toString(), adapter in dictation && "core/voice/OnDeviceRecognizer.kt" in dictation)
        val forbidden = listOf(
            Regex("""SavedStateHandle"""), Regex("""(?i)datastore"""), Regex("""SharedPreferences"""), Regex("""\bLog\."""),
            Regex("""\bprintln\("""), Regex("""FileOutputStream|openFileOutput|writeText\("""), Regex("""rememberSaveable"""),
        )
        val offenders = dictation.flatMap { (file, text) -> forbidden.filter { it.containsMatchIn(text) }.map { "$file -> ${it.pattern}" } }
        assertEquals(emptyList<String>(), offenders)
    }
}
