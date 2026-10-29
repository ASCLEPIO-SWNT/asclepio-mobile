package co.asclepio.mobile.core.ui

import java.time.LocalDate
import co.asclepio.mobile.core.model.toDatePickerMillis
import org.junit.Assert.*
import org.junit.Test

class PrivateDatePickerDraftTest {
    @Test fun `R6 unconfirmed valid and partial date drafts remain literal without mutating confirmed value`() {
        val confirmed = LocalDate.of(1990, 5, 17)
        val opened = PrivateDatePickerDraft.open(confirmed).copy(inputMode = true)
        val typed = opened.withInput("03/02/2001")
        assertTrue(typed.isOpen)
        assertTrue(typed.canAccept)
        assertEquals(LocalDate.of(2001, 2, 3), typed.date)
        assertEquals(confirmed, opened.date)
        val partial = typed.withInput("03/0")
        assertEquals("03/0", partial.inputText)
        assertTrue(partial.inputMode)
        assertNull(partial.date)
        assertFalse(partial.canAccept)
        assertEquals(partial, partial.copy())
        assertEquals(confirmed, opened.date)
    }

    @Test fun `R6 input order is stable across locales and invalid calendar dates cannot be accepted`() {
        val open = PrivateDatePickerDraft.open(null).copy(inputMode = true)
        assertEquals(LocalDate.of(2001, 2, 3), open.withInput("03022001").date)
        assertFalse(open.withInput("31/02/2001").canAccept)
        assertFalse(open.withInput("2026-02-03").canAccept)
        assertFalse(open.withInput("").canAccept)
        assertFalse(PrivateDatePickerDraft().canAccept)
    }

    @Test fun `R6 signed and extended date years stay literal and cannot overflow or be accepted`() {
        val open = PrivateDatePickerDraft.open(null).copy(inputMode = true)
        for (input in listOf("03/02/+999999999", "03/02/+2001", "03/02/-0001", "0302+999999999", "03/02/10000")) {
            val invalid = open.withInput(input)
            assertEquals(input, invalid.inputText)
            assertNull(invalid.date)
            assertFalse(invalid.canAccept)
        }
    }

    @Test fun `R6 calendar range includes valid input and retained displayed month outside default years`() {
        for (year in listOf(0, 1800, 9999)) {
            val millis = LocalDate.of(year, 2, 3).toDatePickerMillis()
            assertTrue(year in privateDatePickerYearRange(millis, null))
            assertTrue(year in privateDatePickerYearRange(null, millis))
        }
        val range = privateDatePickerYearRange(LocalDate.of(1800, 2, 3).toDatePickerMillis(), LocalDate.of(9999, 1, 1).toDatePickerMillis())
        assertTrue(1800 in range)
        assertTrue(9999 in range)
    }

    @Test fun `R20 date modal state has no serializer and its string representation hides private values`() {
        val draft = PrivateDatePickerDraft.open(LocalDate.of(2001,2,3)).copy(inputMode = true).withInput("03/0")
        assertFalse(draft.toString().contains("03/0"))
        assertFalse(draft.toString().contains("2001"))
        assertTrue(PrivateDatePickerDraft::class.java.declaredMethods.none { it.name == "serializer" })
    }
}
