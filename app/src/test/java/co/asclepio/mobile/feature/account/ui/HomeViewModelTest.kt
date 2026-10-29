package co.asclepio.mobile.feature.account.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.auth.StoredSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: AccountTestEnv

    @Before fun setUp() {
        env = AccountTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    private suspend fun TestScope.home(meCode: Int = 200, me: String = AccountTestEnv.me()): HomeViewModel {
        env.rememberTenant()
        env.enqueue(200, AccountTestEnv.LOGIN_OK)
        env.enqueue(meCode, me)
        env.accountRepository.login(env.tenant.tenantId, co.asclepio.mobile.feature.account.data.DocumentType.CC, "123", "p")
        val vm = HomeViewModel(env.sessionManager)
        vm.state.first { !it.verifying }
        runCurrent()
        return vm
    }

    @Test fun `R31 muestra IPS y sede recordadas y el aviso de identidad mientras identityVerified es false`() = runTest {
        val vm = home()
        val state = vm.state.value
        assertEquals(env.tenant, state.tenant)
        assertTrue(state.showIdentityNotice)
        assertEquals("/api/v1/auth/patient/login", env.server.takeRequest().path)
        assertEquals("Bearer ${AccountTestEnv.TOKEN}", env.server.takeRequest().getHeader("Authorization"))
        assertEquals(2, env.server.requestCount) // Home no vuelve a consultar /me al rotar.
    }

    @Test fun `R31 con identidad verificada no muestra el aviso`() = runTest {
        assertFalse(home(me = AccountTestEnv.me(identityVerified = true)).state.value.showIdentityNotice)
    }

    @Test fun `R31 el estado de inicio no contiene documento ni nombre del paciente`() = runTest {
        val text = home().state.value.toString()
        assertFalse(text.contains("p-1"))
        assertFalse(text.contains("a-1"))
    }

    @Test fun `R28 al abrir el inicio con un 401 de me se borra la sesion`() = runTest {
        home(401, env.problem(401))
        assertEquals(SessionState.LoggedOut(env.tenant), env.sessionManager.state.value)
        assertEquals(StoredSession.None, env.store.session())
    }

    @Test fun `R30 cerrar sesion pide confirmacion y cancelar conserva la sesion`() = runTest {
        val vm = home()
        vm.requestLogout()
        assertTrue(vm.state.value.showLogoutDialog)
        vm.cancelLogout()
        runCurrent()
        assertFalse(vm.state.value.showLogoutDialog)
        co.asclepio.mobile.core.auth.assertPatientSession(env.sessionManager, env.tenant)
        assertEquals(AccountTestEnv.TOKEN, env.sessionManager.currentToken())
    }

    @Test fun `R30 confirmar deja el estado en LoggedOut, borra el token, conserva la IPS y no hace peticiones`() = runTest {
        val vm = home()
        val before = env.server.requestCount
        vm.requestLogout()
        vm.confirmLogout()
        env.sessionManager.state.first { it is SessionState.LoggedOut }
        assertEquals(SessionState.LoggedOut(env.tenant), env.sessionManager.state.value)
        assertNull(env.sessionManager.currentToken())
        assertEquals(StoredSession.None, env.store.session())
        assertEquals(env.tenant, env.store.tenant())
        assertEquals(before, env.server.requestCount)
    }

    @Test fun `R32 tras el login el archivo de sesion no contiene la contrasena ni el documento`() = runTest {
        env.rememberTenant()
        env.enqueue(200, AccountTestEnv.LOGIN_OK)
        env.enqueue(200, AccountTestEnv.me())
        val vm = LoginViewModel(env.accountRepository, env.sessionManager)
        vm.updateForm(LoginForm(co.asclepio.mobile.feature.account.data.DocumentType.CC, "DOC-998877", "clave-secreta-77"))
        vm.submit()
        env.sessionManager.state.first { it is SessionState.Authenticated }
        val files = tmp.root.walkTopDown().filter(File::isFile).toList()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val raw = f.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(f.name, raw.contains("clave-secreta-77"))
            assertFalse(f.name, raw.contains("DOC-998877"))
        }
    }
}
