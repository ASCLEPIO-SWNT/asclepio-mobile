package co.asclepio.mobile.feature.account.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.model.datePickerMillisToLocalDate
import co.asclepio.mobile.core.model.toDatePickerMillis
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.account.data.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ValidationTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-26T17:00:00Z"), ZoneOffset.UTC)
    private val required = UiMessage(R.string.error_required)
    private val passwordLength = UiMessage(R.string.error_password_length)

    private val valid = SignUpForm(
        documentType = DocumentType.CC,
        documentNumber = "  DEV-123 ",
        fullName = " DEV Paciente ",
        birthDate = LocalDate.parse("1990-05-17"),
        sex = Sex.F,
        password = "secreta-123",
        passwordConfirmation = "secreta-123",
        consentAccepted = true,
    )

    @Test fun `R11 un formulario valido no tiene errores`() {
        assertEquals(emptyMap<SignUpField, UiMessage>(), validateSignUp(valid, clock))
    }

    @Test fun `R11 faltan tipo de documento, sexo y fecha de nacimiento`() {
        val errors = validateSignUp(valid.copy(documentType = null, sex = null, birthDate = null), clock)
        assertEquals(
            mapOf(SignUpField.DocumentType to required, SignUpField.Sex to required, SignUpField.BirthDate to required),
            errors,
        )
    }

    @Test fun `R11 numero de documento y nombre vacios tras quitar espacios`() {
        val errors = validateSignUp(valid.copy(documentNumber = "   ", fullName = "\t "), clock)
        assertEquals(mapOf(SignUpField.DocumentNumber to required, SignUpField.FullName to required), errors)
    }

    @Test fun `R11 numero de documento de mas de 64 y nombre de mas de 200 caracteres sin contar espacios de los extremos`() {
        assertTrue(validateSignUp(valid.copy(documentNumber = " " + "1".repeat(64) + " ", fullName = "a".repeat(200)), clock).isEmpty())
        val errors = validateSignUp(valid.copy(documentNumber = "1".repeat(65), fullName = "a".repeat(201)), clock)
        assertEquals(
            mapOf(
                SignUpField.DocumentNumber to UiMessage(R.string.error_max_length, listOf(64)),
                SignUpField.FullName to UiMessage(R.string.error_max_length, listOf(200)),
            ),
            errors,
        )
    }

    @Test fun `R11 la contrasena debe tener entre 10 y 128 unidades UTF-16`() {
        fun errorsFor(p: String) = validateSignUp(valid.copy(password = p, passwordConfirmation = p), clock)
        assertEquals(mapOf(SignUpField.Password to passwordLength), errorsFor("123456789"))
        assertEquals(mapOf(SignUpField.Password to passwordLength), errorsFor("x".repeat(129)))
        assertTrue(errorsFor("1234567890").isEmpty())
        assertTrue(errorsFor("x".repeat(128)).isEmpty())
        // 5 emojis = 10 unidades UTF-16 (String.length), aunque sean 5 caracteres visibles.
        assertTrue(errorsFor("😀".repeat(5)).isEmpty())
    }

    @Test fun `R11 la confirmacion debe ser identica a la contrasena`() {
        val errors = validateSignUp(valid.copy(passwordConfirmation = "secreta-124"), clock)
        assertEquals(mapOf(SignUpField.PasswordConfirmation to UiMessage(R.string.error_password_mismatch)), errors)
    }

    @Test fun `R12 menor de edad o fecha futura marca la fecha de nacimiento`() {
        val underage = UiMessage(R.string.error_underage)
        assertEquals(mapOf(SignUpField.BirthDate to underage), validateSignUp(valid.copy(birthDate = LocalDate.parse("2008-10-27")), clock))
        assertEquals(mapOf(SignUpField.BirthDate to underage), validateSignUp(valid.copy(birthDate = LocalDate.parse("2030-01-01")), clock))
        assertTrue(validateSignUp(valid.copy(birthDate = LocalDate.parse("2008-10-26")), clock).isEmpty())
    }

    @Test fun `R10 el formulario se convierte al cuerpo con documento y nombre recortados`() {
        val request = valid.toRequest(tenantId = "t-1", consentVersion = "2026-10-v1")
        assertEquals("DEV-123", request.documentNumber)
        assertEquals("DEV Paciente", request.fullName)
        assertEquals("1990-05-17", request.birthDate)
        assertEquals(true, request.consentAccepted)
        assertEquals("2026-10-v1", request.consentVersion)
    }

    @Test fun `R10 la fecha del DatePicker se convierte en UTC sin desplazar el dia`() {
        // 17/05/1990 00:00 UTC, como lo entrega el DatePicker de Material 3 (D6).
        assertEquals(LocalDate.parse("1990-05-17"), datePickerMillisToLocalDate(642902400000))
        assertEquals(642902400000, LocalDate.parse("1990-05-17").toDatePickerMillis())
    }

    @Test fun `R17 login con tipo, numero o contrasena vacios marca Campo obligatorio`() {
        assertEquals(
            mapOf(LoginField.DocumentType to required, LoginField.DocumentNumber to required, LoginField.Password to required),
            validateLogin(LoginForm(documentType = null, documentNumber = "  ", password = "")),
        )
        assertTrue(validateLogin(LoginForm(DocumentType.CE, " 123 ", "x")).isEmpty())
    }

    @Test fun `R32 toString de los formularios enmascara la contrasena y su confirmacion`() {
        val text = valid.copy(password = "secreta-AAA1", passwordConfirmation = "secreta-BBB2").toString()
        assertTrue(!text.contains("secreta-AAA1") && !text.contains("secreta-BBB2"))
        assertTrue(text.contains("password=***") && text.contains("passwordConfirmation=***"))
        val login = LoginForm(DocumentType.CC, "123", "secreta-CCC3").toString()
        assertTrue(!login.contains("secreta-CCC3") && login.contains("password=***"))
    }
}
