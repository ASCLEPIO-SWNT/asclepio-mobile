package co.asclepio.mobile.feature.account.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.core.auth.AccessMode
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.feature.account.data.DocumentType
import co.asclepio.mobile.feature.account.data.LoginPrefill
import co.asclepio.mobile.feature.account.data.LoginResult
import co.asclepio.mobile.core.model.Sex
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StaffTransitionRegressionTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: AccountTestEnv
    @Before fun setUp() { env = AccountTestEnv(tmp.root) }
    @After fun tearDown() = env.close()

    @Test fun `R14 cambiar acceso o cerrar limpia el documento pendiente de auto login paciente`() = runTest {
        env.rememberTenant()
        env.accountRepository.setLoginPrefill(LoginPrefill(DocumentType.CC, "private-document", LoginResult.NotPatient))
        assertNotNull(env.accountRepository.loginPrefill.value)
        env.sessionManager.selectAccess(AccessMode.STAFF)
        assertNull(env.accountRepository.loginPrefill.value)
        env.accountRepository.setLoginPrefill(LoginPrefill(DocumentType.CC, "private-document", LoginResult.NotPatient))
        env.sessionManager.logout()
        assertNull(env.accountRepository.loginPrefill.value)
    }

    @Test fun `R15 cambiar acceso durante alta no permite que su respuesta inicie login paciente`() = runTest {
        env.rememberTenant()
        env.enqueue(200, """{"version":"v1","text":"Consentimiento"}""")
        val vm = SignUpViewModel(env.accountRepository, env.sessionManager, env.clock)
        vm.state.first { it.consent is ConsentUiState.Loaded }
        env.server.takeRequest()
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                return MockResponse().setResponseCode(201).setBody("""{"id":"a","patientId":"p"}""")
            }
        }
        vm.updateForm(SignUpForm(DocumentType.CC, "private-document", "Nombre", LocalDate.of(1990,1,1), Sex.X, "password-123", "password-123", true))
        vm.submit()
        assertTrue(withContext(Dispatchers.IO) { received.await(2, TimeUnit.SECONDS) })
        env.sessionManager.selectAccess(AccessMode.STAFF)
        release.countDown()
        // Una nueva petición se observaría inmediatamente en el dispatcher; se espera la respuesta del alta.
        vm.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals(SessionState.StaffEntry, env.sessionManager.state.value)
        assertNull(env.sessionManager.currentToken())
        assertEquals(2, env.server.requestCount) // consentimiento + alta, nunca login
        assertNull(env.accountRepository.loginPrefill.value)
    }
    @Test fun `R15 selector paciente anterior no cambia IPS ni cierra entrada personal posterior`() = runTest {
        env.rememberTenant()
        env.enqueue(200, """[{"id":"other","name":"Otra IPS","sites":[{"id":"site","name":"Sede"}]}]""")
        val vm = TenantPickerViewModel(env.tenantRepository, env.sessionManager)
        vm.state.first { it is TenantPickerUiState.Success }
        vm.selectTenant("other"); vm.selectSite("site")
        env.sessionManager.selectAccess(AccessMode.STAFF)
        vm.confirm()
        vm.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals(SessionState.StaffEntry, env.sessionManager.state.value)
        assertEquals(env.tenant, env.store.tenant())
    }

}
