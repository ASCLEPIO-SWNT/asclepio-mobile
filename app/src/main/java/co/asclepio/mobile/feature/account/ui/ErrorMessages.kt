package co.asclepio.mobile.feature.account.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.commonMessage
import co.asclepio.mobile.core.network.httpStatus
import co.asclepio.mobile.core.ui.UiMessage

// Traducción de errores a mensajes de la app por pantalla (D7, D8, R34). Nunca se usan title, detail ni errors[].message.

fun tenantsErrorMessage(error: ApiError): UiMessage = error.commonMessage()

fun consentErrorMessage(error: ApiError): UiMessage = error.commonMessage()

/** R18: un 401 del login tiene un único mensaje, sea cual sea la causa. */
fun loginErrorMessage(error: ApiError): UiMessage =
    if (error.httpStatus == 401) UiMessage(R.string.error_login_invalid) else error.commonMessage()

/** R21: tras el login, un 401 de `/me` se muestra como error general. */
fun meErrorMessage(error: ApiError): UiMessage = error.commonMessage()

val notPatientMessage = UiMessage(R.string.error_not_patient)

data class SignUpErrors(val fieldErrors: Map<SignUpField, UiMessage> = emptyMap(), val message: UiMessage? = null)

/**
 * D18: nombre del campo de la API → campo del formulario y su mensaje de la app (el de su regla de validación local).
 * `consentAccepted`, `consentVersion`, `tenantId` y cualquier otro van al mensaje general.
 */
private val apiFieldMessages: Map<String, Pair<SignUpField, UiMessage>> = mapOf(
    "documentType" to (SignUpField.DocumentType to UiMessage(R.string.error_required)),
    "documentNumber" to (SignUpField.DocumentNumber to UiMessage(R.string.error_max_length, listOf(DOCUMENT_NUMBER_MAX))),
    "fullName" to (SignUpField.FullName to UiMessage(R.string.error_max_length, listOf(FULL_NAME_MAX))),
    "birthDate" to (SignUpField.BirthDate to UiMessage(R.string.error_required)),
    "sex" to (SignUpField.Sex to UiMessage(R.string.error_required)),
    "password" to (SignUpField.Password to UiMessage(R.string.error_password_length)),
)

/** R13, R14, R15, R22, R23. */
fun signUpErrorMessage(error: ApiError): SignUpErrors = when (error.httpStatus) {
    400 -> {
        val problem = (error as ApiError.Http).problem
        val fields = problem.errors.mapNotNull { apiFieldMessages[it.field] }.toMap()
        if (fields.isEmpty()) SignUpErrors(message = UiMessage(R.string.error_general)) else SignUpErrors(fieldErrors = fields)
    }
    409 -> SignUpErrors(message = UiMessage(R.string.error_signup_conflict))
    422 -> SignUpErrors(message = UiMessage(R.string.error_signup_unprocessable))
    else -> SignUpErrors(message = error.commonMessage())
}
