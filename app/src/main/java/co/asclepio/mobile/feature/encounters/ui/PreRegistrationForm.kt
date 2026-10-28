package co.asclepio.mobile.feature.encounters.ui

import androidx.annotation.StringRes
import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.encounters.data.ComplaintSource
import co.asclepio.mobile.feature.encounters.data.CreatePreRegistrationRequest
import co.asclepio.mobile.feature.encounters.data.PatientPreRegistrationDto

/** `maxLength` de `chiefComplaintText` en CreatePreRegistrationDto (contrato 0.4.0). */
const val CHIEF_COMPLAINT_MAX = 2000

enum class PreRegistrationField { Site, ChiefComplaint }

/**
 * Formulario de pre-registro: sede y motivo. El motivo solo vive en memoria (R27, D13).
 * `complaintSource` es siempre [ComplaintSource.TYPED] en F-02 (D8); F-08 lo cambiará al dictar.
 */
data class PreRegistrationForm(
    val siteId: String? = null,
    val chiefComplaintText: String = "",
    val complaintSource: ComplaintSource = ComplaintSource.TYPED,
) {
    override fun toString(): String =
        "PreRegistrationForm(siteId=$siteId, chiefComplaintText=***, complaintSource=$complaintSource)"
}

/** Validación local (D10, R11): sede obligatoria y motivo de 1 a 2 000 unidades UTF-16 tras quitar espacios. */
fun validatePreRegistration(form: PreRegistrationForm): Map<PreRegistrationField, UiMessage> = buildMap {
    if (form.siteId == null) put(PreRegistrationField.Site, UiMessage(R.string.error_site_required))
    val text = form.chiefComplaintText.trim()
    when {
        text.isEmpty() -> put(PreRegistrationField.ChiefComplaint, UiMessage(R.string.error_required))
        text.length > CHIEF_COMPLAINT_MAX ->
            put(PreRegistrationField.ChiefComplaint, UiMessage(R.string.error_max_length, listOf(CHIEF_COMPLAINT_MAX)))
    }
}

/** Cuerpo del `POST` (R10). Solo se llama con un formulario ya validado. */
fun PreRegistrationForm.toRequest(): CreatePreRegistrationRequest =
    CreatePreRegistrationRequest(
        siteId = checkNotNull(siteId),
        chiefComplaintText = chiefComplaintText.trim(),
        complaintSource = complaintSource,
    )

private const val PENDING = "PENDING"
private const val CONFIRMED = "CONFIRMED"
private const val EXPIRED = "EXPIRED"
private const val REJECTED = "REJECTED"
private val FINISHED_ENCOUNTER = setOf("CLOSED", "CANCELLED")

/** Texto del estado del pre-registro y de su atención (tabla de PA-M02-2, R17). Nunca el nivel de triage (R21). */
@StringRes
fun statusText(dto: PatientPreRegistrationDto): Int = when (dto.status) {
    PENDING -> R.string.status_pending
    EXPIRED -> R.string.status_expired
    REJECTED -> R.string.status_rejected
    CONFIRMED -> when (dto.encounter?.status) {
        null -> R.string.status_confirmed
        "REGISTERED", "WAITING_TRIAGE" -> R.string.status_waiting_triage
        "TRIAGED" -> R.string.status_triaged
        "IN_CARE" -> R.string.status_in_care
        "CLOSED" -> R.string.status_closed
        "CANCELLED" -> R.string.status_cancelled
        else -> R.string.status_unknown
    }
    else -> R.string.status_unknown
}

/**
 * "En curso" (glosario, R19): `PENDING`; `CONFIRMED` sin atención o con una atención que no está cerrada ni cancelada;
 * o un valor desconocido (para no ofrecer un pre-registro que la API rechazaría, PA-M02-2).
 */
fun isInProgress(dto: PatientPreRegistrationDto): Boolean = when (dto.status) {
    PENDING -> true
    EXPIRED, REJECTED -> false
    CONFIRMED -> dto.encounter?.status !in FINISHED_ENCOUNTER
    else -> true
}
