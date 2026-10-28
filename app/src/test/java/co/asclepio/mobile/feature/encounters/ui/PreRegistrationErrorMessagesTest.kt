package co.asclepio.mobile.feature.encounters.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.allApiErrors
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.httpError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreRegistrationErrorMessagesTest {
    private val general = UiMessage(R.string.error_general)
    private val siteRequired = UiMessage(R.string.error_site_required)
    private val complaintMax = UiMessage(R.string.error_max_length, listOf(2000))

    @Test fun `R13 un 409 del POST muestra el aviso de pre-registro existente con Ver mi estado`() {
        assertEquals(
            PreRegistrationErrors(message = UiMessage(R.string.error_preregistration_conflict), showViewStatus = true),
            preRegistrationErrorMessage(httpError(409)),
        )
    }

    @Test fun `R14 un 422 del POST indica que la sede ya no esta disponible`() {
        assertEquals(
            PreRegistrationErrors(message = UiMessage(R.string.error_site_unavailable), siteUnavailable = true),
            preRegistrationErrorMessage(httpError(422)),
        )
    }

    @Test fun `R15 un 400 marca la sede y el motivo segun errors field`() {
        assertEquals(
            PreRegistrationErrors(mapOf(PreRegistrationField.Site to siteRequired, PreRegistrationField.ChiefComplaint to complaintMax)),
            preRegistrationErrorMessage(httpError(400, "siteId", "chiefComplaintText")),
        )
        assertEquals(
            PreRegistrationErrors(mapOf(PreRegistrationField.Site to siteRequired)),
            preRegistrationErrorMessage(httpError(400, "siteId")),
        )
    }

    @Test fun `R15 un 400 sin campos conocidos, sin errors o con complaintSource muestra el error general`() {
        for (error in listOf(httpError(400), httpError(400, "patientId", "arrivalAt"), httpError(400, "complaintSource"))) {
            assertEquals(PreRegistrationErrors(message = general), preRegistrationErrorMessage(error))
        }
        assertEquals(
            PreRegistrationErrors(mapOf(PreRegistrationField.Site to siteRequired), message = general),
            preRegistrationErrorMessage(httpError(400, "siteId", "complaintSource")),
        )
    }

    @Test fun `R24 429, sin red, 403, 404 y 5xx usan los mensajes comunes en el formulario, las sedes y el estado`() {
        val tooMany = UiMessage(R.string.error_too_many_requests, listOf(30))
        assertEquals(tooMany, preRegistrationErrorMessage(httpError(429, retryAfter = 30)).message)
        assertEquals(tooMany, sitesErrorMessage(httpError(429, retryAfter = 30)))
        assertEquals(tooMany, statusErrorMessage(httpError(429, retryAfter = 30)))
        assertEquals(UiMessage(R.string.error_too_many_requests, listOf(60)), statusErrorMessage(httpError(429)))
        for (fn in listOf(::sitesErrorMessage, ::statusErrorMessage, { e: ApiError -> preRegistrationErrorMessage(e).message })) {
            assertEquals(UiMessage(R.string.error_network), fn(ApiError.Network))
            for (code in listOf(403, 404, 500, 503)) assertEquals(general, fn(httpError(code)))
            assertEquals(general, fn(ApiError.InvalidBody))
        }
    }

    @Test fun `R20 un fallo de la lista de pre-registros se traduce al mensaje de la app que corresponde`() {
        assertEquals(UiMessage(R.string.error_network), statusErrorMessage(ApiError.Network))
        assertEquals(general, statusErrorMessage(httpError(500)))
    }

    @Test fun `R28 cada error del pre-registro y del estado se traduce a un id de R string sin textos de la API`() {
        val ids = setOf(
            R.string.error_general, R.string.error_network, R.string.error_too_many_requests, R.string.error_site_required,
            R.string.error_max_length, R.string.error_preregistration_conflict, R.string.error_site_unavailable,
        )
        for (error in allApiErrors) {
            val mapped = preRegistrationErrorMessage(error)
            val messages = listOfNotNull(mapped.message, sitesErrorMessage(error), statusErrorMessage(error)) + mapped.fieldErrors.values
            assertFalse(messages.isEmpty())
            for (m in messages) {
                assertTrue("$error → $m", m.resId in ids)
                assertTrue(m.args.all { it is Int })
            }
        }
    }
}
