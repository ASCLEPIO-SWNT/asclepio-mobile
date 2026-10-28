package co.asclepio.mobile.core.auth

import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.httpStatus
import co.asclepio.mobile.feature.encounters.data.ComplaintSource
import co.asclepio.mobile.feature.encounters.data.CreatePreRegistrationRequest
import co.asclepio.mobile.feature.patients.data.UpdatePatientProfileRequest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PatientCallTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: PatientTestEnv

    @Before fun setUp() {
        env = PatientTestEnv(tmp.root)
    }

    @After fun tearDown() = env.close()

    private val endpoints: List<Pair<String, suspend () -> ApiResult<*>>> by lazy {
        listOf(
            "GET /patient/profile" to { env.profileRepository.profile() },
            "PATCH /patient/profile" to { env.profileRepository.update(UpdatePatientProfileRequest(fullName = "X")) },
            "POST /patient/pre-registrations" to {
                env.preRegistrationRepository.create(CreatePreRegistrationRequest("s", "m", ComplaintSource.TYPED))
            },
            "GET /patient/pre-registrations" to { env.preRegistrationRepository.latest() },
        )
    }

    @Test fun `R23 un 401 de cada endpoint del paciente borra la sesion y conserva la IPS recordada`() = runTest {
        for ((name, call) in endpoints) {
            env.login()
            env.enqueue(401, env.problem(401))
            val result = call()
            assertEquals(name, 401, (result as ApiResult.Failure).error.httpStatus)
            assertEquals(name, SessionState.LoggedOut(env.tenant), env.sessionManager.state.value)
            assertEquals(name, StoredSession.None, env.store.session())
            assertNull(name, env.sessionManager.currentToken())
            assertEquals(name, env.tenant, env.store.tenant())
        }
    }

    @Test fun `R23 otros errores de los endpoints del paciente conservan la sesion`() = runTest {
        env.login()
        for ((name, call) in endpoints) {
            for (code in listOf(400, 403, 404, 409, 422, 429, 500)) {
                env.enqueue(code, env.problem(code))
                assertTrue(name, call() is ApiResult.Failure)
                assertEquals("$name $code", SessionState.LoggedIn(env.tenant), env.sessionManager.state.value)
            }
        }
    }

    @Test fun `R23 un 401 de la lista publica de sedes no cierra la sesion`() = runTest {
        env.login()
        env.enqueue(401, env.problem(401))
        assertTrue(env.preRegistrationRepository.tenants() is ApiResult.Failure)
        assertEquals(SessionState.LoggedIn(env.tenant), env.sessionManager.state.value)
    }
}
