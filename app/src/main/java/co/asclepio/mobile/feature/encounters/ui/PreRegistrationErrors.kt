package co.asclepio.mobile.feature.encounters.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.network.commonMessage
import co.asclepio.mobile.core.network.httpStatus
import co.asclepio.mobile.core.ui.UiMessage

// Traducción de errores del pre-registro a mensajes de la app (D12, R28). Nunca se usan title, detail ni errors[].message.

/** Resultado de un error del `POST`: campos marcados, mensaje y las acciones que provoca (R13–R15). */
data class PreRegistrationErrors(
    val fieldErrors: Map<PreRegistrationField, UiMessage> = emptyMap(),
    val message: UiMessage? = null,
    /** R13: acción "Ver mi estado". */
    val showViewStatus: Boolean = false,
    /** R14: quitar la sede elegida y volver a pedir las sedes. */
    val siteUnavailable: Boolean = false,
)

/** Nombre del campo de la API → campo del formulario y el mensaje de su regla de validación local (R15). */
private val apiFieldMessages: Map<String, Pair<PreRegistrationField, UiMessage>> = mapOf(
    "siteId" to (PreRegistrationField.Site to UiMessage(R.string.error_site_required)),
    "chiefComplaintText" to (PreRegistrationField.ChiefComplaint to UiMessage(R.string.error_max_length, listOf(CHIEF_COMPLAINT_MAX))),
)

/** R13, R14, R15, R24: errores del `POST /patient/pre-registrations`. 403, 404 y 5xx dan el error general. */
fun preRegistrationErrorMessage(error: ApiError): PreRegistrationErrors = when (error.httpStatus) {
    400 -> {
        val apiFields = (error as ApiError.Http).problem.errors.map { it.field }
        val fields = apiFields.mapNotNull { apiFieldMessages[it] }.toMap()
        val general = fields.isEmpty() || "complaintSource" in apiFields
        PreRegistrationErrors(fieldErrors = fields, message = if (general) UiMessage(R.string.error_general) else null)
    }
    409 -> PreRegistrationErrors(message = UiMessage(R.string.error_preregistration_conflict), showViewStatus = true)
    422 -> PreRegistrationErrors(message = UiMessage(R.string.error_site_unavailable), siteUnavailable = true)
    else -> PreRegistrationErrors(message = error.commonMessage())
}

/** R9, R24: errores de `GET /public/tenants` en el formulario. */
fun sitesErrorMessage(error: ApiError): UiMessage = error.commonMessage()

/** R20, R24: errores de `GET /patient/pre-registrations` en el inicio. */
fun statusErrorMessage(error: ApiError): UiMessage = error.commonMessage()
