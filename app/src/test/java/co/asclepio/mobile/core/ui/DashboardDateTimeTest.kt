package co.asclepio.mobile.core.ui

import org.junit.Assert.*
import org.junit.Test

class DashboardDateTimeTest {
    @Test fun `R23 server time crosses UTC midnight into Bogota and keeps meridiem together without seconds`() {
        val evening = formatDashboardBogota("2026-10-28T04:30:59.000Z")
        assertTrue(evening.startsWith("27/10/26"))
        assertTrue(evening.contains("11:30\u00a0p.\u00a0m."))
        assertFalse(evening.contains(":59"))
        val morning = formatDashboardBogota("2026-10-27T13:05:09.000Z")
        assertTrue(morning.contains("8:05\u00a0a.\u00a0m."))
        assertFalse(morning.contains(":09"))
    }
}
