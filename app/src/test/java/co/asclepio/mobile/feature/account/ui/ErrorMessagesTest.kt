package co.asclepio.mobile.feature.account.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.FieldError
import co.asclepio.mobile.core.network.ProblemDetails
import co.asclepio.mobile.core.ui.UiMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ErrorMessagesTest {
    private fun http(status: Int, vararg fields: String, retryAfter: Int? = null) =
        ApiError.Http(
            ProblemDetails(
                status = status,
                title = "Título de la API",
                detail = "Detalle de la API",
                errors = fields.map { FieldError(it, "mensaje de la API") },
                retryAfterSeconds = retryAfter,
            ),
        )

    private val allErrors: List<ApiError> = listOf(
        ApiError.Network, ApiError.InvalidBody,
        http(400, "documentNumber"), http(400), http(401), http(404), http(409), http(422),
        http(429, retryAfter = 30), http(429), http(500), http(503),
    )

    private val general = UiMessage(R.string.error_general)

    @Test fun `R34 cada error de cada pantalla se traduce a un id de R string sin textos de la API`() {
        val f01Strings = setOf(
            R.string.error_network, R.string.error_general, R.string.error_too_many_requests,
            R.string.error_login_invalid, R.string.error_signup_conflict, R.string.error_signup_unprocessable,
            R.string.error_required, R.string.error_max_length, R.string.error_password_length,
        )
        for (error in allErrors) {
            val messages = listOfNotNull(
                tenantsErrorMessage(error),
                consentErrorMessage(error),
                loginErrorMessage(error),
                meErrorMessage(error),
                signUpErrorMessage(error).message,
            ) + signUpErrorMessage(error).fieldErrors.values
            for (m in messages) {
                assertTrue("$error → $m", m.resId in f01Strings)
                assertTrue(m.args.all { it is Int })
            }
        }
        assertEquals(UiMessage(R.string.error_not_patient), notPatientMessage)
    }

    @Test fun `R34 los errores de validacion se traducen a ids de R string`() {
        val ids = setOf(
            R.string.error_required, R.string.error_max_length, R.string.error_password_length,
            R.string.error_password_mismatch, R.string.error_underage,
        )
        val clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-26T17:00:00Z"), java.time.ZoneOffset.UTC)
        val messages = validateSignUp(SignUpForm(documentNumber = "1".repeat(70), passwordConfirmation = "x"), clock).values +
            validateSignUp(SignUpForm(birthDate = java.time.LocalDate.parse("2020-01-01")), clock).values +
            validateLogin(LoginForm()).values
        assertTrue(messages.isNotEmpty())
        messages.forEach { assertTrue("$it", it.resId in ids) }
    }

    @Test fun `R18 un 401 en el login es el mensaje generico de credenciales`() {
        assertEquals(UiMessage(R.string.error_login_invalid), loginErrorMessage(http(401)))
    }

    @Test fun `R21 un 401 de me tras el login se muestra como error general`() {
        assertEquals(general, meErrorMessage(http(401)))
    }

    @Test fun `R13 un 400 marca cada campo del formulario que aparece en errors con su mensaje de la app`() {
        val result = signUpErrorMessage(http(400, "documentType", "documentNumber", "fullName", "birthDate", "sex", "password"))
        assertNull(result.message)
        assertEquals(
            mapOf(
                SignUpField.DocumentType to UiMessage(R.string.error_required),
                SignUpField.DocumentNumber to UiMessage(R.string.error_max_length, listOf(64)),
                SignUpField.FullName to UiMessage(R.string.error_max_length, listOf(200)),
                SignUpField.BirthDate to UiMessage(R.string.error_required),
                SignUpField.Sex to UiMessage(R.string.error_required),
                SignUpField.Password to UiMessage(R.string.error_password_length),
            ),
            result.fieldErrors,
        )
    }

    @Test fun `R13 un 400 sin campos del formulario o sin errors muestra el mensaje general`() {
        for (error in listOf(http(400), http(400, "consentAccepted", "consentVersion", "tenantId", "otro"))) {
            val result = signUpErrorMessage(error)
            assertEquals(general, result.message)
            assertTrue(result.fieldErrors.isEmpty())
        }
    }

    @Test fun `R14 un 409 en el alta es el mensaje de cuenta existente`() {
        assertEquals(UiMessage(R.string.error_signup_conflict), signUpErrorMessage(http(409)).message)
    }

    @Test fun `R15 un 422 en el alta es el mensaje de fecha o consentimiento`() {
        assertEquals(UiMessage(R.string.error_signup_unprocessable), signUpErrorMessage(http(422)).message)
    }

    @Test fun `R34 strings xml contiene exactamente los textos de la tabla de PA-1 y PA-6`() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        val strings = (0 until nodes.length).associate {
            val n = nodes.item(it)
            n.attributes.getNamedItem("name").nodeValue to n.textContent.replace(Regex("%\\d\\$"), "%")
        }
        val expected = mapOf(
            "error_no_tenants" to "No hay IPS disponibles en este momento. Intenta más tarde.",
            "tenant_no_sites" to "Sin sedes disponibles",
            "error_network" to "No hay conexión con el servidor. Revisa tu internet e intenta de nuevo.",
            "error_general" to "Algo salió mal. Intenta de nuevo en unos minutos.",
            "error_too_many_requests" to "Demasiados intentos. Espera %d segundos e intenta de nuevo.",
            "error_login_invalid" to "Documento o contraseña incorrectos. Después de 5 intentos fallidos la cuenta se bloquea 15 minutos.",
            "error_signup_conflict" to "Ya existe una cuenta con este documento en esta IPS. Inicia sesión.",
            "error_signup_unprocessable" to "No pudimos crear la cuenta. Revisa tu fecha de nacimiento y vuelve a aceptar el consentimiento si cambió.",
            "error_not_patient" to "Esta cuenta no es de paciente. Usa la aplicación web del personal.",
            "error_required" to "Campo obligatorio",
            "error_max_length" to "Máximo %d caracteres",
            "error_password_length" to "La contraseña debe tener entre 10 y 128 caracteres",
            "error_password_mismatch" to "Las contraseñas no coinciden",
            "error_underage" to "Debes ser mayor de 18 años para crear una cuenta",
            "document_type_cc" to "Cédula de ciudadanía",
            "document_type_ce" to "Cédula de extranjería",
            "document_type_pa" to "Pasaporte",
            "sex_m" to "Masculino",
            "sex_f" to "Femenino",
            "sex_x" to "Indeterminado / otro",
            "home_identity_unverified" to "Tu identidad se verificará en recepción",
            "home_preregister_soon" to "Pronto podrás pre-registrarte desde aquí",
            "logout_dialog_title" to "¿Cerrar sesión?",
            "logout_cancel" to "Cancelar",
            "logout_confirm" to "Cerrar sesión",
        )
        for ((name, text) in expected) assertEquals(name, text, strings[name])
    }

    @Test fun `R34 los Composables y ViewModels de la feature no tienen textos literales de UI`() {
        val dirs = listOf("src/main/java/co/asclepio/mobile/feature/account/ui", "src/main/java/co/asclepio/mobile/core/ui")
        val literal = Regex("""(Text\(\s*"|text\s*=\s*"|label\s*=\s*\{\s*Text\(\s*"|stringResource\(\s*")""")
        val offenders = dirs.flatMap { File(it).walkTopDown().filter { f -> f.extension == "kt" }.toList() }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (literal.containsMatchIn(l)) "${f.name}:${i + 1}" else null } }
        assertTrue(offenders.toString(), offenders.isEmpty())
        assertTrue(File(dirs[0]).walkTopDown().any { it.name.endsWith("Screen.kt") })
    }
}
