package co.asclepio.mobile.core.auth

import co.asclepio.mobile.core.network.createRetrofit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant

/** Regresiones F-01: el token legado ahora se verifica antes de la raíz privada. */
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
    @After fun tearDown() { manager.close(); ds.close(); server.shutdown() }

    @Test fun `R1 sin sesion el estado es bienvenida y paciente conserva selector de IPS`() = runTest {
        manager.refresh()
        assertEquals(SessionState.Welcome, manager.state.value)
        manager.selectAccess(AccessMode.PATIENT)
        assertEquals(SessionState.NoTenant, manager.state.value)
    }
    @Test fun `R3 con IPS recordada paciente entra en LoggedOut con esa IPS`() = runTest {
        store.saveTenant(tenant)
        manager.refresh()
        manager.selectAccess(AccessMode.PATIENT)
        assertEquals(SessionState.LoggedOut(tenant), manager.state.value)
        assertNull(manager.currentToken())
    }
    private suspend fun loggedIn() {
        manager.saveTenant(tenant)
        val attempt = manager.beginLogin(AccessMode.PATIENT)
        check(manager.completeLogin(attempt, "tok", expiresAt, MeDto("PATIENT", "a", "t-1", "p", "v1", false)))
    }
    @Test fun `R27 sesion guardada vigente verifica me y una vencida al abrir se borra`() = runTest {
        store.saveTenant(tenant)
        store.saveSession("tok", expiresAt)
        server.enqueue(MockResponse().setBody(patientMe))
        manager.refresh()
        assertPatientSession(manager, tenant)
        assertEquals("tok", manager.currentToken())
        clock.now = expiresAt
        manager.refresh()
        assertEquals(SessionState.LoggedOut(tenant), manager.state.value)
        assertEquals(StoredSession.None, store.session())
        assertNull(manager.currentToken())
    }
    @Test fun `R27 al volver a primer plano vencida se borra y se pide login`() = runTest {
        loggedIn()
        clock.now = expiresAt.plus(Duration.ofMinutes(1))
        manager.refresh()
        assertEquals(SessionState.LoggedOut(tenant), manager.state.value)
        assertEquals(StoredSession.None, store.session())
    }
    @Test fun `R27 un token que no se puede descifrar borra la sesion`() = runTest {
        store.saveTenant(tenant)
        SessionStore(ds.dataStore, BrokenTokenCipher()).saveSession("tok", expiresAt)
        val broken = SessionManager(SessionStore(ds.dataStore, BrokenTokenCipher()), meApi, clock)
        broken.selectAccess(AccessMode.PATIENT)
        // selectAccess limpia cualquier sesión anterior; se simula el token ilegible tras elegir entrada.
        SessionStore(ds.dataStore, BrokenTokenCipher()).saveSession("tok", expiresAt)
        broken.refresh()
        assertEquals(SessionState.LoggedOut(tenant), broken.state.value)
        assertEquals(StoredSession.None, store.session())
        broken.close()
    }
    @Test fun `R28 al restaurar 401 me borra sesion con Bearer explicito`() = runTest {
        store.saveTenant(tenant)
        store.saveSession("tok", expiresAt)
        server.enqueue(MockResponse().setResponseCode(401))
        manager.refresh()
        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
        assertEquals(StoredSession.None, store.session())
        assertFalse(manager.state.value is SessionState.Authenticated)
    }
    @Test fun `R28 me 5xx conserva token pero bloquea contenido hasta reintentar`() = runTest {
        store.saveTenant(tenant)
        store.saveSession("tok", expiresAt)
        server.enqueue(MockResponse().setResponseCode(503))
        manager.refresh()
        assertTrue(manager.state.value is SessionState.VerificationError)
        assertNull(manager.currentToken())
        assertEquals(StoredSession.Present("tok", expiresAt), store.session())
    }
    @Test fun `R28 me 200 devuelve perfil verificado antes del inicio y no se duplica en refresh`() = runTest {
        store.saveTenant(tenant)
        store.saveSession("tok", expiresAt)
        server.enqueue(MockResponse().setBody(patientMe))
        manager.refresh()
        assertEquals(false, manager.patientPrincipal()?.identityVerified)
        val state = manager.state.value
        manager.refresh()
        assertEquals(state, manager.state.value)
        assertEquals(1, server.requestCount)
    }
    @Test fun `R30 logout borra token conserva IPS y no llama API`() = runTest {
        loggedIn()
        manager.logout()
        assertEquals(SessionState.LoggedOut(tenant), manager.state.value)
        assertNull(manager.currentToken())
        assertEquals(StoredSession.None, store.session())
        assertEquals(tenant, store.tenant())
        assertEquals(0, server.requestCount)
    }
    @Test fun `R2 guardar IPS deja LoggedOut con esa IPS`() = runTest {
        manager.refresh()
        manager.saveTenant(tenant)
        assertEquals(SessionState.LoggedOut(tenant), manager.state.value)
        assertEquals(tenant, store.tenant())
    }
}
