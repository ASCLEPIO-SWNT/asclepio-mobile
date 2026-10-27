package co.asclepio.mobile

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
        assertEquals(Route.Login, rootRoute(SessionState.LoggedOut(tenant)))
    }

    @Test fun `R31 con sesion activa la app abre en el inicio`() {
        assertEquals(Route.Home, rootRoute(SessionState.LoggedIn(tenant)))
    }

    @Test fun `R27 mientras se carga la sesion no hay pantalla raiz`() {
        assertNull(rootRoute(SessionState.Loading))
    }
}
