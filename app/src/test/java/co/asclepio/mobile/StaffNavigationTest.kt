package co.asclepio.mobile

import co.asclepio.mobile.core.auth.*
import org.junit.Test
import org.junit.Assert.*

class StaffNavigationTest {
    private val tenant = RememberedTenant("tenant", "IPS", "site", "Sede")
    private fun staff(role: StaffRole, generation: Long = 1) = SessionState.Authenticated(VerifiedPrincipal.Staff("user", "tenant", "Nombre", role), generation)
    private val patient = SessionState.Authenticated(VerifiedPrincipal.Patient("a", "tenant", "p", "v1", false, tenant), 2)

    @Test fun `R1 bienvenida separa paciente y personal sin registro publico staff`() {
        assertEquals(Route.Welcome, rootRoute(SessionState.Welcome))
        assertEquals(Route.TenantPicker, rootRoute(SessionState.NoTenant))
        assertEquals(Route.Login, rootRoute(SessionState.LoggedOut(tenant)))
        assertEquals(Route.StaffLogin, rootRoute(SessionState.StaffEntry))
        assertFalse(canAccessRoute(SessionState.StaffEntry, Route.SignUp))
    }
    @Test fun `R16 cuatro roles tienen inicio y destinos provisionales exactos`() {
        val expected = mapOf(
            StaffRole.RECEPTION to setOf(StaffDestination.RECEPTION, StaffDestination.ENCOUNTERS),
            StaffRole.NURSE to setOf(StaffDestination.TRIAGE, StaffDestination.QUEUE, StaffDestination.ENCOUNTERS),
            StaffRole.PHYSICIAN to setOf(StaffDestination.MEDICINE, StaffDestination.QUEUE, StaffDestination.ENCOUNTERS),
            StaffRole.ADMIN to setOf(StaffDestination.ADMINISTRATION, StaffDestination.REPORTS, StaffDestination.QUEUE, StaffDestination.ENCOUNTERS),
        )
        for ((role, destinations) in expected) {
            assertEquals(destinations, role.destinations)
            assertEquals(Route.Staff(role.home), rootRoute(staff(role)))
            assertTrue(role.home in destinations)
        }
        assertNotEquals(StaffRole.ADMIN.home, StaffDestination.REPORTS)
        assertNotEquals(StaffRole.PHYSICIAN.home, StaffDestination.QUEUE)
    }
    @Test fun `R17 rutas forzadas ajenas quedan denegadas antes de construir funciones privadas`() {
        for (role in StaffRole.entries) {
            val state = staff(role)
            for (destination in StaffDestination.entries) {
                assertEquals(destination in role.destinations, canAccessRoute(state, Route.Staff(destination)))
            }
            assertFalse(canAccessRoute(state, Route.Profile))
            assertFalse(canAccessRoute(state, Route.PreRegistration))
            assertFalse(canAccessRoute(state, Route.Home))
        }
        for (destination in StaffDestination.entries) assertFalse(canAccessRoute(patient, Route.Staff(destination)))
        for (state in listOf(SessionState.Loading, SessionState.Verifying, SessionState.Welcome)) {
            assertFalse(canAccessRoute(state, Route.Home))
            assertFalse(canAccessRoute(state, Route.Staff(StaffDestination.REPORTS)))
        }
    }
    @Test fun `R14 misma clase de principal con generacion nueva destruye identidad de navegacion anterior`() {
        assertNotEquals(navigationIdentity(staff(StaffRole.NURSE, 1)), navigationIdentity(staff(StaffRole.NURSE, 2)))
        assertNotEquals(navigationIdentity(patient), navigationIdentity(SessionState.LoggedOut(tenant)))
        assertEquals(navigationIdentity(patient), navigationIdentity(patient.copy()))
    }
    @Test fun `R19 paciente conserva inicio perfil y preregistro y no monta personal`() {
        assertEquals(Route.Home, rootRoute(patient))
        assertTrue(canAccessRoute(patient, Route.Home))
        assertTrue(canAccessRoute(patient, Route.Profile))
        assertTrue(canAccessRoute(patient, Route.PreRegistration))
        assertFalse(canAccessRoute(patient, Route.StaffLogin))
    }
}
