package co.asclepio.mobile.feature.encounters.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.encounters.data.ComplaintSource
import co.asclepio.mobile.feature.encounters.data.CreatePreRegistrationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreRegistrationFormTest {
    private val valid = PreRegistrationForm(siteId = "s-1", chiefComplaintText = "  Dolor de cabeza  ")

    @Test fun `R10 el formulario se convierte al cuerpo con el motivo recortado y complaintSource TYPED`() {
        assertEquals(ComplaintSource.TYPED, PreRegistrationForm().complaintSource)
        assertEquals(CreatePreRegistrationRequest("s-1", "Dolor de cabeza", ComplaintSource.TYPED), valid.toRequest())
    }

    @Test fun `R11 sin sede marca Elige una sede`() {
        assertEquals(
            mapOf(PreRegistrationField.Site to UiMessage(R.string.error_site_required)),
            validatePreRegistration(valid.copy(siteId = null)),
        )
    }

    @Test fun `R11 el motivo vacio tras quitar espacios o de mas de 2000 unidades UTF-16 se marca`() {
        assertEquals(
            mapOf(PreRegistrationField.ChiefComplaint to UiMessage(R.string.error_required)),
            validatePreRegistration(valid.copy(chiefComplaintText = " \n\t ")),
        )
        assertEquals(
            mapOf(PreRegistrationField.ChiefComplaint to UiMessage(R.string.error_max_length, listOf(2000))),
            validatePreRegistration(valid.copy(chiefComplaintText = "a".repeat(2001))),
        )
        assertTrue(validatePreRegistration(valid.copy(chiefComplaintText = " " + "a".repeat(2000) + " ")).isEmpty())
        // 1000 emojis = 2000 unidades UTF-16 (String.length); uno más supera el máximo.
        assertTrue(validatePreRegistration(valid.copy(chiefComplaintText = "😀".repeat(1000))).isEmpty())
        assertFalse(validatePreRegistration(valid.copy(chiefComplaintText = "😀".repeat(1000) + "a")).isEmpty())
    }

    @Test fun `R11 marca a la vez sede y motivo`() {
        assertEquals(setOf(PreRegistrationField.Site, PreRegistrationField.ChiefComplaint), validatePreRegistration(PreRegistrationForm()).keys)
    }

    @Test fun `R27 el toString del formulario enmascara el motivo`() {
        val text = valid.copy(chiefComplaintText = "Dolor toracico intenso").toString()
        assertFalse(text.contains("Dolor"))
        assertTrue(text.contains("chiefComplaintText=***"))
    }
}
