package co.asclepio.mobile.feature.account.ui

import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.R
import co.asclepio.mobile.core.auth.SessionState
import co.asclepio.mobile.core.auth.StoredSession
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.ui.UiMessage
import co.asclepio.mobile.feature.account.data.DocumentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate

/** T14: entrada automática tras el alta (R16). */
class AutoLoginTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: AccountTestEnv

    @Before fun setUp() {
        env = AccountTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    private val form = SignUpForm(
        documentType = DocumentType.PA,
        documentNumber = " AB123 ",
        fullName = "DEV Paciente",
        birthDate = LocalDate.parse("1990-05-17"),
        sex = Sex.F,
        password = "secreta-123",
        passwordConfirmation = "secreta-123",
        consentAccepted = true,
    )

    private suspend fun signUp(): SignUpViewModel {
        env.rememberTenant()
        env.enqueue(200, """{"version":"2026-10-v1","text":"Texto"}""")
        val vm = SignUpViewModel(env.accountRepository, env.sessionManager, env.clock)
        vm.state.first { it.consent is ConsentUiState.Loaded }
        vm.updateForm(form)
        env.enqueue(201, """{"id":"a-1","patientId":"p-1"}""")
        return vm
    }

    @Test fun `R16 tras el 201 inicia sesion con el mismo tenant, documento y contrasena sin pedirlos`() = runTest {
        val vm = signUp()
        env.enqueue(200, AccountTestEnv.LOGIN_OK)
        env.enqueue(200, AccountTestEnv.me())
        vm.submit()
        env.sessionManager.state.first { it is SessionState.LoggedIn }
        val paths = (1..4).map { env.server.takeRequest() }
        assertEquals(
            listOf("/api/v1/public/consent", "/api/v1/auth/patient/accounts", "/api/v1/auth/patient/login", "/api/v1/me"),
            paths.map { it.path },
        )
        val login = Json.parseToJsonElement(paths[2].body.readUtf8()).jsonObject
        assertEquals(JsonPrimitive("t-1"), login["tenantId"])
        assertEquals(JsonPrimitive("PA"), login["documentType"])
        assertEquals(JsonPrimitive("AB123"), login["documentNumber"])
        assertEquals(JsonPrimitive("secreta-123"), login["password"])
        assertEquals(StoredSession.Present(AccountTestEnv.TOKEN, Instant.parse(AccountTestEnv.EXPIRES_AT)), env.store.session())
        assertFalse(vm.state.value.navigateToLogin)
    }

    @Test fun `R16 si la entrada automatica falla va al login con el documento ya escrito y el mensaje`() = runTest {
        val vm = signUp()
        env.enqueue(401, env.problem(401))
        vm.submit()
        vm.state.first { it.navigateToLogin }
        val login = LoginViewModel(env.accountRepository, env.sessionManager)
        runCurrent()
        val state = login.state.value
        assertEquals(LoginForm(DocumentType.PA, "AB123", ""), state.form)
        assertEquals(UiMessage(R.string.error_login_invalid), state.message)
        assertEquals(SessionState.LoggedOut(env.tenant), env.sessionManager.state.value)
        // El prefill se consume una sola vez: un login nuevo empieza vacío (R17).
        val again = LoginViewModel(env.accountRepository, env.sessionManager)
        runCurrent()
        assertEquals(LoginForm(), again.state.value.form)
    }

    @Test fun `R16 si me no es paciente tras el alta va al login con el mensaje de no paciente`() = runTest {
        val vm = signUp()
        env.enqueue(200, AccountTestEnv.LOGIN_OK)
        env.enqueue(200, AccountTestEnv.me(kind = "STAFF"))
        vm.submit()
        vm.state.first { it.navigateToLogin }
        val login = LoginViewModel(env.accountRepository, env.sessionManager)
        runCurrent()
        assertEquals(notPatientMessage, login.state.value.message)
        assertEquals(StoredSession.None, env.store.session())
    }
}
