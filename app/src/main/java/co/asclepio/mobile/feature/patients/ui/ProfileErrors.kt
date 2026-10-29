package co.asclepio.mobile.feature.patients.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.core.model.FULL_NAME_MAX
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.commonMessage
import co.asclepio.mobile.core.network.httpStatus
import co.asclepio.mobile.core.ui.UiMessage

// Traducción de errores de "Mis datos" a mensajes de la app (D12, R28). Nunca se usan title, detail ni errors[].message.

/** Resultado de un error del `PATCH`: campos marcados, mensaje general y si la API dijo que los datos están bloqueados (409). */
data class ProfileErrors(
    val fieldErrors: Map<ProfileField, UiMessage> = emptyMap(),
    val message: UiMessage? = null,
    val locked: Boolean = false,
)

/** Nombre del campo de la API → campo del formulario y el mensaje de su regla de validación local (R7). */
private val apiFieldMessages: Map<String, Pair<ProfileField, UiMessage>> = mapOf(
    "fullName" to (ProfileField.FullName to UiMessage(R.string.error_max_length, listOf(FULL_NAME_MAX))),
    "birthDate" to (ProfileField.BirthDate to UiMessage(R.string.error_required)),
    "sex" to (ProfileField.Sex to UiMessage(R.string.error_required)),
)

/** R6, R7, R24: errores del `PATCH /patient/profile`. 403, 404 y 5xx dan el error general. */
fun profileErrorMessage(error: ApiError): ProfileErrors = when (error.httpStatus) {
    400 -> {
        val fields = (error as ApiError.Http).problem.errors.mapNotNull { apiFieldMessages[it.field] }.toMap()
        if (fields.isEmpty()) ProfileErrors(message = UiMessage(R.string.error_general)) else ProfileErrors(fieldErrors = fields)
    }
    409 -> ProfileErrors(locked = true)
    422 -> ProfileErrors(fieldErrors = mapOf(ProfileField.BirthDate to UiMessage(R.string.error_underage_profile)))
    else -> ProfileErrors(message = error.commonMessage())
}

/** R24: errores del `GET /patient/profile`. */
fun profileLoadErrorMessage(error: ApiError): UiMessage = error.commonMessage()
