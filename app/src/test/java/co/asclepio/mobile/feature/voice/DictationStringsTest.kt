package co.asclepio.mobile.feature.voice

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Textos del dictado (F-08-mobile) en los cinco catálogos de la app. */
class DictationStringsTest {
    private val resources = File("src/main/res")
    private val folders = listOf("values", "values-en", "values-pt", "values-fr", "values-de")

    private val keys = setOf(
        "dictation_start", "dictation_stop", "dictation_cancel", "dictation_listening", "dictation_processing",
        "dictation_spanish_only", "dictation_unavailable", "dictation_permission_denied", "dictation_no_match",
        "dictation_busy", "dictation_error", "dictation_cancelled",
    )

    private fun strings(folder: String): Map<String, String> =
        File(resources, folder).listFiles { file -> file.extension == "xml" }.orEmpty().flatMap { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.getElementsByTagName("string")
            (0 until nodes.length).map { (nodes.item(it) as Element).let { e -> e.getAttribute("name") to e.textContent } }
        }.toMap()

    @Test fun `R14 los textos del dictado existen en los cinco idiomas`() {
        for (folder in folders) {
            val catalog = strings(folder)
            assertEquals(folder, keys, catalog.keys.filter { it.startsWith("dictation_") }.toSet())
            for (key in keys) assertTrue("$folder/$key vacío", catalog.getValue(key).isNotBlank())
        }
        // La nota indica en cada idioma de la interfaz que el dictado reconoce solo español.
        val spanish = mapOf("values" to "español", "values-en" to "Spanish", "values-pt" to "espanhol", "values-fr" to "espagnol", "values-de" to "Spanisch")
        for ((folder, word) in spanish) assertTrue(folder, strings(folder).getValue("dictation_spanish_only").contains(word))
        assertEquals("Dictar", strings("values").getValue("dictation_start"))
        assertEquals("Escuchando…", strings("values").getValue("dictation_listening"))
        assertEquals("Procesando…", strings("values").getValue("dictation_processing"))
    }

    @Test fun `R14 DictationControls no tiene textos literales de UI`() {
        val controls = File("src/main/java/co/asclepio/mobile/feature/voice/ui/DictationControls.kt")
        assertTrue("Falta $controls", controls.isFile)
        val literal = Regex("""(Text\(\s*"|text\s*=\s*"|contentDescription\s*=\s*"|stringResource\(\s*"|UiMessage\(\s*")""")
        val offenders = controls.readLines().mapIndexedNotNull { i, line -> if (literal.containsMatchIn(line)) "${i + 1}: $line" else null }
        assertEquals(emptyList<String>(), offenders)
        for (key in keys - setOf("dictation_permission_denied", "dictation_no_match", "dictation_busy", "dictation_error", "dictation_cancelled")) {
            assertTrue("DictationControls debe usar $key", controls.readText().contains("R.string.$key"))
        }
    }
}
