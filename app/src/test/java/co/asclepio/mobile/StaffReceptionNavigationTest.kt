package co.asclepio.mobile

import co.asclepio.mobile.core.auth.*
import co.asclepio.mobile.core.ui.guide.GuideDestination
import org.junit.Assert.*
import org.junit.Test

class StaffReceptionNavigationTest {
    @Test fun `R1 reception registration and encounter detail reject patient before VM while roles stay distinct`() {
        val tenant = RememberedTenant("t", "IPS", "s", "Sede")
        val patient = SessionState.Authenticated(VerifiedPrincipal.Patient("a", "t", "p", "v", false, tenant), 1)
        assertFalse(canAccessRoute(patient, Route.StaffRegistration))
        assertFalse(canAccessRoute(patient, Route.StaffEncounterDetail("opaque")))
        StaffRole.entries.forEach { role ->
            val staff = SessionState.Authenticated(VerifiedPrincipal.Staff("s", "t", "Staff", role), 2)
            assertEquals(role == StaffRole.RECEPTION, canAccessRoute(staff, Route.StaffRegistration))
            assertTrue(canAccessRoute(staff, Route.StaffEncounterDetail("opaque")))
        }
        assertFalse(canAccessRoute(SessionState.Welcome, Route.StaffRegistration))
        assertFalse(canAccessRoute(SessionState.StaffEntry, Route.StaffEncounterDetail("opaque")))
    }
    @Test fun `R25 reception guides are fixed and detail context never supplies patient text`() {
        assertEquals(GuideDestination.StaffReception, routeGuideDestination(Route.Staff(StaffDestination.RECEPTION)))
        assertEquals(GuideDestination.StaffRegistration, routeGuideDestination(Route.StaffRegistration))
        assertEquals(GuideDestination.StaffEncounterDetail, routeGuideDestination(Route.StaffEncounterDetail("opaque")))
    }
}
