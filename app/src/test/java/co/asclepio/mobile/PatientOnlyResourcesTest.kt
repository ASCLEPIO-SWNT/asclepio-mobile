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
}
