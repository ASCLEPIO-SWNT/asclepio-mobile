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
