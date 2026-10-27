package co.asclepio.mobile.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AgeTest {
    private fun at(iso: String): Clock = Clock.fixed(Instant.parse(iso), ZoneOffset.UTC)
    private val noonBogota = at("2026-10-26T17:00:00Z") // 26/10/2026 12:00 en Bogotá

    @Test fun `R12 quien cumple 18 hoy es mayor de edad`() {
        assertTrue(isAdult(LocalDate.parse("2008-10-26"), noonBogota))
    }

    @Test fun `R12 quien cumple 18 manana no es mayor de edad`() {
        assertFalse(isAdult(LocalDate.parse("2008-10-27"), noonBogota))
    }

    @Test fun `R12 nacido un 29 de febrero cumple el 1 de marzo en anos no bisiestos`() {
        val born = LocalDate.parse("2008-02-29")
        assertFalse(isAdult(born, at("2026-02-28T17:00:00Z")))
        assertTrue(isAdult(born, at("2026-03-01T17:00:00Z")))
    }

    @Test fun `R12 una fecha futura no es mayor de edad`() {
        assertFalse(isAdult(LocalDate.parse("2027-01-01"), noonBogota))
    }

    @Test fun `R12 a las 04h30 UTC todavia es ayer en Bogota`() {
        // 27/10/2026 04:30 UTC = 26/10/2026 23:30 en Bogotá: quien cumple 18 el 27 aún no es mayor.
        val clock = at("2026-10-27T04:30:00Z")
        assertFalse(isAdult(LocalDate.parse("2008-10-27"), clock))
        assertTrue(isAdult(LocalDate.parse("2008-10-26"), clock))
    }
}
