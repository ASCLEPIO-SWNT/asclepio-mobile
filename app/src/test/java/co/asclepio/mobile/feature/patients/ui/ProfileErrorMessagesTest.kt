package co.asclepio.mobile.feature.patients.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.allApiErrors
import co.asclepio.mobile.core.network.ApiError
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.httpError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileErrorMessagesTest {
    private val general = UiMessage(R.string.error_general)

    @Test fun `R7 un 422 del PATCH marca la fecha de nacimiento con el mensaje de mayoria de edad`() {
        assertEquals(
            ProfileErrors(fieldErrors = mapOf(ProfileField.BirthDate to UiMessage(R.string.error_underage))),
            profileErrorMessage(httpError(422)),
        )
    }

    @Test fun `R7 un 400 marca cada campo de errors con su mensaje de la app`() {
        assertEquals(
            ProfileErrors(
                fieldErrors = mapOf(
                    ProfileField.FullName to UiMessage(R.string.error_max_length, listOf(200)),
                    ProfileField.BirthDate to UiMessage(R.string.error_required),
                    ProfileField.Sex to UiMessage(R.string.error_required),
                ),
            ),
            profileErrorMessage(httpError(400, "fullName", "birthDate", "sex")),
        )
        assertEquals(
            ProfileErrors(fieldErrors = mapOf(ProfileField.FullName to UiMessage(R.string.error_max_length, listOf(200)))),
            profileErrorMessage(httpError(400, "fullName", "documentNumber")),
        )
    }

    @Test fun `R7 un 400 sin campos del formulario o sin errors muestra el error general`() {
        for (error in listOf(httpError(400), httpError(400, "documentType", "patientId", "otro"))) {
            assertEquals(ProfileErrors(message = general), profileErrorMessage(error))
        }
    }

    @Test fun `R6 un 409 del PATCH indica que los datos quedaron bloqueados`() {
        // El aviso es el de R2 (profile_locked), que la pantalla muestra mientras los datos están bloqueados.
        assertEquals(ProfileErrors(locked = true), profileErrorMessage(httpError(409)))
    }

    @Test fun `R24 429, sin red, 403, 404 y 5xx usan los mensajes comunes de F-01`() {
        assertEquals(UiMessage(R.string.error_too_many_requests, listOf(30)), profileErrorMessage(httpError(429, retryAfter = 30)).message)
        assertEquals(UiMessage(R.string.error_too_many_requests, listOf(60)), profileErrorMessage(httpError(429)).message)
        assertEquals(UiMessage(R.string.error_network), profileErrorMessage(ApiError.Network).message)
        for (code in listOf(403, 404, 500, 503)) assertEquals(ProfileErrors(message = general), profileErrorMessage(httpError(code)))
        assertEquals(general, profileLoadErrorMessage(httpError(403)))
        assertEquals(UiMessage(R.string.error_network), profileLoadErrorMessage(ApiError.Network))
    }

    @Test fun `R28 cada error de Mis datos se traduce a un id de R string sin textos de la API`() {
        val ids = setOf(
            R.string.error_general, R.string.error_network, R.string.error_too_many_requests, R.string.error_underage,
            R.string.error_required, R.string.error_max_length,
        )
        for (error in allApiErrors) {
            val mapped = profileErrorMessage(error)
            val messages = listOfNotNull(mapped.message, profileLoadErrorMessage(error)) + mapped.fieldErrors.values
            assertFalse("$error", messages.isEmpty() && !mapped.locked)
            for (m in messages) {
                assertTrue("$error → $m", m.resId in ids)
                assertTrue(m.args.all { it is Int })
            }
        }
    }
}
