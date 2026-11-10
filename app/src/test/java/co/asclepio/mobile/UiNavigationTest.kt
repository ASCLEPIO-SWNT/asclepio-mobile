package co.asclepio.mobile

import co.asclepio.mobile.core.auth.*
import co.asclepio.mobile.core.ui.guide.GuideDestination
import org.junit.Assert.*
import org.junit.Test

class UiNavigationTest {
    private val tenant = RememberedTenant("tenant", "IPS", "site", "Sede")
    private val patient = SessionState.Authenticated(VerifiedPrincipal.Patient("a", "tenant", "p", "v1", false, tenant), 1)

    @Test fun `R15 own requests are guarded for patients while staff and entry cannot mount them`() {
        assertTrue(canAccessRoute(patient, Route.MyRequests))
        StaffRole.entries.forEach { role ->
            assertFalse(canAccessRoute(SessionState.Authenticated(VerifiedPrincipal.Staff("s", "tenant", "Staff", role), 2), Route.MyRequests))
        }
        assertFalse(canAccessRoute(SessionState.Welcome, Route.MyRequests))
        assertFalse(canAccessRoute(SessionState.LoggedOut(tenant), Route.MyRequests))
        assertEquals(Route.Home, rootRoute(patient))
    }

    @Test fun `R11 guide mapping uses only static destinations and every implemented route is covered`() {
        val expected = mapOf<Route, GuideDestination>(
            Route.Welcome to GuideDestination.Welcome, Route.StaffLogin to GuideDestination.StaffLogin,
            Route.TenantPicker to GuideDestination.TenantPicker, Route.Login() to GuideDestination.PatientLogin,
            Route.SignUp to GuideDestination.SignUp, Route.Home to GuideDestination.Home,
            Route.Profile to GuideDestination.Profile, Route.PreRegistration to GuideDestination.PreRegistration,
            Route.MyRequests to GuideDestination.MyRequests, Route.Status to GuideDestination.Status,
        )
        expected.forEach { (route, guide) -> assertEquals(guide, routeGuideDestination(route)) }
        StaffDestination.entries.forEach {
            val guide = when (it) {
                StaffDestination.RECEPTION -> GuideDestination.StaffReception
                StaffDestination.ENCOUNTERS -> GuideDestination.StaffEncounters
                StaffDestination.MEDICINE -> GuideDestination.MedicineDashboard
                StaffDestination.ADMINISTRATION -> GuideDestination.AdministrationDashboard
                StaffDestination.QUEUE -> GuideDestination.StaffQueue
                StaffDestination.REPORTS -> GuideDestination.StaffReports
                else -> GuideDestination.StaffPending
            }
            assertEquals(guide, routeGuideDestination(Route.Staff(it)))
        }
    }

    @Test fun `R18 adding own requests keeps staff role restrictions and patient home semantics`() {
        assertEquals(Route.Home, rootRoute(patient))
        assertTrue(canAccessRoute(patient, Route.Profile))
        assertTrue(canAccessRoute(patient, Route.PreRegistration))
        StaffDestination.entries.forEach { assertFalse(canAccessRoute(patient, Route.Staff(it))) }
    }
}
