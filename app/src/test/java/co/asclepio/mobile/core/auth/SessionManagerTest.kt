package co.asclepio.mobile.core.auth

import co.asclepio.mobile.core.network.createRetrofit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant

class SessionManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var ds: TestDataStore
    private lateinit var store: SessionStore
    private lateinit var manager: SessionManager
    private lateinit var meApi: MeApi
    private val clock = MutableClock(Instant.parse("2026-10-26T15:00:00Z"))
    private val tenant = RememberedTenant("t-1", "IPS Uno", "s-1", "Sede Norte")
    private val expiresAt = Instant.parse("2026-10-26T23:00:00Z")
    private val patientMe = """{"kind":"PATIENT","id":"a","tenantId":"t-1","patientId":"p","consentVersion":"v1","identityVerified":false}"""

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        ds = TestDataStore(tmp.root.resolve("s.preferences_pb"))
        store = SessionStore(ds.dataStore, FakeTokenCipher())
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor { manager.currentToken() }).build()
        meApi = createRetrofit(server.url("/").toString(), client).create(MeApi::class.java)
        manager = SessionManager(store, meApi, clock)
    }

    @After fun tearDown() {
        ds.close()
        server.shutdown()
    }

    @Test fun `R1 sin IPS recordada el estado es NoTenant`() = runTest {
        manager.refresh()
        assertEquals(SessionState.NoTenant, manager.state.value)
    }

    @Test fun `R3 con IPS recordada y sin sesion el estado es LoggedOut con esa IPS`() = runTest {
        store.saveTenant(tenant)
        manager.refresh()
        assertEquals(SessionState.LoggedOut(tenant), manager.state.value)
        assertNull(manager.currentToken())
    }

    @Test fun `R27 una sesion vigente se carga y una vencida al abrir se borra`() = runTest {
        store.saveTenant(tenant)
        store.saveSession("tok", expiresAt)
        manager.refresh()
        assertEquals(SessionState.LoggedIn(tenant), manager.state.value)
        assertEquals("tok", manager.currentToken())

        clock.now = expiresAt // no es posterior a la hora del dispositivo
        manager.refresh()
        assertEquals(SessionState.LoggedOut(tenant), manager.state.value)
        assertEquals(StoredSession.None, store.session())
        assertNull(manager.currentToken())
    }

    @Test fun `R27 al volver a primer plano con la sesion vencida se borra y se pide iniciar sesion`() = runTest {
        store.saveTenant(tenant)
        manager.startSession("tok", expiresAt)
        assertEquals(SessionState.LoggedIn(tenant), manager.state.value)
        clock.now = expiresAt.plus(Duration.ofMinutes(1))
        manager.refresh() // ON_START
        assertEquals(SessionState.LoggedOut(tenant), manager.state.value)
        assertEquals(StoredSession.None, store.session())
    }

    @Test fun `R27 un token guardado que no se puede descifrar borra la sesion`() = runTest {
        store.saveTenant(tenant)
        SessionStore(ds.dataStore, BrokenTokenCipher()).saveSession("tok", expiresAt)
        val broken = SessionManager(SessionStore(ds.dataStore, BrokenTokenCipher()), meApi, clock)
        broken.refresh()
        assertEquals(SessionState.LoggedOut(tenant), broken.state.value)
        assertEquals(StoredSession.None, store.session())
    }

    private suspend fun loggedIn() {
        store.saveTenant(tenant)
        store.saveSession("tok", expiresAt)
        manager.refresh()
    }

    @Test fun `R28 al abrir con sesion activa pide me con el token y un 401 borra la sesion`() = runTest {
        loggedIn()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"type":"about:blank","title":"Unauthorized","status":401}"""))
        assertNull(manager.verifySession())
        val recorded = server.takeRequest()
        assertEquals("/api/v1/me", recorded.path)
        assertEquals("Bearer tok", recorded.getHeader("Authorization"))
        assertEquals(SessionState.LoggedOut(tenant), manager.state.value)
        assertEquals(StoredSession.None, store.session())
    }

    @Test fun `R28 si me falla con 5xx o sin red se conserva la sesion`() = runTest {
        loggedIn()
        server.enqueue(MockResponse().setResponseCode(503))
        assertNull(manager.verifySession())
        assertEquals(SessionState.LoggedIn(tenant), manager.state.value)
        server.shutdown() // sin respuesta HTTP
        assertNull(manager.verifySession())
        assertEquals(SessionState.LoggedIn(tenant), manager.state.value)
        assertEquals(StoredSession.Present("tok", expiresAt), store.session())
    }

    @Test fun `R28 si me responde 200 se conserva la sesion y se devuelve el perfil`() = runTest {
        loggedIn()
        server.enqueue(MockResponse().setBody(patientMe))
        assertEquals(false, manager.verifySession()?.identityVerified)
        assertEquals(SessionState.LoggedIn(tenant), manager.state.value)
    }

    @Test fun `R30 cerrar sesion borra token y expiracion de memoria y disco, conserva la IPS y no llama a la API`() = runTest {
        loggedIn()
        manager.logout()
        assertEquals(SessionState.LoggedOut(tenant), manager.state.value)
        assertNull(manager.currentToken())
        assertEquals(StoredSession.None, store.session())
        assertEquals(tenant, store.tenant())
        assertEquals(0, server.requestCount)
    }

    @Test fun `R2 guardar la IPS deja el estado en LoggedOut con esa IPS`() = runTest {
        manager.refresh()
        manager.saveTenant(tenant)
        assertEquals(SessionState.LoggedOut(tenant), manager.state.value)
        assertEquals(tenant, store.tenant())
    }
}
