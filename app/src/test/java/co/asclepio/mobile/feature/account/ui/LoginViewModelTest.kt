package co.asclepio.mobile.feature.account.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.auth.StoredSession
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.account.data.DocumentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class LoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: AccountTestEnv

    @Before fun setUp() {
        env = AccountTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    private val form = LoginForm(DocumentType.CC, "  123456 ", "secreta-123")

    private suspend fun TestScope.ready(): LoginViewModel {
        env.rememberTenant()
        val vm = LoginViewModel(env.accountRepository, env.sessionManager)
        runCurrent()
        return vm
    }

    private suspend fun LoginViewModel.settled() = state.first { !it.submitting }

    @Test fun `R3 muestra la IPS y la sede recordadas y los campos de documento empiezan vacios`() = runTest {
        val vm = ready()
        assertEquals(env.tenant, vm.state.value.tenant)
        assertEquals(LoginForm(), vm.state.value.form)
    }

    @Test fun `R6 confirming the same tenant requires a fresh login entry and never rearms the old draft`() = runTest {
        val old = ready()
        old.updateForm(form)
        env.rememberTenant()
        runCurrent()
        old.submit()
        runCurrent()
        assertEquals(0, env.server.requestCount)

        val fresh = LoginViewModel(env.accountRepository, env.sessionManager)
        runCurrent()
        assertEquals(LoginForm(), fresh.state.value.form)
        fresh.updateForm(form)
        env.enqueue(401, env.problem(401))
        fresh.submit()
        fresh.settled()
        assertEquals(1, env.server.requestCount)
        assertFalse(fresh.state.value.submitting)
    }

    @Test fun `R17 con campos vacios marca Campo obligatorio y no envia la peticion`() = runTest {
        val vm = ready()
        vm.updateForm(LoginForm(null, " ", ""))
        vm.submit()
        runCurrent()
        val required = UiMessage(R.string.error_required)
        assertEquals(
            mapOf(LoginField.DocumentType to required, LoginField.DocumentNumber to required, LoginField.Password to required),
            vm.state.value.fieldErrors,
        )
        assertEquals(0, env.server.requestCount)
    }

    @Test fun `R17 envia el login con el tenantId recordado y el documento sin espacios en los extremos`() = runTest {
        val vm = ready()
        vm.updateForm(form)
        env.enqueue(401, env.problem(401))
        vm.submit()
        vm.settled()
        val recorded = env.server.takeRequest()
        assertEquals("/api/v1/auth/patient/login", recorded.path)
        assertNull(recorded.getHeader("Authorization"))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals(JsonPrimitive("t-1"), body["tenantId"])
        assertEquals(JsonPrimitive("CC"), body["documentType"])
        assertEquals(JsonPrimitive("123456"), body["documentNumber"])
        assertEquals(JsonPrimitive("secreta-123"), body["password"])
    }

    @Test fun `R18 un 401 muestra el mismo mensaje generico para cuerpos distintos y vacia la contrasena`() = runTest {
        val vm = ready()
        val bodies = listOf(
            env.problem(401),
            """{"type":"about:blank","title":"Account locked","status":401,"detail":"bloqueada hasta las 10:15"}""",
            "",
            "<html>no</html>",
        )
        for (body in bodies) {
            vm.updateForm(form)
            env.enqueue(401, body)
            vm.submit()
            val state = vm.settled()
            assertEquals(body, UiMessage(R.string.error_login_invalid), state.message)
            assertEquals("", state.form.password)
            assertEquals("123456", state.form.documentNumber.trim())
        }
    }

    @Test fun `R19 con 200 pide me con Bearer y si es PATIENT de la IPS guarda la sesion`() = runTest {
        val vm = ready()
        vm.updateForm(form)
        env.enqueue(200, AccountTestEnv.LOGIN_OK)
        env.enqueue(200, AccountTestEnv.me())
        vm.submit()
        env.sessionManager.state.first { it is SessionState.Authenticated }
        env.server.takeRequest()
        val me = env.server.takeRequest()
        assertEquals("/api/v1/me", me.path)
        assertEquals("Bearer ${AccountTestEnv.TOKEN}", me.getHeader("Authorization"))
        assertEquals(StoredSession.Present(AccountTestEnv.TOKEN, Instant.parse(AccountTestEnv.EXPIRES_AT)), env.store.session())
        assertEquals(AccountTestEnv.TOKEN, env.sessionManager.currentToken())
    }

    @Test fun `R20 si me es STAFF o de otra IPS descarta el token y muestra que no es de paciente`() = runTest {
        val vm = ready()
        for (me in listOf(AccountTestEnv.me(kind = "STAFF"), AccountTestEnv.me(tenantId = "t-otra"), AccountTestEnv.me(kind = "OTRO"))) {
            vm.updateForm(form)
            env.enqueue(200, AccountTestEnv.LOGIN_OK)
            env.enqueue(200, me)
            vm.submit()
            assertEquals(me, notPatientMessage, vm.settled().message)
            assertEquals(StoredSession.None, env.store.session())
            assertNull(env.sessionManager.currentToken())
            assertEquals(SessionState.LoggedOut(env.tenant), env.sessionManager.state.value)
        }
    }

    @Test fun `R21 si me falla con 401, 5xx o cuerpo no valido descarta el token y muestra el mensaje general`() = runTest {
        val vm = ready()
        for ((code, body) in listOf(401 to env.problem(401), 503 to env.problem(503), 200 to """{"kind":1}""")) {
            vm.updateForm(form)
            env.enqueue(200, AccountTestEnv.LOGIN_OK)
            env.enqueue(code, body)
            vm.submit()
            assertEquals("$code", UiMessage(R.string.error_general), vm.settled().message)
            assertEquals(StoredSession.None, env.store.session())
            assertNull(env.sessionManager.currentToken())
        }
    }

    @Test fun `R21 si me falla sin conexion descarta el token y muestra sin conexion`() = runTest {
        env.close()
        env = AccountTestEnv(tmp.newFolder(), readTimeoutMs = 300) // sin respuesta HTTP: SocketTimeoutException
        val vm = ready()
        vm.updateForm(form)
        env.enqueue(200, AccountTestEnv.LOGIN_OK)
        env.server.enqueue(okhttp3.mockwebserver.MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        vm.submit()
        assertEquals(UiMessage(R.string.error_network), vm.settled().message)
        assertEquals(StoredSession.None, env.store.session())
    }

    @Test fun `R22 un 429 en el login muestra los segundos y deshabilita el boton ese tiempo`() = runTest {
        val vm = ready()
        vm.updateForm(form)
        env.enqueue(429, env.problem(429), retryAfter = "5")
        vm.submit()
        val state = vm.settled()
        assertEquals(UiMessage(R.string.error_too_many_requests, listOf(5)), state.message)
        assertFalse(state.canSubmit)
        vm.submit()
        advanceTimeBy(4_000)
        runCurrent()
        assertFalse(vm.state.value.canSubmit)
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(vm.state.value.canSubmit)
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R23 un 5xx en el login muestra el mensaje general y sin servidor el de sin conexion`() = runTest {
        val vm = ready()
        vm.updateForm(form)
        env.enqueue(500, env.problem(500))
        vm.submit()
        assertEquals(UiMessage(R.string.error_general), vm.settled().message)
        env.server.shutdown()
        vm.updateForm(form)
        vm.submit()
        assertEquals(UiMessage(R.string.error_network), vm.settled().message)
    }

    @Test fun `R25 mientras el login esta en curso una segunda pulsacion no envia otra peticion`() = runTest {
        val vm = ready()
        vm.updateForm(form)
        env.enqueue(401, env.problem(401))
        vm.submit()
        assertTrue(vm.state.value.submitting)
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        vm.submit()
        vm.settled()
        runCurrent()
        assertEquals(1, env.server.requestCount)
    }
}
