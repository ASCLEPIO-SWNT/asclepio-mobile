package co.asclepio.mobile.feature.staffaccount.data

import co.asclepio.mobile.StaffTestEnv
import co.asclepio.mobile.core.auth.*
import co.asclepio.mobile.core.network.appJson
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StaffAccountRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffTestEnv
    @Before fun setUp() { env = StaffTestEnv(tmp.root) }
    @After fun tearDown() { env.close() }

    @Test fun `R3 login envia solo correo recortado y password literal sin Bearer ni IPS`() = runTest {
        env.manager.saveTenant(env.tenant)
        env.enqueue(200, StaffTestEnv.LOGIN_OK)
        env.enqueue(200, StaffTestEnv.staffMe())
        assertEquals(StaffLoginResult.Success, env.repository.login("  nurse@ips.co  ", " x "))
        val login = env.server.takeRequest()
        assertEquals("POST", login.method)
        assertEquals("/api/v1/auth/login", login.path)
        assertNull(login.getHeader("Authorization"))
        val body = appJson.parseToJsonElement(login.body.readUtf8()).jsonObject
        assertEquals(setOf("email", "password"), body.keys)
        assertEquals("nurse@ips.co", body.getValue("email").jsonPrimitive.content)
        assertEquals(" x ", body.getValue("password").jsonPrimitive.content)
        val me = env.server.takeRequest()
        assertEquals("/api/v1/me", me.path)
        assertEquals("Bearer ${StaffTestEnv.TOKEN}", me.getHeader("Authorization"))
        assertEquals(2, env.server.requestCount)
    }

    @Test fun `R4 no persiste ni abre personal antes de me y admite los cuatro roles sin IPS`() = runTest {
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == "/api/v1/auth/login") MockResponse().setBody(StaffTestEnv.LOGIN_OK) else {
                    received.countDown()
                    check(release.await(2, TimeUnit.SECONDS))
                    MockResponse().setBody(StaffTestEnv.staffMe())
                }
        }
        val login = async { env.repository.login("nurse@ips.co", "p") }
        // La llamada HTTP suspende, por eso el planificador se deja avanzar antes de esperar al servidor.
        assertTrue(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { received.await(2, TimeUnit.SECONDS) })
        assertEquals(StoredSession.None, env.store.session())
        assertFalse(env.manager.state.value is SessionState.Authenticated)
        release.countDown()
        assertEquals(StaffLoginResult.Success, login.await())
        assertTrue((env.manager.state.value as SessionState.Authenticated).principal is VerifiedPrincipal.Staff)
        assertNull(env.store.tenant())
        for (role in StaffRole.entries) {
            env.manager.logout()
            val attempt = env.manager.beginLogin(AccessMode.STAFF)
            assertTrue(env.manager.completeLogin(attempt, StaffTestEnv.TOKEN, StaffTestEnv.EXPIRES, StaffTestEnv.staffMeDto(role.name)))
            assertEquals(role, ((env.manager.state.value as SessionState.Authenticated).principal as VerifiedPrincipal.Staff).role)
        }
    }

    @Test fun `R5 descarta kind rol datos incompletos y principal equivocado sin guardar token`() = runTest {
        for (me in listOf(
            StaffTestEnv.staffMe().replace("NURSE", "OWNER"),
            StaffTestEnv.staffMe().replace("STAFF", "ALIEN"),
            StaffTestEnv.staffMe().replace("\"fullName\":\"Nombre privado\",", ""),
            StaffTestEnv.patientMe(),
        )) {
            env.enqueue(200, StaffTestEnv.LOGIN_OK)
            env.enqueue(200, me)
            assertEquals(StaffLoginResult.InvalidPrincipal, env.repository.login("a@b.co", "p"))
            assertEquals(StoredSession.None, env.store.session())
            assertNull(env.manager.currentToken())
            assertFalse(env.manager.state.value is SessionState.Authenticated)
        }
        env.manager.saveTenant(env.tenant)
        val attempt = env.manager.beginLogin(AccessMode.PATIENT)
        assertFalse(env.manager.completeLogin(attempt, "x", StaffTestEnv.EXPIRES, StaffTestEnv.patientMeDto().copy(tenantId = "other")))
        assertFalse(env.manager.completeLogin(attempt, "x", StaffTestEnv.EXPIRES, StaffTestEnv.staffMeDto()))
    }

    @Test fun `R9 hay un token cifrado unico y ningun perfil de personal se escribe en disco`() = runTest {
        env.activate()
        env.activate(token = "another-private-token", role = "ADMIN")
        assertEquals(StoredSession.Present("another-private-token", StaffTestEnv.EXPIRES), env.store.session())
        val bytes = tmp.root.walkTopDown().filter { it.isFile }.joinToString { it.readBytes().toString(Charsets.ISO_8859_1) }
        for (secret in listOf(StaffTestEnv.TOKEN, "another-private-token", "Nombre privado", "nurse@ips.co", "NURSE", "ADMIN")) assertFalse(secret, bytes.contains(secret))
    }

    @Test fun `R20 modelos de personal y tokens enmascaran identidad y credenciales en toString`() = runTest {
        env.activate()
        val text = listOf(
            StaffLoginRequest("secret@ips.co", "password-private"),
            StaffTestEnv.staffMeDto(), env.manager.state.value,
            AccessTokenDto("token-private", "Bearer", StaffTestEnv.EXPIRES.toString()),
        ).joinToString()
        for (secret in listOf("secret@ips.co", "password-private", "Nombre privado", "nurse@ips.co", "staff-id", "staff-tenant", "token-private")) assertFalse(secret, text.contains(secret))
    }
}
