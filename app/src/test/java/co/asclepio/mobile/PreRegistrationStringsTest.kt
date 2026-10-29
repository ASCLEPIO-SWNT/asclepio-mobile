package co.asclepio.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Textos de F-02 en strings.xml (tablas de PA-M02-1 y PA-M02-2) y ausencia de textos literales en la UI. */
class PreRegistrationStringsTest {
    private val strings: Map<String, String> by lazy {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        (0 until nodes.length).associate {
            val n = nodes.item(it)
            n.attributes.getNamedItem("name").nodeValue to n.textContent
        }
    }

    @Test fun `R16 el texto Pronto podras pre-registrarte ya no existe en los recursos ni en el inicio`() {
        assertFalse(strings.containsKey("home_preregister_soon"))
        assertFalse(strings.values.any { it.contains("Pronto podrás pre-registrarte") })
        val home = File("src/main/java/co/asclepio/mobile/feature/account/ui/HomeScreen.kt").readText()
        assertFalse(home.contains("home_preregister_soon"))
        assertTrue(home.contains("R.string.home_action_profile"))
    }

    @Test fun `R28 strings xml contiene exactamente los textos de la tabla de PA-M02-1`() {
        val expected = mapOf(
            "home_action_profile" to "Mis datos",
            "home_action_new_preregistration" to "Nuevo preregistro",
            "home_refresh" to "Actualizar",
            "home_no_preregistrations" to "Aún no has enviado ningún preregistro.",
            "home_status_site" to "Sede: %1\$s",
            "home_status_sent" to "Enviado: %1\$s",
            "home_status_expires" to "Si recepción no lo confirma, vence: %1\$s",
            "profile_title" to "Mis datos",
            "profile_locked" to "Tus datos ya fueron verificados en recepción y no se pueden cambiar desde la app.",
            "profile_save" to "Guardar",
            "profile_saved" to "Datos guardados",
            "value_missing" to "—",
            "document_type_cc" to "Cédula de ciudadanía",
            "document_type_ce" to "Cédula de extranjería",
            "document_type_pa" to "Pasaporte",
            "document_type_ti" to "Tarjeta de identidad",
            "document_type_rc" to "Registro civil",
            "preregistration_title" to "Nuevo preregistro",
            "preregistration_site" to "Sede a la que llegas",
            "preregistration_complaint" to "Motivo de consulta",
            "preregistration_counter" to "%1\$d / %2\$d",
            "preregistration_submit" to "Enviar preregistro",
            "preregistration_view_status" to "Ver mi estado",
            "error_no_sites" to "No hay sedes disponibles para tu IPS. Intenta más tarde.",
            "error_site_required" to "Elige una sede",
            "error_preregistration_conflict" to "Ya tienes un preregistro pendiente o una atención en curso.",
            "error_site_unavailable" to "La sede elegida ya no está disponible. Elige otra sede.",
            // Reutilizados de F-01-mobile.
            "error_required" to "Campo obligatorio",
            "error_max_length" to "Máximo %1\$d caracteres",
            "error_network" to "No hay conexión con el servidor. Revisa tu internet e intenta de nuevo.",
            "error_general" to "Algo salió mal. Intenta de nuevo en unos minutos.",
            "error_too_many_requests" to "Demasiados intentos. Espera %1\$d segundos e intenta de nuevo.",
        )
        for ((name, text) in expected) assertEquals(name, text, strings[name])
        assertEquals("Debes ser mayor de 18 años.", strings["error_underage_profile"])
    }

    @Test fun `R28 strings xml contiene exactamente los textos de estado de la tabla de PA-M02-2`() {
        val expected = mapOf(
            "status_pending" to "Pendiente de confirmación. Acércate a recepción para confirmar tu llegada.",
            "status_expired" to "Venció sin confirmarse. Puedes enviar uno nuevo.",
            "status_rejected" to "Recepción no aceptó este preregistro. Puedes enviar uno nuevo o acercarte a recepción.",
            "status_confirmed" to "Llegada confirmada",
            "status_waiting_triage" to "En espera de triage",
            "status_triaged" to "En espera de atención",
            "status_in_care" to "En atención",
            "status_closed" to "Atención cerrada",
            "status_cancelled" to "Atención cancelada",
            "status_unknown" to "Estado no disponible. Intenta actualizar más tarde.",
        )
        for ((name, text) in expected) assertEquals(name, text, strings[name])
    }

    @Test fun `R28 los Composables y ViewModels de patients y encounters no tienen textos literales de UI`() {
        val dirs = listOf(
            "src/main/java/co/asclepio/mobile/feature/patients/ui",
            "src/main/java/co/asclepio/mobile/feature/encounters/ui",
            "src/main/java/co/asclepio/mobile/feature/account/ui/HomeScreen.kt",
        )
        val literal = Regex("""(Text\(\s*"|text\s*=\s*"|label\s*=\s*\{\s*Text\(\s*"|stringResource\(\s*"|UiMessage\(\s*")""")
        val files = dirs.flatMap { File(it).walkTopDown().filter { f -> f.extension == "kt" }.toList() }
        assertTrue(files.count { it.name.endsWith("Screen.kt") || it.name.endsWith("Pane.kt") } >= 3)
        assertTrue(files.count { it.name.endsWith("ViewModel.kt") } >= 3)
        val offenders = files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l -> if (literal.containsMatchIn(l)) "${f.name}:${i + 1}" else null }
        }
        assertTrue(offenders.toString(), offenders.isEmpty())
    }
}
