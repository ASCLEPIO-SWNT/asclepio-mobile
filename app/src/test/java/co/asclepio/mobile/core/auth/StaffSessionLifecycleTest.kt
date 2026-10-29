package co.asclepio.mobile.core.auth

import co.asclepio.mobile.StaffTestEnv
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.feature.patients.data.PatientProfileApi
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StaffSessionLifecycleTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffTestEnv
    @After fun tearDown() { if (::env.isInitialized) env.close() }

    @Test fun `R10 restauracion staff sin IPS verifica me antes de abrir y refresh estable no lo duplica`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        env.store.saveSession(StaffTestEnv.TOKEN, StaffTestEnv.EXPIRES)
        env.enqueue(200, StaffTestEnv.staffMe())
        env.manager.refresh()
        val authenticated = env.manager.state.value as SessionState.Authenticated
        assertTrue(authenticated.principal is VerifiedPrincipal.Staff)
        val request = env.server.takeRequest()
        assertEquals("Bearer ${StaffTestEnv.TOKEN}", request.getHeader("Authorization"))
        env.manager.refresh()
        assertEquals(authenticated, env.manager.state.value)
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R10 token paciente anterior requiere IPS recordada coincidente y me completo`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        env.store.saveSession(StaffTestEnv.TOKEN, StaffTestEnv.EXPIRES)
        env.enqueue(200, StaffTestEnv.patientMe())
        env.manager.refresh()
        assertEquals(StoredSession.None, env.store.session())
        env.manager.saveTenant(env.tenant)
        env.store.saveSession(StaffTestEnv.TOKEN, StaffTestEnv.EXPIRES)
        env.enqueue(200, StaffTestEnv.patientMe())
        env.manager.refresh()
        assertTrue((env.manager.state.value as SessionState.Authenticated).principal is VerifiedPrincipal.Patient)
    }

    @Test fun `R11 5xx y red al restaurar conservan cifrado sin perfil ni acceso y permiten reintentar`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        env.store.saveSession(StaffTestEnv.TOKEN, StaffTestEnv.EXPIRES)
        env.enqueue(503)
        env.manager.refresh()
        assertTrue(env.manager.state.value is SessionState.VerificationError)
        assertNull(env.manager.currentToken())
        assertTrue(env.store.session() is StoredSession.Present)
        env.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        env.manager.refresh()
        assertTrue(env.manager.state.value is SessionState.VerificationError)
        assertTrue(env.store.session() is StoredSession.Present)
        env.enqueue(200, StaffTestEnv.staffMe())
        env.manager.refresh()
        assertTrue(env.manager.state.value is SessionState.Authenticated)
    }

    @Test fun `R12 expiracion invalida ilegible vencida y timer cierran sin contenido privado`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        env.store.saveSession(StaffTestEnv.TOKEN, env.clock.instant())
        env.manager.refresh()
        assertEquals(StoredSession.None, env.store.session())
        assertFalse(env.manager.state.value is SessionState.Authenticated)
        val attempt = env.manager.beginLogin(AccessMode.STAFF)
        val expiry = env.clock.instant().plusSeconds(10)
        assertTrue(env.manager.completeLogin(attempt, "token", expiry, StaffTestEnv.staffMeDto()))
        runCurrent()
        env.clock.now = expiry
        advanceTimeBy(10_000)
        runCurrent()
        env.manager.state.first { it == SessionState.StaffEntry }
        assertEquals(StoredSession.None, env.store.session())
        assertNull(env.manager.currentToken())
        assertEquals(SessionState.StaffEntry, env.manager.state.value)
    }

    @Test fun `R13 un 401 vigente cierra centralmente conserva IPS y no llama logout HTTP`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        env.manager.saveTenant(env.tenant)
        env.activate()
        env.enqueue(401)
        val result = env.manager.authenticatedCall(AccessMode.STAFF) { env.meApi.meWithToken(it.authorization) }
        assertTrue(result is ApiResult.Failure)
        assertEquals(SessionState.StaffEntry, env.manager.state.value)
        assertEquals(StoredSession.None, env.store.session())
        assertEquals(env.tenant, env.store.tenant())
        assertEquals("/api/v1/me", env.server.takeRequest().path)
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R14 logout y cambio de acceso invalidan incluso intentos pendientes y conservan solo IPS publica`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        env.manager.saveTenant(env.tenant)
        env.manager.selectAccess(AccessMode.STAFF)
        val attempt = env.manager.beginLogin(AccessMode.STAFF)
        env.manager.selectAccess(AccessMode.PATIENT)
        assertFalse(env.manager.isCurrent(attempt.generation))
        try {
            env.manager.completeLogin(attempt, "old", StaffTestEnv.EXPIRES, StaffTestEnv.staffMeDto())
            fail("No acepta login invalidado")
        } catch (_: SessionInvalidated) { }
        assertEquals(SessionState.LoggedOut(env.tenant), env.manager.state.value)
        assertEquals(StoredSession.None, env.store.session())
    }

    @Test fun `R15 respuesta y 401 antiguos no publican ni cierran una sesion posterior`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        for (status in listOf(200, 401)) {
        env.activate(token = "token-A")
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                return MockResponse().setResponseCode(status).setBody(if (status == 200) StaffTestEnv.staffMe() else "")
            }
        }
        val old = async {
            try { env.manager.authenticatedCall(AccessMode.STAFF) { env.meApi.meWithToken(it.authorization) }; false }
            catch (_: SessionInvalidated) { true }
        }
        assertTrue(withContext(Dispatchers.IO) { received.await(2, TimeUnit.SECONDS) })
        env.activate(token = "token-B", role = "ADMIN")
        val newer = env.manager.state.value
        release.countDown()
        assertTrue(old.await())
        assertEquals(newer, env.manager.state.value)
        assertEquals("token-B", env.manager.currentToken())
        assertEquals("Bearer token-A", env.server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun `R18 una operacion suspendida conserva su Bearer y no obtiene el de la nueva sesion`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        env.activate(AccessMode.PATIENT, "patient-A")
        val captured = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val api = env.retrofit.create(PatientProfileApi::class.java)
        val old = async {
            try {
                env.manager.patientCall { bearer ->
                    captured.complete(Unit); resume.await()
                    api.profile(bearer)
                }; false
            } catch (_: SessionInvalidated) { true }
        }
        captured.await()
        env.activate(AccessMode.PATIENT, "patient-B")
        env.enqueue(200, co.asclepio.mobile.PatientTestEnv.profile())
        resume.complete(Unit)
        assertTrue(old.await())
        assertEquals("Bearer patient-A", env.server.takeRequest().getHeader("Authorization"))
        assertEquals("patient-B", env.manager.currentToken())
    }

    @Test fun `R19 staff no ejecuta funciones de paciente ni paciente llamadas staff`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        env.activate()
        var calls = 0
        try { env.manager.patientCall { calls++; "private" }; fail("Debe negar paciente") } catch (_: SessionInvalidated) { }
        env.activate(AccessMode.PATIENT)
        try { env.manager.authenticatedCall(AccessMode.STAFF) { calls++; "private" }; fail("Debe negar staff") } catch (_: SessionInvalidated) { }
        assertEquals(0, calls)
        assertEquals(0, env.server.requestCount)
    }
}
