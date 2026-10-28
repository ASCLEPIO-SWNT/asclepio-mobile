package co.asclepio.mobile.feature.encounters.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.feature.encounters.data.EncounterRefDto
import co.asclepio.mobile.feature.encounters.data.PatientPreRegistrationDto
import org.junit.Assert.assertEquals
import org.junit.Test

class PreRegistrationStatusTest {
    private fun dto(status: String, encounter: String? = null) = PatientPreRegistrationDto(
        id = "pr", siteId = "s", siteName = "Sede Norte", status = status,
        preRegisteredAt = "2026-10-27T14:30:00.000Z", expiresAt = "2026-10-27T20:30:00.000Z",
        encounter = encounter?.let { EncounterRefDto("e", it) },
    )

    // Tabla de PA-M02-2: (status, encounter.status) → texto y si está en curso.
    private val table = listOf(
        Triple(dto("PENDING"), R.string.status_pending, true),
        Triple(dto("EXPIRED"), R.string.status_expired, false),
        Triple(dto("REJECTED"), R.string.status_rejected, false),
        Triple(dto("CONFIRMED"), R.string.status_confirmed, true),
        Triple(dto("CONFIRMED", "REGISTERED"), R.string.status_waiting_triage, true),
        Triple(dto("CONFIRMED", "WAITING_TRIAGE"), R.string.status_waiting_triage, true),
        Triple(dto("CONFIRMED", "TRIAGED"), R.string.status_triaged, true),
        Triple(dto("CONFIRMED", "IN_CARE"), R.string.status_in_care, true),
        Triple(dto("CONFIRMED", "CLOSED"), R.string.status_closed, false),
        Triple(dto("CONFIRMED", "CANCELLED"), R.string.status_cancelled, false),
        Triple(dto("NUEVO"), R.string.status_unknown, true),
        Triple(dto("CONFIRMED", "NUEVO"), R.string.status_unknown, true),
    )

    @Test fun `R17 cada fila de la tabla de estados da su texto y un valor desconocido da Estado no disponible`() {
        for ((d, text, _) in table) assertEquals("${d.status}/${d.encounter?.status}", text, statusText(d))
    }

    @Test fun `R19 en curso PENDING, CONFIRMED sin cerrar ni cancelar y valores desconocidos`() {
        for ((d, _, inProgress) in table) assertEquals("${d.status}/${d.encounter?.status}", inProgress, isInProgress(d))
    }
}
