package co.asclepio.mobile.feature.account.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.RememberedTenant
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.ui.UiMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TenantPickerViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: AccountTestEnv

    @Before fun setUp() {
        env = AccountTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    private val tenantsJson = """[
        {"id":"t-2","name":"IPS Sin Sedes","sites":[]},
        {"id":"t-1","name":"IPS Uno","sites":[{"id":"s-9","name":"Sede Sur"},{"id":"s-1","name":"Sede Norte"}]},
        {"id":"t-3","name":"IPS Tres","sites":[{"id":"s-3","name":"Unica"}]}
    ]"""

    private suspend fun TenantPickerViewModel.settled() = state.first { it !is TenantPickerUiState.Loading }

    @Test fun `R1 muestra las IPS en el orden recibido y las sedes de la IPS elegida en su orden`() = runTest {
        env.enqueue(200, tenantsJson)
        val vm = TenantPickerViewModel(env.tenantRepository, env.sessionManager)
        val state = vm.settled() as TenantPickerUiState.Success
        assertEquals(listOf("t-2", "t-1", "t-3"), state.tenants.map { it.id })
        assertEquals("/api/v1/public/tenants", env.server.takeRequest().path)
        vm.selectTenant("t-1")
        val selected = vm.state.value as TenantPickerUiState.Success
        assertEquals(listOf("Sede Sur", "Sede Norte"), selected.selectedTenant?.sites?.map { it.name })
        assertFalse(selected.canContinue)
    }

    @Test fun `R1 una IPS sin sedes esta deshabilitada y no se puede elegir`() = runTest {
        env.enqueue(200, tenantsJson)
        val vm = TenantPickerViewModel(env.tenantRepository, env.sessionManager)
        val state = vm.settled() as TenantPickerUiState.Success
        assertFalse(state.isEnabled(state.tenants[0]))
        assertTrue(state.isEnabled(state.tenants[1]))
        vm.selectTenant("t-2")
        assertNull((vm.state.value as TenantPickerUiState.Success).selectedTenantId)
    }

    @Test fun `R2 sin sede no se puede continuar y al confirmar IPS y sede se guarda la IPS recordada`() = runTest {
        env.sessionManager.refresh()
        env.enqueue(200, tenantsJson)
        val vm = TenantPickerViewModel(env.tenantRepository, env.sessionManager)
        vm.settled()
        vm.selectTenant("t-1")
        vm.confirm()
        runCurrent()
        assertNull(env.store.tenant())
        vm.selectSite("s-1")
        assertTrue((vm.state.value as TenantPickerUiState.Success).canContinue)
        vm.confirm()
        vm.state.first { (it as? TenantPickerUiState.Success)?.confirmed == true }
        val remembered = RememberedTenant("t-1", "IPS Uno", "s-1", "Sede Norte")
        assertEquals(remembered, env.store.tenant())
        assertEquals(SessionState.LoggedOut(remembered), env.sessionManager.state.value)
    }

    @Test fun `R4 una lista vacia muestra el mensaje de sin IPS con reintentar y no permite continuar`() = runTest {
        env.enqueue(200, "[]")
        val vm = TenantPickerViewModel(env.tenantRepository, env.sessionManager)
        assertEquals(TenantPickerUiState.Error(UiMessage(R.string.error_no_tenants), retryEnabled = true), vm.settled())
        env.enqueue(200, tenantsJson)
        vm.retry()
        assertTrue(vm.settled() is TenantPickerUiState.Success)
        assertEquals(2, env.server.requestCount)
    }

    @Test fun `R5 sin conexion o 5xx muestra el mensaje correspondiente con reintentar y no modifica la IPS recordada`() = runTest {
        env.rememberTenant()
        env.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val vm = TenantPickerViewModel(env.tenantRepository, env.sessionManager)
        assertEquals(TenantPickerUiState.Error(UiMessage(R.string.error_network), retryEnabled = true), vm.settled())
        env.enqueue(500, env.problem(500))
        vm.retry()
        assertEquals(TenantPickerUiState.Error(UiMessage(R.string.error_general), retryEnabled = true), vm.settled())
        assertEquals(env.tenant, env.store.tenant())
    }

    @Test fun `R22 un 429 muestra los segundos de Retry-After y deshabilita reintentar durante ese tiempo`() = runTest {
        env.rememberTenant()
        env.enqueue(429, env.problem(429), retryAfter = "30")
        val vm = TenantPickerViewModel(env.tenantRepository, env.sessionManager)
        assertEquals(
            TenantPickerUiState.Error(UiMessage(R.string.error_too_many_requests, listOf(30)), retryEnabled = false),
            vm.settled(),
        )
        vm.retry() // deshabilitado: no envía otra petición
        advanceTimeBy(29_000)
        runCurrent()
        assertFalse((vm.state.value as TenantPickerUiState.Error).retryEnabled)
        assertEquals(1, env.server.requestCount)
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue((vm.state.value as TenantPickerUiState.Error).retryEnabled)
        assertEquals(env.tenant, env.store.tenant())
    }
}
