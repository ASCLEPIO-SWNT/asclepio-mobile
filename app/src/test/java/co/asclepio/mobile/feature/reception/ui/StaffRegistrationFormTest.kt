package co.asclepio.mobile.feature.reception.ui

import co.asclepio.mobile.core.encounters.StaffDocumentType
import co.asclepio.mobile.core.model.Sex
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class StaffRegistrationFormTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-30T02:00:00Z"), ZoneOffset.UTC)
    private val documented = StaffPatientForm(
        mode = StaffPatientMode.Document(StaffDocumentType.TI, " DOC-1 "),
        fullName = " Menor Sintético ", birthDate = LocalDate.of(2020, 1, 1), sex = Sex.X,
    )

    @Test fun `R11 documented patient accepts minors and uses trimmed exact document and name`() {
        assertTrue(validateStaffPatient(documented, clock).isEmpty())
        val request = documented.toRequest()
        assertEquals(StaffDocumentType.TI, request.documentType)
        assertEquals("DOC-1", request.documentNumber)
        assertEquals("Menor Sintético", request.fullName)
        assertEquals("2020-01-01", request.birthDate)
        assertNull(request.estimatedAgeYears)
    }
    @Test fun `R11 birth date uses Bogota today and documented name limits are trimmed`() {
        assertTrue(validateStaffPatient(documented.copy(birthDate = LocalDate.of(2026, 10, 29)), clock).isEmpty())
        assertTrue(StaffPatientField.BirthDate in validateStaffPatient(documented.copy(birthDate = LocalDate.of(2026, 10, 30)), clock))
        assertTrue(validateStaffPatient(documented.copy(fullName = "x".repeat(200)), clock).isEmpty())
        assertTrue(StaffPatientField.FullName in validateStaffPatient(documented.copy(fullName = "x".repeat(201)), clock))
    }
    @Test fun `R12 NN age zero and 120 are valid while fraction and 121 are rejected`() {
        val nn = StaffPatientForm(StaffPatientMode.Nn, sex = Sex.F, ageMode = NnAgeMode.EstimatedAge)
        for (age in listOf("0", "120")) {
            val form = nn.copy(estimatedAge = age)
            assertTrue(validateStaffPatient(form, clock).isEmpty())
            assertEquals(age.toInt(), form.toRequest().estimatedAgeYears)
            assertNull(form.toRequest().documentType)
            assertNull(form.toRequest().documentNumber)
            assertNull(form.toRequest().fullName)
            assertNull(form.toRequest().birthDate)
        }
        for (age in listOf("", "-1", "121", "0.5")) assertTrue(StaffPatientField.EstimatedAge in validateStaffPatient(nn.copy(estimatedAge = age), clock))
    }
    @Test fun `R12 NN date mode sends only date and optional literal name without display fallback`() {
        val nn = StaffPatientForm(StaffPatientMode.Nn, sex = Sex.M, birthDate = LocalDate.of(2020, 1, 1), estimatedAge = "9")
        assertTrue(validateStaffPatient(nn, clock).isEmpty())
        assertNull(nn.toRequest().estimatedAgeYears)
        assertNull(nn.toRequest().fullName)
        assertEquals("Nombre", nn.copy(fullName = " Nombre ").toRequest().fullName)
        assertTrue(StaffPatientField.Sex in validateStaffPatient(nn.copy(sex = null), clock))
    }
    @Test fun `R9 exact document search validates trim and 64 character boundary`() {
        assertTrue(validateStaffSearch(StaffSearchForm(StaffDocumentType.RC, " x ")).isEmpty())
        assertTrue(validateStaffSearch(StaffSearchForm(StaffDocumentType.CC, "x".repeat(64))).isEmpty())
        assertTrue(StaffSearchField.DocumentNumber in validateStaffSearch(StaffSearchForm(StaffDocumentType.CC, "x".repeat(65))))
        assertTrue(StaffSearchField.DocumentType in validateStaffSearch(StaffSearchForm(null, "x")))
    }
    @Test fun `R14 arrival validates site and trimmed complaint without changing original draft`() {
        val form = StaffArrivalForm("site", " Motivo original ")
        assertTrue(validateStaffArrival(form).isEmpty())
        assertEquals("Motivo original", form.toRequest("patient").chiefComplaintText)
        assertEquals(" Motivo original ", form.chiefComplaintText)
        assertTrue(validateStaffArrival(form.copy(chiefComplaintText = "x".repeat(2000))).isEmpty())
        assertTrue(StaffArrivalField.ChiefComplaint in validateStaffArrival(form.copy(chiefComplaintText = "x".repeat(2001))))
        assertTrue(StaffArrivalField.Site in validateStaffArrival(form.copy(siteId = null)))
    }
    @Test fun `R24 private registration forms mask string representations`() {
        assertFalse(documented.toString().contains("Menor"))
        assertFalse(documented.mode.toString().contains("DOC-1"))
        assertFalse(StaffSearchForm(StaffDocumentType.CC, "PRIVATE").toString().contains("PRIVATE"))
        assertFalse(StaffArrivalForm("site", "PRIVATE").toString().contains("PRIVATE"))
    }
}
