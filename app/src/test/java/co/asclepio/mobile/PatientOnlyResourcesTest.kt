package co.asclepio.mobile

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Recursos de la app solo del paciente (DT-01-mobile): textos de rechazo y ausencia de lo retirado. */
class PatientOnlyResourcesTest {
    private val resources = File("src/main/res")
    private val folders = listOf("values", "values-en", "values-pt", "values-fr", "values-de")

    /** Todas las cadenas `<string>` de una carpeta `values*`, por nombre. */
    private fun strings(folder: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        File(resources, folder).listFiles { file -> file.extension == "xml" }.orEmpty().forEach { file ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = document.documentElement.getElementsByTagName("string")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                result[element.getAttribute("name")] = element.textContent
            }
        }
        return result
    }

    @Test fun `R7 el rechazo de una cuenta que no es de paciente remite a la web del personal en los cinco idiomas`() {
        assertEquals("Esta cuenta no es de paciente. Usa la aplicación web del personal.", strings("values").getValue("error_not_patient"))
        val expected = mapOf(
            "values-en" to "This account is not a patient account. Use the staff web application.",
            "values-pt" to "Esta conta não é de paciente. Use o aplicativo web da equipe.",
            "values-fr" to "Ce compte n’est pas un compte patient. Utilisez l’application web du personnel.",
            "values-de" to "Dieses Konto ist kein Patientenkonto. Verwenden Sie die Webanwendung für das Personal.",
        )
        expected.forEach { (folder, text) -> assertEquals(folder, text, strings(folder).getValue("error_not_patient")) }
    }

    @Test fun `R7 ningun idioma conserva el texto retirado que indicaba un acceso de personal en la app`() {
        val retired = listOf(
            "Esta cuenta es de personal. Vuelve y elige Acceso del personal.",
            "This is a staff account. Go back and choose Staff access.",
            "Esta conta é da equipe. Volte e escolha Acesso da equipe.",
            "Ce compte est un compte du personnel. Revenez en arrière et choisissez Accès du personnel.",
            "Dies ist ein Personalkonto. Gehen Sie zurück und wählen Sie Personalzugang.",
        )
        val accessHints = listOf("Acceso del personal", "Staff access", "Acesso da equipe", "Accès du personnel", "Personalzugang")
        for (folder in folders) {
            val values = strings(folder).values
            assertTrue(folder, values.none { it in retired })
            assertTrue(folder, values.none { value -> accessHints.any { value.contains(it) } })
        }
    }

    private val main = File("src/main/java/co/asclepio/mobile")

    @Test fun `R9 las fuentes de produccion no contienen los paquetes ni archivos de personal`() {
        val retired = listOf(
            "feature/staffaccount", "feature/staffencounters", "feature/reception", "feature/triage", "feature/dashboards",
            "core/encounters", "core/auth/StaffDestinations.kt", "core/ui/DashboardComponents.kt",
            "core/ui/DashboardDateTime.kt", "core/ui/DashboardRead.kt",
        )
        assertTrue("Falta $main", main.isDirectory)
        assertEquals(emptyList<String>(), retired.filter { File(main, it).exists() })
        val packages = listOf("feature.staffaccount", "feature.staffencounters", "feature.reception", "feature.triage", "feature.dashboards", "core.encounters")
        val references = main.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            val text = file.readText()
            packages.filter { text.contains("co.asclepio.mobile.$it") }.map { "${file.relativeTo(main)} -> $it" }
        }.toList()
        assertEquals(emptyList<String>(), references)
        val container = File(main, "AppContainer.kt").readText()
        val staffDependencies = listOf("Staff", "Reception", "Dashboard", "ClinicalCapture", "EncountersApi", "EncountersRepository")
        assertEquals(emptyList<String>(), staffDependencies.filter { container.contains(it) })
    }

    @Test fun `R10 los cinco catalogos no contienen archivos claves ni drawables de personal y conservan el resto`() {
        val retiredFiles = listOf(
            "staff_encounters.xml", "staff_reception.xml", "staff_registration.xml", "staff_voice_guide.xml",
            "dashboards.xml", "clinical_capture.xml", "clinical_guides.xml",
        )
        val retiredKeys = setOf(
            "access_choose", "access_patient", "access_patient_intro", "access_patient_action", "access_staff",
            "access_staff_intro", "access_staff_action", "change_access", "change_access_dialog_title", "change_access_dialog_body",
        )
        val prefixes = listOf("staff_", "dash_", "clinical_", "guide_staff_", "guide_welcome_")
        val base = names("values")
        for (folder in folders) {
            assertEquals(folder, emptyList<String>(), retiredFiles.filter { File(File(resources, folder), it).exists() })
            val keys = names(folder)
            assertEquals(folder, emptyList<String>(), keys.filter { key -> key in retiredKeys || prefixes.any { key.startsWith(it) } })
            assertEquals(folder, base, keys)
        }
        for (kept in listOf("access_denied", "access_go_home", "session_verification_title", "navigate_back", "error_not_patient")) {
            assertTrue(kept, kept in base)
        }
        val drawables = File(resources, "drawable").list().orEmpty().filter { it.startsWith("ic_dashboard_") }
        assertEquals(emptyList<String>(), drawables)
    }

    /** Nombres de todos los recursos de texto (string, plurals, string-array) de una carpeta `values*`. */
    private fun names(folder: String): Set<String> = File(resources, folder).listFiles { file -> file.extension == "xml" }.orEmpty().flatMap { file ->
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        listOf("string", "plurals", "string-array").flatMap { tag ->
            val nodes = root.getElementsByTagName(tag)
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("name") }
        }
    }.toSet()
}
