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

    @Test fun `R6 el dictado se anade al final separado por un espacio`() {
        val form = PreRegistrationForm(siteId = "s-1", chiefComplaintText = "Dolor de cabeza").withDictation("desde ayer")
        assertEquals("Dolor de cabeza desde ayer", form.chiefComplaintText)
    }

    @Test fun `R6 sin espacio extra si el motivo esta vacio o termina en espacio`() {
        assertEquals("desde ayer", PreRegistrationForm().withDictation("desde ayer").chiefComplaintText)
        assertEquals("Fiebre desde ayer", PreRegistrationForm(chiefComplaintText = "Fiebre ").withDictation("desde ayer").chiefComplaintText)
        assertEquals("Fiebre\ndesde ayer", PreRegistrationForm(chiefComplaintText = "Fiebre\n").withDictation("desde ayer").chiefComplaintText)
    }

    @Test fun `R6 el dictado no trunca y un motivo de mas de 2000 da el error de longitud`() {
        val form = PreRegistrationForm(siteId = "s-1", chiefComplaintText = "a".repeat(1995)).withDictation("bbbbbbbbbb")
        assertEquals("a".repeat(1995) + " " + "bbbbbbbbbb", form.chiefComplaintText)
        assertEquals(2006, form.chiefComplaintText.length)
        assertEquals(
            mapOf(PreRegistrationField.ChiefComplaint to UiMessage(R.string.error_max_length, listOf(2000))),
            validatePreRegistration(form),
        )
    }

    @Test fun `R8 aceptar dictado fija VOICE_ON_DEVICE`() {
        val form = PreRegistrationForm(siteId = "s-1", chiefComplaintText = "Escrito").withDictation("dictado")
        assertEquals(ComplaintSource.VOICE_ON_DEVICE, form.complaintSource)
        assertEquals(ComplaintSource.VOICE_ON_DEVICE, form.toRequest().complaintSource)
    }

    @Test fun `R8 dictado con edicion se envia como VOICE_ON_DEVICE`() {
        val form = PreRegistrationForm(siteId = "s-1").withDictation("me duele el pecho").withTypedText("Me duele mucho el pecho, empezó anoche")
        assertEquals(ComplaintSource.VOICE_ON_DEVICE, form.complaintSource)
        assertEquals(
            CreatePreRegistrationRequest("s-1", "Me duele mucho el pecho, empezó anoche", ComplaintSource.VOICE_ON_DEVICE),
            form.toRequest(),
        )
    }

    @Test fun `R8 borrar todo el motivo vuelve a TYPED y lo escrito despues sigue TYPED`() {
        val dictated = PreRegistrationForm(siteId = "s-1").withDictation("me duele el pecho")
        val blank = dictated.withTypedText("  \n ")
        assertEquals(ComplaintSource.TYPED, blank.complaintSource)
        assertEquals(ComplaintSource.TYPED, dictated.withTypedText("").complaintSource)
        val retyped = blank.withTypedText("Dolor de cabeza")
        assertEquals(ComplaintSource.TYPED, retyped.complaintSource)
        assertEquals("Dolor de cabeza", retyped.chiefComplaintText)
    }

    @Test fun `R8 sin dictado el origen es TYPED`() {
        val form = PreRegistrationForm(siteId = "s-1").withTypedText("Fiebre").withTypedText("Fiebre alta")
        assertEquals(ComplaintSource.TYPED, form.complaintSource)
        assertEquals(ComplaintSource.TYPED, PreRegistrationForm().complaintSource)
    }
}
