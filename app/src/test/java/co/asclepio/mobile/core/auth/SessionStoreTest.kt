package co.asclepio.mobile.core.auth

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.Base64

class SessionStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val opened = mutableListOf<TestDataStore>()
    private val token = "eyJhbGciOiJIUzI1NiJ9.token-de-prueba.firma"
    private val expiresAt = Instant.parse("2026-10-26T23:00:00Z")
    private val tenant = RememberedTenant("t-1", "IPS Uno", "s-1", "Sede Norte")

    private fun file() = tmp.root.resolve("asclepio_session.preferences_pb")

    private fun open(cipher: TokenCipher = FakeTokenCipher()): SessionStore {
        val ds = TestDataStore(file()).also { opened += it }
        return SessionStore(ds.dataStore, cipher)
    }

    private fun closeAll() {
        opened.forEach { it.close() }
        opened.clear()
    }

    @After fun tearDown() = closeAll()

    @Test fun `R26 el archivo persistido no contiene el token en claro y al leerlo devuelve el original`() = runTest {
        open().saveSession(token, expiresAt)
        closeAll()
        val bytes = file().readBytes()
        assertTrue(bytes.isNotEmpty())
        val raw = bytes.toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains(token))
        assertFalse(raw.contains(Base64.getEncoder().encodeToString(token.toByteArray())))
        assertEquals(StoredSession.Present(token, expiresAt), open().session())
    }

    @Test fun `R26 el token se guarda cifrado con el TokenCipher como Base64 de iv y texto cifrado`() = runTest {
        val cipher = FakeTokenCipher()
        open(cipher).saveSession(token, expiresAt)
        closeAll()
        val raw = file().readBytes().toString(Charsets.ISO_8859_1)
        assertTrue(raw.contains(Base64.getEncoder().encodeToString(cipher.encrypt(token))))
    }

    @Test fun `R27 un token que no se puede descifrar se lee como ilegible`() = runTest {
        open().saveSession(token, expiresAt)
        closeAll()
        assertEquals(StoredSession.Unreadable, open(BrokenTokenCipher()).session())
    }

    @Test fun `R2 la IPS recordada guarda tenant, nombre, sede y nombre de la sede, y nada mas`() = runTest {
        val store = open()
        assertNull(store.tenant())
        store.saveTenant(tenant)
        closeAll()
        assertEquals(tenant, open().tenant())
        closeAll()
        val raw = file().readBytes().toString(Charsets.UTF_8)
        for (key in listOf("tenant_id", "tenant_name", "site_id", "site_name")) assertTrue(key, raw.contains(key))
        assertFalse(raw.contains("document"))
        assertFalse(raw.contains("password"))
    }

    @Test fun `R30 borrar la sesion elimina token y expiracion y conserva la IPS recordada`() = runTest {
        val store = open()
        store.saveTenant(tenant)
        store.saveSession(token, expiresAt)
        store.clearSession()
        closeAll()
        val reopened = open()
        assertEquals(StoredSession.None, reopened.session())
        assertEquals(tenant, reopened.tenant())
        closeAll()
        val raw = file().readBytes().toString(Charsets.UTF_8)
        assertFalse(raw.contains("access_token"))
        assertFalse(raw.contains("expires_at"))
    }
}
