package co.asclepio.mobile.feature.patients.ui

import co.asclepio.mobile.R
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.patients.data.PatientProfileDto
import co.asclepio.mobile.feature.patients.data.UpdatePatientProfileRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ProfileFormTest {
    // 2026-10-27 10:00 en America/Bogota.
    private val clock = Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC)
    private val loaded = PatientProfileDto("p", "CC", "DEV-1020304050", "DEV Paciente", "1990-05-17", Sex.F, true)
    private val form = loaded.toForm()
    private val underage = UiMessage(R.string.error_underage)

    @Test fun `R3 el formulario parte del perfil cargado`() {
        assertEquals(ProfileForm("DEV Paciente", LocalDate.parse("1990-05-17"), Sex.F), form)
        assertEquals(ProfileForm("", null, null), loaded.copy(fullName = null, birthDate = null, sex = null).toForm())
    }

    @Test fun `R3 diff incluye solo los campos cambiados con el nombre recortado y la fecha en YYYY-MM-DD`() {
        assertEquals(UpdatePatientProfileRequest(fullName = "DEV Otro"), diff(loaded, form.copy(fullName = "  DEV Otro ")))
        assertEquals(
            UpdatePatientProfileRequest(birthDate = "1991-01-02", sex = Sex.X),
            diff(loaded, form.copy(birthDate = LocalDate.parse("1991-01-02"), sex = Sex.X)),
        )
        assertEquals(
            UpdatePatientProfileRequest("DEV Otro", "1991-01-02", Sex.M),
            diff(loaded, ProfileForm("DEV Otro", LocalDate.parse("1991-01-02"), Sex.M)),
        )
    }

    @Test fun `R4 sin cambios, o solo con espacios en los extremos, diff esta vacio`() {
        assertTrue(diff(loaded, form).isEmpty)
        assertTrue(diff(loaded, form.copy(fullName = " DEV Paciente  ")).isEmpty)
        val empty = loaded.copy(fullName = null, birthDate = null, sex = null)
        assertTrue(diff(empty, empty.toForm()).isEmpty)
    }

    @Test fun `R5 nombre vacio o de mas de 200 caracteres tras quitar espacios`() {
        assertEquals(mapOf(ProfileField.FullName to UiMessage(R.string.error_required)), validateProfile(form.copy(fullName = " \t "), clock))
        assertEquals(
            mapOf(ProfileField.FullName to UiMessage(R.string.error_max_length, listOf(200))),
            validateProfile(form.copy(fullName = "a".repeat(201)), clock),
        )
        assertTrue(validateProfile(form.copy(fullName = " " + "a".repeat(200) + " "), clock).isEmpty())
    }

    @Test fun `R5 la mayoria de edad se calcula en America Bogota con los casos frontera`() {
        assertTrue(validateProfile(form.copy(birthDate = LocalDate.parse("2008-10-27")), clock).isEmpty())
        assertEquals(mapOf(ProfileField.BirthDate to underage), validateProfile(form.copy(birthDate = LocalDate.parse("2008-10-28")), clock))
        assertEquals(mapOf(ProfileField.BirthDate to underage), validateProfile(form.copy(birthDate = LocalDate.parse("2030-01-01")), clock))
        // 2026-10-28 03:00 UTC sigue siendo 27/10 en Bogotá: quien nació el 28/10/2008 aún no cumple 18.
        val lateUtc = Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneOffset.UTC)
        assertEquals(mapOf(ProfileField.BirthDate to underage), validateProfile(form.copy(birthDate = LocalDate.parse("2008-10-28")), lateUtc))
        // 29 de febrero: cumple el 1 de marzo en años no bisiestos.
        val leap = form.copy(birthDate = LocalDate.parse("2008-02-29"))
        assertFalse(validateProfile(leap, Clock.fixed(Instant.parse("2026-02-28T17:00:00Z"), ZoneOffset.UTC)).isEmpty())
        assertTrue(validateProfile(leap, Clock.fixed(Instant.parse("2026-03-01T17:00:00Z"), ZoneOffset.UTC)).isEmpty())
    }

    @Test fun `R5 marca a la vez nombre y fecha invalidos`() {
        assertEquals(
            mapOf(ProfileField.FullName to UiMessage(R.string.error_required), ProfileField.BirthDate to underage),
            validateProfile(ProfileForm("", LocalDate.parse("2010-01-01"), Sex.M), clock),
        )
    }

    @Test fun `R1 las etiquetas del documento y del sexo y el guion para los nulos`() {
        assertEquals(DisplayText.Res(R.string.document_type_cc), documentTypeLabel("CC"))
        assertEquals(DisplayText.Res(R.string.document_type_ce), documentTypeLabel("CE"))
        assertEquals(DisplayText.Res(R.string.document_type_pa), documentTypeLabel("PA"))
        assertEquals(DisplayText.Res(R.string.document_type_ti), documentTypeLabel("TI"))
        assertEquals(DisplayText.Res(R.string.document_type_rc), documentTypeLabel("RC"))
        assertEquals(DisplayText.Raw("NUIP"), documentTypeLabel("NUIP"))
        assertEquals(DisplayText.Res(R.string.value_missing), documentTypeLabel(null))
        assertEquals(
            ProfileDisplay(
                documentType = DisplayText.Res(R.string.document_type_cc),
                documentNumber = DisplayText.Raw("DEV-1020304050"),
                fullName = DisplayText.Raw("DEV Paciente"),
                birthDate = DisplayText.Raw("17/05/1990"),
                sex = DisplayText.Res(R.string.sex_f),
            ),
            profileDisplay(loaded),
        )
        val missing = DisplayText.Res(R.string.value_missing)
        assertEquals(
            ProfileDisplay(missing, missing, missing, missing, missing),
            profileDisplay(PatientProfileDto("p", null, null, null, null, null, false)),
        )
        assertEquals(DisplayText.Res(R.string.sex_m), sexLabel(Sex.M))
        assertEquals(DisplayText.Res(R.string.sex_x), sexLabel(Sex.X))
    }

    @Test fun `R27 el toString del formulario y de lo mostrado enmascara nombre, documento y fecha`() {
        for (text in listOf(form.toString(), profileDisplay(loaded).toString())) {
            assertFalse(text, text.contains("DEV Paciente"))
            assertFalse(text, text.contains("DEV-1020304050"))
            assertFalse(text, text.contains("1990") || text.contains("17/05"))
        }
    }
}
