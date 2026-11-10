package co.asclepio.mobile.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import co.asclepio.mobile.MainDispatcherRule
import co.asclepio.mobile.PatientTestEnv
import co.asclepio.mobile.core.model.Sex
import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.feature.account.data.DocumentType
import co.asclepio.mobile.feature.account.data.LoginPrefill
import co.asclepio.mobile.feature.account.data.LoginResult
import co.asclepio.mobile.feature.account.ui.AccountTestEnv
import co.asclepio.mobile.feature.account.ui.ConsentUiState
import co.asclepio.mobile.feature.account.ui.SignUpForm
import co.asclepio.mobile.feature.account.ui.SignUpViewModel
import co.asclepio.mobile.feature.account.ui.TenantPickerUiState
import co.asclepio.mobile.feature.account.ui.TenantPickerViewModel
import co.asclepio.mobile.feature.patients.data.PatientProfileApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Ciclo de vida de la sesión del paciente (DT-01-mobile): generaciones, expiración, 401 y Bearer por operación (R11),
 * con Retrofit, DataStore y MockWebServer reales.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PatientSessionLifecycleTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: SessionTestEnv
    private var account: AccountTestEnv? = null
    private var subject: SessionManager? = null
    @After fun tearDown() {
        subject?.close()
        account?.close()
        if (::env.isInitialized) env.close()
    }

    private class GateCipher : TokenCipher {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        private val delegate = FakeTokenCipher()
        override fun decrypt(ciphertext: ByteArray) = delegate.decrypt(ciphertext)
        override fun encrypt(plaintext: String): ByteArray {
            if (plaintext == "B") {
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
            }
            return delegate.encrypt(plaintext)
        }
    }
    private class PausingStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        override val data: Flow<Preferences> = delegate.data
        var paused = false
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (paused) { entered.complete(Unit); release.await() }
            return delegate.updateData(transform)
        }
    }

    @Test fun `R11 5xx y red al restaurar conservan el token cifrado sin abrir contenido y permiten reintentar`() = runTest {
        env = SessionTestEnv(tmp.root, backgroundScope)
        env.store.saveTenant(env.tenant)
        env.store.saveSession(SessionTestEnv.TOKEN, SessionTestEnv.EXPIRES)
        env.enqueue(503)
        env.manager.refresh()
        assertTrue(env.manager.state.value is SessionState.VerificationError)
        assertNull(env.manager.currentToken())
        assertTrue(env.store.session() is StoredSession.Present)
        env.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        env.manager.refresh()
        assertTrue(env.manager.state.value is SessionState.VerificationError)
        assertTrue(env.store.session() is StoredSession.Present)
        env.enqueue(200, SessionTestEnv.patientMe())
        env.manager.refresh()
        assertPatientSession(env.manager, env.tenant)
    }

    @Test fun `R11 sesion vencida al restaurar y temporizador de expiracion cierran sin contenido privado`() = runTest {
        env = SessionTestEnv(tmp.root, backgroundScope)
        env.store.saveTenant(env.tenant)
        env.store.saveSession(SessionTestEnv.TOKEN, env.clock.instant())
        env.manager.refresh()
        assertEquals(StoredSession.None, env.store.session())
        assertFalse(env.manager.state.value is SessionState.Authenticated)
        assertEquals(0, env.server.requestCount)
        val attempt = env.manager.beginLogin()
        val expiry = env.clock.instant().plusSeconds(10)
        assertTrue(env.manager.completeLogin(attempt, "token", expiry, SessionTestEnv.patientMeDto()))
        runCurrent()
        env.clock.now = expiry
        advanceTimeBy(10_000)
        runCurrent()
        env.manager.state.first { it == SessionState.LoggedOut(env.tenant) }
        assertEquals(StoredSession.None, env.store.session())
        assertNull(env.manager.currentToken())
        assertEquals(env.tenant, env.store.tenant())
    }

    @Test fun `R11 un 401 vigente cierra la sesion centralmente conserva la IPS y no llama logout HTTP`() = runTest {
        env = SessionTestEnv(tmp.root, backgroundScope)
        env.activate()
        env.enqueue(401)
        val result = env.manager.patientCall { env.meApi.meWithToken(it) }
        assertTrue(result is ApiResult.Failure)
        assertEquals(SessionState.LoggedOut(env.tenant), env.manager.state.value)
        assertEquals(StoredSession.None, env.store.session())
        assertEquals(env.tenant, env.store.tenant())
        assertEquals("/api/v1/me", env.server.takeRequest().path)
        assertEquals(1, env.server.requestCount)
    }

    @Test fun `R11 cerrar sesion invalida un inicio de sesion pendiente y conserva solo la IPS publica`() = runTest {
        env = SessionTestEnv(tmp.root, backgroundScope)
        env.manager.saveTenant(env.tenant)
        val attempt = env.manager.beginLogin()
        env.manager.logout()
        assertFalse(env.manager.isCurrent(attempt.generation))
        try {
            env.manager.completeLogin(attempt, "old", SessionTestEnv.EXPIRES, SessionTestEnv.patientMeDto())
            fail("No acepta un inicio de sesion invalidado")
        } catch (_: SessionInvalidated) { }
        assertEquals(SessionState.LoggedOut(env.tenant), env.manager.state.value)
        assertEquals(StoredSession.None, env.store.session())
        assertEquals(env.tenant, env.store.tenant())
    }

    @Test fun `R11 respuesta y 401 antiguos no publican ni cierran una sesion posterior`() = runTest {
        env = SessionTestEnv(tmp.root, backgroundScope)
        for (status in listOf(200, 401)) {
            env.activate(token = "token-A")
            val received = CountDownLatch(1)
            val release = CountDownLatch(1)
            env.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received.countDown()
                    check(release.await(2, TimeUnit.SECONDS))
                    return MockResponse().setResponseCode(status).setBody(if (status == 200) SessionTestEnv.patientMe() else "")
                }
            }
            val old = async {
                try { env.manager.patientCall { env.meApi.meWithToken(it) }; false }
                catch (_: SessionInvalidated) { true }
            }
            assertTrue(withContext(Dispatchers.IO) { received.await(2, TimeUnit.SECONDS) })
            env.activate(token = "token-B")
            val newer = env.manager.state.value
            release.countDown()
            assertTrue(old.await())
            assertEquals(newer, env.manager.state.value)
            assertEquals("token-B", env.manager.currentToken())
            assertEquals("Bearer token-A", env.server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun `R11 una operacion suspendida conserva su Bearer y no obtiene el de la nueva sesion`() = runTest {
        env = SessionTestEnv(tmp.root, backgroundScope)
        env.activate("patient-A")
        val captured = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val api = env.retrofit.create(PatientProfileApi::class.java)
        val old = async {
            try {
                env.manager.patientCall { bearer ->
                    captured.complete(Unit); resume.await()
                    api.profile(bearer)
                }; false
            } catch (_: SessionInvalidated) { true }
        }
        captured.await()
        env.activate("patient-B")
        env.enqueue(200, PatientTestEnv.profile())
        resume.complete(Unit)
        assertTrue(old.await())
        assertEquals("Bearer patient-A", env.server.takeRequest().getHeader("Authorization"))
        assertEquals("patient-B", env.manager.currentToken())
    }

    @Test fun `R11 una llamada iniciada sin sesion no espera al nuevo inicio de sesion para usar su token`() = runTest {
        env = SessionTestEnv(tmp.root, backgroundScope)
        val cipher = GateCipher()
        val manager = SessionManager(SessionStore(env.dataStore, cipher), env.meApi, env.clock, backgroundScope).also { subject = it }
        manager.saveTenant(env.tenant)
        val attempt = manager.beginLogin()
        val installingB = async(Dispatchers.IO) { manager.completeLogin(attempt, "B", SessionTestEnv.EXPIRES, SessionTestEnv.patientMeDto()) }
        assertTrue(withContext(Dispatchers.IO) { cipher.entered.await(3, TimeUnit.SECONDS) })
        var operations = 0
        val old = async {
            try { manager.patientCall { operations++; "private" }; false }
            catch (_: SessionInvalidated) { true }
        }
        runCurrent()
        cipher.release.countDown()
        assertTrue(installingB.await())
        assertTrue(old.await())
        assertEquals(0, operations)
        assertEquals("B", manager.currentToken())
    }

    @Test fun `R11 un token que vence mientras se guarda nunca activa al paciente y se elimina`() = runTest {
        env = SessionTestEnv(tmp.root, backgroundScope)
        val cipher = GateCipher()
        val store = SessionStore(env.dataStore, cipher)
        val manager = SessionManager(store, env.meApi, env.clock, backgroundScope).also { subject = it }
        manager.saveTenant(env.tenant)
        val attempt = manager.beginLogin()
        val expires = env.clock.instant().plusSeconds(10)
        val login = async(Dispatchers.IO) { manager.completeLogin(attempt, "B", expires, SessionTestEnv.patientMeDto()) }
        assertTrue(withContext(Dispatchers.IO) { cipher.entered.await(3, TimeUnit.SECONDS) })
        env.clock.now = expires
        cipher.release.countDown()
        assertFalse(login.await())
        assertFalse(manager.state.value is SessionState.Authenticated)
        assertEquals(StoredSession.None, store.session())
    }

    @Test fun `R11 un beginLogin de la generacion anterior se rechaza aunque espere al cierre de sesion`() = runTest {
        env = SessionTestEnv(tmp.root, backgroundScope)
        val pausing = PausingStore(env.dataStore)
        val manager = SessionManager(SessionStore(pausing, FakeTokenCipher()), env.meApi, env.clock, backgroundScope).also { subject = it }
        manager.saveTenant(env.tenant)
        val origin = manager.generation
        pausing.paused = true
        val changed = async { manager.logout() }
        pausing.entered.await()
        val previous = async {
            try { manager.beginLogin(expectedGeneration = origin); false }
            catch (_: SessionInvalidated) { true }
        }
        runCurrent()
        pausing.release.complete(Unit)
        changed.await()
        assertTrue(previous.await())
        assertEquals(SessionState.LoggedOut(env.tenant), manager.state.value)
    }

    @Test fun `R11 restauracion que vence durante me con 503 borra el token sin quedar en error`() = runTest {
        env = SessionTestEnv(tmp.root, backgroundScope)
        val expires = env.clock.instant().plusSeconds(10)
        env.store.saveTenant(env.tenant)
        env.store.saveSession("saved", expires)
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received.countDown(); check(release.await(3, TimeUnit.SECONDS))
                return MockResponse().setResponseCode(503)
            }
        }
        val restore = async { env.manager.refresh() }
        assertTrue(withContext(Dispatchers.IO) { received.await(3, TimeUnit.SECONDS) })
        env.clock.now = expires
        release.countDown(); restore.await()
        assertEquals(StoredSession.None, env.store.session())
        assertEquals(SessionState.LoggedOut(env.tenant), env.manager.state.value)
    }

    @Test fun `R11 respuesta protegida que vence durante HTTP se descarta aunque el temporizador no haya corrido`() = runTest {
        env = SessionTestEnv(tmp.root, backgroundScope)
        env.activate()
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received.countDown(); check(release.await(3, TimeUnit.SECONDS))
                return MockResponse().setBody(SessionTestEnv.patientMe())
            }
        }
        val response = async {
            try { env.manager.patientCall { env.meApi.meWithToken(it) }; false }
            catch (_: SessionInvalidated) { true }
        }
        assertTrue(withContext(Dispatchers.IO) { received.await(3, TimeUnit.SECONDS) })
        env.clock.now = SessionTestEnv.EXPIRES
        release.countDown()
        assertTrue(response.await())
        assertEquals(StoredSession.None, env.store.session())
        assertEquals(SessionState.LoggedOut(env.tenant), env.manager.state.value)
    }

    @Test fun `R11 cerrar sesion o confirmar otra IPS limpia el documento pendiente de auto login`() = runTest {
        val account = AccountTestEnv(tmp.newFolder("account")).also { this@PatientSessionLifecycleTest.account = it }
        account.rememberTenant()
        account.accountRepository.setLoginPrefill(LoginPrefill(DocumentType.CC, "private-document", LoginResult.NotPatient))
        account.sessionManager.logout()
        assertNull(account.accountRepository.loginPrefill.value)
        account.accountRepository.setLoginPrefill(LoginPrefill(DocumentType.CC, "private-document", LoginResult.NotPatient))
        account.sessionManager.saveTenant(account.tenant)
        assertNull(account.accountRepository.loginPrefill.value)
    }

    @Test fun `R11 cerrar sesion durante el alta impide que su respuesta inicie sesion`() = runTest {
        val account = AccountTestEnv(tmp.newFolder("account")).also { this@PatientSessionLifecycleTest.account = it }
        account.rememberTenant()
        account.enqueue(200, """{"version":"v1","text":"Consentimiento"}""")
        val vm = SignUpViewModel(account.accountRepository, account.sessionManager, account.clock)
        vm.state.first { it.consent is ConsentUiState.Loaded }
        account.server.takeRequest()
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        account.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                return MockResponse().setResponseCode(201).setBody("""{"id":"a","patientId":"p"}""")
            }
        }
        vm.updateForm(SignUpForm(DocumentType.CC, "private-document", "Nombre", LocalDate.of(1990, 1, 1), Sex.X, "password-123", "password-123", true))
        vm.submit()
        assertTrue(withContext(Dispatchers.IO) { received.await(2, TimeUnit.SECONDS) })
        account.sessionManager.logout()
        release.countDown()
        vm.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals(SessionState.LoggedOut(account.tenant), account.sessionManager.state.value)
        assertNull(account.sessionManager.currentToken())
        assertEquals(2, account.server.requestCount) // consentimiento + alta, nunca login
        assertNull(account.accountRepository.loginPrefill.value)
    }

    @Test fun `R11 un selector de IPS de la generacion anterior no cambia la IPS recordada`() = runTest {
        val account = AccountTestEnv(tmp.newFolder("account")).also { this@PatientSessionLifecycleTest.account = it }
        account.rememberTenant()
        account.enqueue(200, """[{"id":"other","name":"Otra IPS","sites":[{"id":"site","name":"Sede"}]}]""")
        val vm = TenantPickerViewModel(account.tenantRepository, account.sessionManager)
        vm.state.first { it is TenantPickerUiState.Success }
        vm.selectTenant("other"); vm.selectSite("site")
        account.sessionManager.logout()
        vm.confirm()
        vm.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals(SessionState.LoggedOut(account.tenant), account.sessionManager.state.value)
        assertEquals(account.tenant, account.store.tenant())
    }

    @Test fun `R8 el modelo de sesion solo tiene principal paciente sin modo de acceso ni rol de personal`() {
        val principals = VerifiedPrincipal::class.java.declaredClasses.filter { VerifiedPrincipal::class.java.isAssignableFrom(it) }
        assertEquals(listOf("Patient"), principals.map { it.simpleName })
        val states = SessionState::class.java.declaredClasses.filter { SessionState::class.java.isAssignableFrom(it) }.map { it.simpleName }.toSet()
        assertEquals(setOf("Loading", "NoTenant", "LoggedOut", "Verifying", "VerificationError", "Authenticated"), states)
        for (retired in listOf("AccessMode", "StaffRole", "StaffDestination", "StaffDestinationsKt")) {
            try {
                Class.forName("co.asclepio.mobile.core.auth.$retired")
                fail("$retired no debe existir")
            } catch (_: ClassNotFoundException) { }
        }
        assertTrue(LoginAttempt::class.java.declaredFields.none { it.name == "mode" })
        assertTrue(VerifiedPrincipal::class.java.methods.none { it.name == "getMode" })
    }

    @Test fun `R8 me de personal con cualquiera de los cuatro roles u otro kind no se convierte en principal`() {
        val tenant = RememberedTenant(SessionTestEnv.TENANT, "IPS Uno", "site", "Sede Norte")
        for (role in listOf("RECEPTION", "NURSE", "PHYSICIAN", "ADMIN")) {
            val staff = MeDto("STAFF", "staff-id", SessionTestEnv.TENANT, role = role, email = "staff@ips.co", fullName = "Nombre")
            assertNull(staff.toPrincipal(tenant))
            assertNull(staff.toPrincipal(null))
        }
        assertNull(MeDto("ADMIN", "id", SessionTestEnv.TENANT, "patient", "v1", false).toPrincipal(tenant))
        assertNull(MeDto("PATIENT", "id", SessionTestEnv.TENANT, null, "v1", false).toPrincipal(tenant))
        assertNull(MeDto("PATIENT", "id", "other-tenant", "patient", "v1", false).toPrincipal(tenant))
        val patient = SessionTestEnv.patientMeDto().toPrincipal(tenant)
        assertTrue(patient is VerifiedPrincipal.Patient)
        assertEquals(tenant, patient?.tenant)
    }
}
