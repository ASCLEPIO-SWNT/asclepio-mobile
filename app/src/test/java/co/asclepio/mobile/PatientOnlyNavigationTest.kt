package co.asclepio.mobile

import co.asclepio.mobile.core.auth.RememberedTenant
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.auth.SessionTestEnv
import co.asclepio.mobile.core.auth.VerifiedPrincipal
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.core.ui.guide.GuideDestination
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** La app es solo del paciente (DT-01-mobile): entrada directa, rutas y guía cerradas al paciente. */
class PatientOnlyNavigationTest {
    @get:Rule val tmp = TemporaryFolder()
    private var env: SessionTestEnv? = null
    @After fun tearDown() { env?.close() }

    private val tenant = RememberedTenant(SessionTestEnv.TENANT, "IPS Uno", "site", "Sede Norte")
    private val patient = SessionState.Authenticated(VerifiedPrincipal.Patient("a", SessionTestEnv.TENANT, "p", "v1", false, tenant), 1)
    private val patientRoutes = setOf(Route.Home, Route.Profile, Route.PreRegistration, Route.MyRequests)
    private val entryRoutes = setOf(Route.TenantPicker, Route.Login(), Route.SignUp, Route.Status)

    @Test fun `R3 sin sesion ni IPS recordada la app abre la seleccion de IPS sin eleccion de acceso`() = runTest {
        val env = SessionTestEnv(tmp.root).also { env = it }
        env.manager.refresh()
        assertEquals(SessionState.NoTenant, env.manager.state.value)
        assertEquals(Route.TenantPicker, rootRoute(env.manager.state.value))
    }

    @Test fun `R3 con IPS recordada y sin sesion la app abre el inicio de sesion del paciente`() = runTest {
        val env = SessionTestEnv(tmp.root).also { env = it }
        env.store.saveTenant(tenant)
        env.manager.refresh()
        assertEquals(SessionState.LoggedOut(tenant), env.manager.state.value)
        assertEquals(Route.Login(), rootRoute(env.manager.state.value))
    }

    @Test fun `R3 al cerrar sesion la raiz vuelve directamente al inicio de sesion del paciente`() = runTest {
        val env = SessionTestEnv(tmp.root).also { env = it }
        env.activate()
        assertEquals(Route.Home, rootRoute(env.manager.state.value))
        env.manager.logout()
        assertEquals(Route.Login(), rootRoute(env.manager.state.value))
        assertEquals(0, env.server.requestCount)
    }

    @Test fun `R4 las rutas de navegacion son exactamente las del paciente`() {
        val routes = Route::class.java.declaredClasses.filter { Route::class.java.isAssignableFrom(it) }.map { it.simpleName }.toSet()
        assertEquals(setOf("TenantPicker", "Login", "SignUp", "Home", "Profile", "MyRequests", "PreRegistration", "Status"), routes)
    }

    @Test fun `R4 los destinos de guia son las pantallas del paciente verificacion y preferencias`() {
        assertEquals(
            setOf("TenantPicker", "PatientLogin", "SignUp", "Home", "Profile", "MyRequests", "PreRegistration", "Status", "SessionVerification", "Preferences"),
            GuideDestination.entries.map { it.name }.toSet(),
        )
        val expected = mapOf(
            Route.TenantPicker to GuideDestination.TenantPicker, Route.Login() to GuideDestination.PatientLogin,
            Route.SignUp to GuideDestination.SignUp, Route.Home to GuideDestination.Home,
            Route.Profile to GuideDestination.Profile, Route.PreRegistration to GuideDestination.PreRegistration,
            Route.MyRequests to GuideDestination.MyRequests, Route.Status to GuideDestination.Status,
        )
        expected.forEach { (route, guide) -> assertEquals(guide, routeGuideDestination(route)) }
    }

    @Test fun `R4 el paciente solo monta sus rutas y la entrada solo las de entrada`() {
        patientRoutes.forEach { assertTrue(canAccessRoute(patient, it)) }
        entryRoutes.forEach { assertFalse(canAccessRoute(patient, it)) }
        val loggedOut = SessionState.LoggedOut(tenant)
        listOf(Route.TenantPicker, Route.Login(), Route.SignUp).forEach { assertTrue(canAccessRoute(loggedOut, it)) }
        assertEquals(BuildConfig.DEBUG, canAccessRoute(loggedOut, Route.Status))
        patientRoutes.forEach { assertFalse(canAccessRoute(loggedOut, it)) }
        (patientRoutes + entryRoutes).forEach { route ->
            assertEquals(route == Route.TenantPicker, canAccessRoute(SessionState.NoTenant, route))
            assertFalse(canAccessRoute(SessionState.Loading, route))
            assertFalse(canAccessRoute(SessionState.Verifying, route))
            assertFalse(canAccessRoute(SessionState.VerificationError(UiMessage(R.string.error_network)), route))
        }
    }

    @Test fun `R4 ningun texto ofrece acceso de personal ni cambiar tipo de acceso`() {
        val keys = R.string::class.java.fields.map { it.name }
        val retired = setOf(
            "access_choose", "access_patient", "access_patient_intro", "access_patient_action",
            "access_staff", "access_staff_intro", "access_staff_action",
            "change_access", "change_access_dialog_title", "change_access_dialog_body",
        )
        assertEquals(emptyList<String>(), keys.filter { it in retired || it.startsWith("guide_staff_") || it.startsWith("guide_welcome_") || it.startsWith("staff_") })
    }
}
