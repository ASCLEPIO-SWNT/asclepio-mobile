package co.asclepio.mobile.feature.account.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.isAdult
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.account.data.CreatePatientAccountRequest
import co.asclepio.mobile.feature.account.data.DocumentType
import co.asclepio.mobile.feature.account.data.Sex
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Límites del contrato 0.2.1 (D17).
const val DOCUMENT_NUMBER_MAX = 64
const val FULL_NAME_MAX = 200
const val PASSWORD_MIN = 10
const val PASSWORD_MAX = 128

enum class SignUpField { DocumentType, DocumentNumber, FullName, BirthDate, Sex, Password, PasswordConfirmation }

enum class LoginField { DocumentType, DocumentNumber, Password }

/** Estado del formulario de alta. La contraseña y su confirmación solo viven en memoria (R32, D13). */
data class SignUpForm(
    val documentType: DocumentType? = null,
    val documentNumber: String = "",
    val fullName: String = "",
    val birthDate: LocalDate? = null,
    val sex: Sex? = null,
    val password: String = "",
    val passwordConfirmation: String = "",
    val consentAccepted: Boolean = false,
) {
    override fun toString(): String =
        "SignUpForm(documentType=$documentType, documentNumber=***, fullName=***, birthDate=***, sex=$sex, " +
            "password=***, passwordConfirmation=***, consentAccepted=$consentAccepted)"
}

/** Estado del formulario de inicio de sesión (R32). */
data class LoginForm(
    val documentType: DocumentType? = null,
    val documentNumber: String = "",
    val password: String = "",
) {
    override fun toString(): String = "LoginForm(documentType=$documentType, documentNumber=***, password=***)"
}

private val required = UiMessage(R.string.error_required)

/** Validación local del alta (R11, R12). La API valida igual; esto es para la experiencia de uso. */
fun validateSignUp(form: SignUpForm, clock: Clock): Map<SignUpField, UiMessage> = buildMap {
    if (form.documentType == null) put(SignUpField.DocumentType, required)
    lengthError(form.documentNumber.trim(), DOCUMENT_NUMBER_MAX)?.let { put(SignUpField.DocumentNumber, it) }
    lengthError(form.fullName.trim(), FULL_NAME_MAX)?.let { put(SignUpField.FullName, it) }
    when {
        form.birthDate == null -> put(SignUpField.BirthDate, required)
        !isAdult(form.birthDate, clock) -> put(SignUpField.BirthDate, UiMessage(R.string.error_underage))
    }
    if (form.sex == null) put(SignUpField.Sex, required)
    if (form.password.length !in PASSWORD_MIN..PASSWORD_MAX) {
        put(SignUpField.Password, UiMessage(R.string.error_password_length))
    }
    if (form.passwordConfirmation != form.password) {
        put(SignUpField.PasswordConfirmation, UiMessage(R.string.error_password_mismatch))
    }
}

/** Validación local del inicio de sesión (R17). */
fun validateLogin(form: LoginForm): Map<LoginField, UiMessage> = buildMap {
    if (form.documentType == null) put(LoginField.DocumentType, required)
    if (form.documentNumber.isBlank()) put(LoginField.DocumentNumber, required)
    if (form.password.isEmpty()) put(LoginField.Password, required)
}

private fun lengthError(value: String, max: Int): UiMessage? = when {
    value.isEmpty() -> required
    value.length > max -> UiMessage(R.string.error_max_length, listOf(max))
    else -> null
}

/** Cuerpo del alta (R8, R10). Solo se llama con un formulario ya validado. */
fun SignUpForm.toRequest(tenantId: String, consentVersion: String): CreatePatientAccountRequest =
    CreatePatientAccountRequest(
        tenantId = tenantId,
        documentType = checkNotNull(documentType),
        documentNumber = documentNumber.trim(),
        fullName = fullName.trim(),
        birthDate = checkNotNull(birthDate).toString(),
        sex = checkNotNull(sex),
        password = password,
        consentAccepted = true,
        consentVersion = consentVersion,
    )

/** El DatePicker de Material 3 entrega la medianoche UTC del día elegido: es una fecha civil, sin zona (D6). */
fun datePickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

fun LocalDate.toDatePickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
