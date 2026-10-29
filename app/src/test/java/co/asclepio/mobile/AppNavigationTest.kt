package co.asclepio.mobile

import androidx.navigation3.runtime.NavKey
import co.asclepio.mobile.core.auth.RememberedTenant
import co.asclepio.mobile.core.auth.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavigationTest {
    private val tenant = RememberedTenant("t-1", "IPS Uno", "s-1", "Sede Norte")

    @Test fun `R1 sin IPS recordada la app abre en la eleccion de IPS`() {
        assertEquals(Route.TenantPicker, rootRoute(SessionState.NoTenant))
    }

    @Test fun `R3 con IPS recordada y sin sesion la app abre en el login`() {
        assertEquals(Route.Login(), rootRoute(SessionState.LoggedOut(tenant)))
    }

    @Test fun `R31 con sesion activa la app abre en el inicio`() {
        assertEquals(Route.Home, rootRoute(SessionState.Authenticated(co.asclepio.mobile.core.auth.VerifiedPrincipal.Patient("a", "t-1", "p", "v1", false, tenant), 1)))
    }

    @Test fun `R27 mientras se carga la sesion no hay pantalla raiz`() {
        assertNull(rootRoute(SessionState.Loading))
    }

    @Test fun `R12 tras el 201 se vuelve al inicio quitando el formulario de la pila`() {
        val stack = mutableListOf<NavKey>(Route.Home, Route.PreRegistration)
        stack.popTo(Route.Home)
        assertEquals(listOf<NavKey>(Route.Home), stack)
    }

    @Test fun `R13 Ver mi estado vuelve al inicio y sin inicio en la pila no cambia nada`() {
        val stack = mutableListOf<NavKey>(Route.Home, Route.Profile, Route.PreRegistration)
        stack.popTo(Route.Home)
        assertEquals(listOf<NavKey>(Route.Home), stack)
        val other = mutableListOf<NavKey>(Route.Login(), Route.SignUp)
        other.popTo(Route.Home)
        assertEquals(listOf<NavKey>(Route.Login(), Route.SignUp), other)
    }

    @Test fun `R23 tras un 401 la sesion queda LoggedOut y la raiz vuelve al login`() {
        assertEquals(Route.Login(), rootRoute(SessionState.LoggedOut(tenant)))
    }
}
