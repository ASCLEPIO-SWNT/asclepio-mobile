package co.asclepio.mobile.feature.staffaccount.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.StaffTestEnv
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.auth.StoredSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StaffLoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffTestEnv
    @Before fun setUp() { env = StaffTestEnv(tmp.root) }
    @After fun tearDown() { env.close() }

    @Test fun `R2 rechaza correo ausente invalido y password ausente o mayor a 128 sin HTTP`() = runTest {
        val vm = StaffLoginViewModel(env.repository)
        for (form in listOf(StaffLoginForm(), StaffLoginForm("bad", "x"), StaffLoginForm("a@b.co", ""), StaffLoginForm("a@b.co", "x".repeat(129)))) {
            vm.updateForm(form); vm.submit(); runCurrent()
            assertTrue(vm.state.value.fieldErrors.isNotEmpty())
            assertFalse(vm.state.value.submitting)
        }
        assertEquals(0, env.server.requestCount)
        assertTrue(validateStaffLogin(StaffLoginForm(" a@b.co ", "x")).isEmpty())
        assertTrue(validateStaffLogin(StaffLoginForm("a@b.co", "x".repeat(128))).isEmpty())
    }
    @Test fun `R3 envio pendiente bloquea duplicados y conserva correo escrito`() = runTest {
        env.server.enqueue(MockResponse().setBody(StaffTestEnv.LOGIN_OK).setBodyDelay(200, java.util.concurrent.TimeUnit.MILLISECONDS))
        env.enqueue(200, StaffTestEnv.staffMe())
        val vm = StaffLoginViewModel(env.repository)
        vm.updateForm(StaffLoginForm(" nurse@ips.co ", "literal"))
        vm.submit(); vm.submit()
        assertTrue(vm.state.value.submitting)
        assertFalse(vm.state.value.canSubmit)
        vm.updateForm(StaffLoginForm("changed@ips.co", "another"))
        assertEquals(" nurse@ips.co ", vm.state.value.form.email)
        vm.state.first { !it.submitting }
        assertEquals(2, env.server.requestCount)
        assertEquals("", vm.state.value.form.password)
    }
    @Test fun `R6 cualquier cuerpo 401 da mismo mensaje borra password y conserva identificador`() = runTest {
        val vm = StaffLoginViewModel(env.repository)
        for (body in listOf("{}", "not json", """{"type":"x","title":"PRIVATE","status":401,"detail":"SECRET"}""")) {
            env.enqueue(401, body)
            vm.updateForm(StaffLoginForm("wrong@ips.co", "secret-password")); vm.submit()
            vm.state.first { !it.submitting }
            assertEquals(R.string.staff_error_credentials, vm.state.value.message?.resId)
            assertEquals("", vm.state.value.form.password)
            assertEquals("wrong@ips.co", vm.state.value.form.email)
            assertEquals(StoredSession.None, env.store.session())
        }
    }
    @Test fun `R19 mismo formulario staff permite corregir despues de 401 y entrar en el intento siguiente`() = runTest {
        val vm = StaffLoginViewModel(env.repository)
        env.enqueue(401, "{}")
        vm.updateForm(StaffLoginForm("wrong@ips.co", "x")); vm.submit()
        vm.state.first { !it.submitting }
        vm.updateForm(StaffLoginForm("nurse@ips.co", "corrected"))
        env.enqueue(200, StaffTestEnv.LOGIN_OK); env.enqueue(200, StaffTestEnv.staffMe())
        vm.submit(); vm.state.first { !it.submitting }
        assertTrue(env.manager.state.value is SessionState.Authenticated)
        assertEquals("", vm.state.value.form.password)
    }

    @Test fun `R7 429 bloquea RetryAfter positivo o 60 segundos sin reintentos ni sesion`() = runTest {
        val vm = StaffLoginViewModel(env.repository)
        for ((header, seconds) in listOf("3" to 3, "0" to 60, "-2" to 60, "date" to 60, null to 60)) {
            env.enqueue(429, "{}", header)
            vm.updateForm(StaffLoginForm("a@b.co", "x")); vm.submit()
            vm.state.first { !it.submitting }
            val count = env.server.requestCount
            assertFalse(vm.state.value.canSubmit)
            vm.submit(); runCurrent()
            assertEquals(count, env.server.requestCount)
            assertEquals(StoredSession.None, env.store.session())
            advanceTimeBy(seconds * 1000L - 1); runCurrent()
            assertFalse(vm.state.value.canSubmit)
            advanceTimeBy(1); runCurrent()
            assertTrue(vm.state.value.canSubmit)
        }
    }
    @Test fun `R8 red 5xx y fallo de me muestran recursos locales sin token ni acceso`() = runTest {
        val vm = StaffLoginViewModel(env.repository)
        env.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        vm.updateForm(StaffLoginForm("a@b.co", "x")); vm.submit()
        vm.state.first { !it.submitting }
        assertEquals(R.string.error_network, vm.state.value.message?.resId)
        for (duringMe in listOf(false, true)) {
            if (duringMe) env.enqueue(200, StaffTestEnv.LOGIN_OK)
            env.enqueue(503, """{"type":"x","title":"SECRET","status":503,"detail":"PRIVATE"}""")
            vm.submit(); vm.state.first { !it.submitting }
            assertEquals(R.string.error_general, vm.state.value.message?.resId)
            assertEquals("a@b.co", vm.state.value.form.email)
            assertEquals(StoredSession.None, env.store.session())
            assertFalse(env.manager.state.value is SessionState.Authenticated)
            assertFalse(vm.state.value.toString().contains("SECRET"))
        }
    }
    @Test fun `R20 errores de campos 400 usan mensajes locales y form enmascara correo y password`() = runTest {
        env.enqueue(400, """{"type":"x","title":"SECRET","status":400,"errors":[{"field":"email","message":"PRIVATE"},{"field":"password","message":"PRIVATE"},{"field":"other","message":"PRIVATE"}]}""")
        val vm = StaffLoginViewModel(env.repository)
        vm.updateForm(StaffLoginForm("private@ips.co", "private-password")); vm.submit()
        vm.state.first { !it.submitting }
        assertEquals(setOf(StaffLoginField.Email, StaffLoginField.Password), vm.state.value.fieldErrors.keys)
        for (secret in listOf("private@ips.co", "private-password", "PRIVATE", "SECRET")) assertFalse(vm.state.value.toString().contains(secret))
    }
    @Test fun `R21 formulario en ViewModel conserva edicion y una instancia nueva comienza vacia`() {
        val vm = StaffLoginViewModel(env.repository)
        val form = StaffLoginForm("a@b.co", "secret")
        vm.updateForm(form)
        assertEquals(form, vm.state.value.form)
        assertEquals(StaffLoginForm(), StaffLoginViewModel(env.repository).state.value.form)
    }
    @Test fun `R21 foco de validacion cambia por nuevo envio pero no al editar otro campo`() {
        val vm = StaffLoginViewModel(env.repository)
        vm.submit()
        val attempt = vm.state.value.validationAttempt
        assertTrue(attempt > 0)
        vm.updateForm(StaffLoginForm(password = "primer caracter"))
        assertTrue(StaffLoginField.Email in vm.state.value.fieldErrors)
        assertFalse(StaffLoginField.Password in vm.state.value.fieldErrors)
        assertEquals(attempt, vm.state.value.validationAttempt)
        vm.submit()
        assertTrue(vm.state.value.validationAttempt > attempt)
    }

}
