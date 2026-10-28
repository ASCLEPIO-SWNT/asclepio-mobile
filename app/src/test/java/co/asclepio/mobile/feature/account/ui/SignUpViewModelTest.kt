package co.asclepio.mobile.feature.account.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.R
import co.asclepio.mobile.core.model.Sex
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
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class SignUpViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: AccountTestEnv

    @Before fun setUp() {
        env = AccountTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    private val consentV1 = """{"version":"2026-10-v1","text":"# Consentimiento\nTexto **v1**"}"""
    private val consentV2 = """{"version":"2026-11-v2","text":"# Consentimiento\nTexto **v2**"}"""
    private val validForm = SignUpForm(
        documentType = DocumentType.CC,
        documentNumber = " DEV-123 ",
        fullName = " DEV Paciente ",
        birthDate = LocalDate.parse("1990-05-17"),
        sex = Sex.M,
        password = "secreta-123",
        passwordConfirmation = "secreta-123",
        consentAccepted = true,
    )

    private suspend fun TestScope.ready(consent: String = consentV1): SignUpViewModel {
        env.rememberTenant()
        env.enqueue(200, consent)
        val vm = SignUpViewModel(env.accountRepository, env.sessionManager, env.clock)
        vm.state.first { it.consent !is ConsentUiState.Loading }
        assertEquals("/api/v1/public/consent", env.server.takeRequest().path)
        return vm
    }

    private suspend fun SignUpViewModel.settled() = state.first { !it.submitting }

    @Test fun `R6 al abrir el alta pide el consentimiento y expone el texto completo y su version`() = runTest {
        val vm = ready()
        assertEquals(ConsentUiState.Loaded("2026-10-v1", "# Consentimiento\nTexto **v1**"), vm.state.value.consent)
    }

    @Test fun `R7 la casilla empieza desmarcada y el boton deshabilitado hasta marcarla`() = runTest {
        val vm = ready()
        assertFalse(vm.state.value.form.consentAccepted)
        vm.updateForm(validForm.copy(consentAccepted = false))
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        runCurrent()
        assertEquals(1, env.server.requestCount)
        vm.updateForm(vm.state.value.form.copy(consentAccepted = true))
        assertTrue(vm.state.value.canSubmit)
    }

    @Test fun `R8 el alta envia consentAccepted true y la version vigente como cadena`() = runTest {
        val vm = ready()
        vm.updateForm(validForm)
        env.enqueue(409, env.problem(409))
        vm.submit()
        vm.settled()
        val body = Json.parseToJsonElement(env.server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(JsonPrimitive(true), body["consentAccepted"])
        assertEquals(JsonPrimitive("2026-10-v1"), body["consentVersion"])
    }

    @Test fun `R9 si el consentimiento falla muestra el mensaje con reintentar y no permite enviar`() = runTest {
        env.rememberTenant()
        env.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val vm = SignUpViewModel(env.accountRepository, env.sessionManager, env.clock)
        val state = vm.state.first { it.consent !is ConsentUiState.Loading }
        assertEquals(ConsentUiState.Error(UiMessage(R.string.error_network), retryEnabled = true), state.consent)
        vm.updateForm(validForm)
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        runCurrent()
        assertEquals(1, env.server.requestCount)
        env.enqueue(200, consentV1)
        vm.retryConsent()
        assertTrue(vm.state.first { it.consent !is ConsentUiState.Loading }.consent is ConsentUiState.Loaded)
    }

    @Test fun `R10 el alta envia POST accounts con exactamente los campos del contrato`() = runTest {
        val vm = ready()
        vm.updateForm(validForm)
        env.enqueue(409, env.problem(409))
        vm.submit()
        vm.settled()
        val recorded = env.server.takeRequest()
        assertEquals("/api/v1/auth/patient/accounts", recorded.path)
        assertNull(recorded.getHeader("Authorization"))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals(
            setOf("tenantId", "documentType", "documentNumber", "fullName", "birthDate", "sex", "password", "consentAccepted", "consentVersion"),
            body.keys,
        )
        assertEquals(JsonPrimitive("t-1"), body["tenantId"])
        assertEquals(JsonPrimitive("DEV-123"), body["documentNumber"])
        assertEquals(JsonPrimitive("DEV Paciente"), body["fullName"])
        assertEquals(JsonPrimitive("1990-05-17"), body["birthDate"])
        assertEquals(JsonPrimitive("M"), body["sex"])
    }

    @Test fun `R11 con campos invalidos marca cada campo y no envia la peticion`() = runTest {
        val vm = ready()
        vm.updateForm(validForm.copy(documentNumber = " ", password = "corta", passwordConfirmation = "otra"))
        vm.submit()
        runCurrent()
        assertEquals(
            mapOf(
                SignUpField.DocumentNumber to UiMessage(R.string.error_required),
                SignUpField.Password to UiMessage(R.string.error_password_length),
                SignUpField.PasswordConfirmation to UiMessage(R.string.error_password_mismatch),
            ),
            vm.state.value.fieldErrors,
        )
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R12 un menor de edad marca la fecha y no envia la peticion`() = runTest {
        val vm = ready()
        vm.updateForm(validForm.copy(birthDate = LocalDate.parse("2008-10-27")))
        vm.submit()
        runCurrent()
        assertEquals(mapOf(SignUpField.BirthDate to UiMessage(R.string.error_underage)), vm.state.value.fieldErrors)
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R13 un 400 con errors marca los campos y sin campos del formulario muestra el mensaje general`() = runTest {
        val vm = ready()
        vm.updateForm(validForm)
        env.enqueue(400, env.problem(400, ""","errors":[{"field":"documentNumber","message":"API"},{"field":"tenantId","message":"API"}]"""))
        vm.submit()
        val state = vm.settled()
        assertEquals(mapOf(SignUpField.DocumentNumber to UiMessage(R.string.error_max_length, listOf(64))), state.fieldErrors)
        assertNull(state.message)
        env.enqueue(400, env.problem(400))
        vm.submit()
        val general = vm.settled()
        assertEquals(UiMessage(R.string.error_general), general.message)
        assertTrue(general.fieldErrors.isEmpty())
    }

    @Test fun `R14 un 409 muestra cuenta existente, conserva el formulario y ofrece ir al login`() = runTest {
        val vm = ready()
        vm.updateForm(validForm)
        env.enqueue(409, env.problem(409))
        vm.submit()
        val state = vm.settled()
        assertEquals(UiMessage(R.string.error_signup_conflict), state.message)
        assertTrue(state.showGoToLogin)
        assertEquals(validForm, state.form)
    }

    @Test fun `R15 un 422 muestra el mensaje, vuelve a pedir el consentimiento y si cambio la version desmarca la casilla`() = runTest {
        val vm = ready()
        vm.updateForm(validForm)
        env.enqueue(422, env.problem(422))
        env.enqueue(200, consentV2)
        vm.submit()
        val state = vm.state.first { it.consent == ConsentUiState.Loaded("2026-11-v2", "# Consentimiento\nTexto **v2**") }
        assertEquals(UiMessage(R.string.error_signup_unprocessable), state.message)
        assertFalse(state.form.consentAccepted)
        env.server.takeRequest()
        assertEquals("/api/v1/public/consent", env.server.takeRequest().path)
    }

    @Test fun `R15 un 422 con la misma version conserva la casilla marcada`() = runTest {
        val vm = ready()
        vm.updateForm(validForm)
        env.enqueue(422, env.problem(422))
        env.enqueue(200, consentV1)
        vm.submit()
        vm.settled()
        env.server.takeRequest()
        env.server.takeRequest(5, TimeUnit.SECONDS)
        vm.state.first { !it.reloadingConsent }
        assertEquals(3, env.server.requestCount)
        assertTrue(vm.state.value.form.consentAccepted)
        assertEquals(UiMessage(R.string.error_signup_unprocessable), vm.state.value.message)
    }

    @Test fun `R22 un 429 en el alta deshabilita el envio durante Retry-After`() = runTest {
        val vm = ready()
        vm.updateForm(validForm)
        env.enqueue(429, env.problem(429), retryAfter = "10")
        vm.submit()
        val state = vm.settled()
        assertEquals(UiMessage(R.string.error_too_many_requests, listOf(10)), state.message)
        assertFalse(state.canSubmit)
        vm.submit()
        advanceTimeBy(9_000)
        runCurrent()
        assertFalse(vm.state.value.canSubmit)
        assertEquals(2, env.server.requestCount)
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(vm.state.value.canSubmit)
    }

    @Test fun `R22 un 429 al pedir el consentimiento deshabilita reintentar durante 60 s si falta Retry-After`() = runTest {
        env.rememberTenant()
        env.enqueue(429, env.problem(429))
        val vm = SignUpViewModel(env.accountRepository, env.sessionManager, env.clock)
        val state = vm.state.first { it.consent !is ConsentUiState.Loading }
        assertEquals(ConsentUiState.Error(UiMessage(R.string.error_too_many_requests, listOf(60)), retryEnabled = false), state.consent)
        advanceTimeBy(60_001)
        runCurrent()
        assertEquals(true, (vm.state.value.consent as ConsentUiState.Error).retryEnabled)
    }

    @Test fun `R23 un 5xx o sin conexion en el alta muestran el mensaje general o sin conexion`() = runTest {
        val vm = ready()
        vm.updateForm(validForm)
        env.enqueue(500, env.problem(500))
        vm.submit()
        assertEquals(UiMessage(R.string.error_general), vm.settled().message)
        env.server.shutdown()
        vm.submit()
        assertEquals(UiMessage(R.string.error_network), vm.settled().message)
    }

    @Test fun `R25 mientras el alta esta en curso muestra carga y una segunda pulsacion no envia otra peticion`() = runTest {
        val vm = ready()
        vm.updateForm(validForm)
        env.enqueue(409, env.problem(409))
        vm.submit()
        assertTrue(vm.state.value.submitting)
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        vm.submit()
        vm.settled()
        runCurrent()
        assertEquals(2, env.server.requestCount) // consentimiento + un solo alta
    }

    @Test fun `R32 la contrasena no aparece en el toString del estado del formulario`() = runTest {
        val vm = ready()
        vm.updateForm(validForm)
        assertFalse(vm.state.value.toString().contains("secreta-123"))
    }
}
