package co.asclepio.mobile.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import co.asclepio.mobile.StaffTestEnv
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SessionLockRaceTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var env: StaffTestEnv
    private var subject: SessionManager? = null
    @After fun tearDown() { subject?.close(); if (::env.isInitialized) env.close() }

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

    @Test fun `R18 llamada iniciada sin principal no espera mutex para adquirir las credenciales de B`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        val cipher = GateCipher()
        val manager = SessionManager(SessionStore(env.dataStore, cipher), env.meApi, env.clock, backgroundScope).also { subject = it }
        val attempt = manager.beginLogin(AccessMode.STAFF)
        val installingB = async(Dispatchers.IO) { manager.completeLogin(attempt, "B", StaffTestEnv.EXPIRES, StaffTestEnv.staffMeDto()) }
        assertTrue(withContext(Dispatchers.IO) { cipher.entered.await(3, TimeUnit.SECONDS) })
        var operations = 0
        val old = async {
            try { manager.authenticatedCall(AccessMode.STAFF) { operations++; "private" }; false }
            catch (_: SessionInvalidated) { true }
        }
        runCurrent()
        cipher.release.countDown()
        assertTrue(installingB.await())
        assertTrue(old.await())
        assertEquals(0, operations)
        assertEquals("B", manager.currentToken())
    }

    @Test fun `R12 token que vence mientras se persiste nunca activa principal y se elimina`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        val cipher = GateCipher()
        val store = SessionStore(env.dataStore, cipher)
        val manager = SessionManager(store, env.meApi, env.clock, backgroundScope).also { subject = it }
        val attempt = manager.beginLogin(AccessMode.STAFF)
        val expires = env.clock.instant().plusSeconds(10)
        val login = async(Dispatchers.IO) { manager.completeLogin(attempt, "B", expires, StaffTestEnv.staffMeDto()) }
        assertTrue(withContext(Dispatchers.IO) { cipher.entered.await(3, TimeUnit.SECONDS) })
        env.clock.now = expires
        cipher.release.countDown()
        assertFalse(login.await())
        assertFalse(manager.state.value is SessionState.Authenticated)
        assertEquals(StoredSession.None, store.session())
    }

    @Test fun `R15 beginLogin pendiente rechaza su generacion anterior incluso con el mismo tipo de acceso`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        val pausing = PausingStore(env.dataStore)
        val manager = SessionManager(SessionStore(pausing, FakeTokenCipher()), env.meApi, env.clock, backgroundScope).also { subject = it }
        manager.saveTenant(env.tenant)
        val origin = manager.generation
        pausing.paused = true
        val changed = async { manager.selectAccess(AccessMode.PATIENT) }
        pausing.entered.await()
        val previous = async {
            try { manager.beginLogin(AccessMode.PATIENT, expectedGeneration = origin); false }
            catch (_: SessionInvalidated) { true }
        }
        runCurrent()
        pausing.release.complete(Unit)
        changed.await()
        assertTrue(previous.await())
        assertEquals(SessionState.LoggedOut(env.tenant), manager.state.value)
    }

    @Test fun `R12 restauracion que vence durante me con 503 elimina cifrado sin quedar en error retenido`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        val expires = env.clock.instant().plusSeconds(10)
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
        assertFalse(env.manager.state.value is SessionState.Authenticated)
        assertFalse(env.manager.state.value is SessionState.VerificationError)
    }
    @Test fun `R12 respuesta protegida que vence durante HTTP se descarta aunque el timer no se haya ejecutado`() = runTest {
        env = StaffTestEnv(tmp.root, backgroundScope)
        env.activate()
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        env.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received.countDown(); check(release.await(3, TimeUnit.SECONDS))
                return MockResponse().setBody(StaffTestEnv.staffMe())
            }
        }
        val response = async {
            try { env.manager.authenticatedCall(AccessMode.STAFF) { env.meApi.meWithToken(it.authorization) }; false }
            catch (_: SessionInvalidated) { true }
        }
        assertTrue(withContext(Dispatchers.IO) { received.await(3, TimeUnit.SECONDS) })
        env.clock.now = StaffTestEnv.EXPIRES
        release.countDown()
        assertTrue(response.await())
        assertEquals(StoredSession.None, env.store.session())
        assertEquals(SessionState.StaffEntry, env.manager.state.value)
    }

}
