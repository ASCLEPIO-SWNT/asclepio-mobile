package co.asclepio.mobile.core.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.SecretKey

// Prueba instrumentada (R26): el Android Keystore no existe en la JVM. Se ejecuta con
// ./gradlew connectedDebugAndroidTest en un emulador o dispositivo (pendiente del humano, T20).
@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {
    private val alias = "asclepio_session_key_test"
    private val cipher = KeystoreTokenCipher(alias)
    private val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwcnVlYmEifQ.firma"

    @After fun tearDown() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test fun `R26 cifra y descifra devolviendo exactamente el token original`() {
        assertEquals(token, cipher.decrypt(cipher.encrypt(token)))
    }

    @Test fun `R26 el texto cifrado no contiene el token y lleva un IV de 12 bytes y un tag de 128 bits`() {
        val bytes = cipher.encrypt(token)
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(token))
        assertEquals(12 + token.toByteArray().size + 16, bytes.size)
    }

    @Test fun `R26 dos cifrados del mismo token son distintos por el IV aleatorio`() {
        val a = cipher.encrypt(token)
        val b = cipher.encrypt(token)
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(0, 12).contentEquals(b.copyOfRange(0, 12)))
    }

    @Test fun `R26 la clave AES-256 del Keystore no es exportable`() {
        cipher.encrypt(token)
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null) as SecretKey
        assertNotNull(key)
        assertEquals("AES", key.algorithm)
        assertNull(key.encoded)
    }

    @Test fun `R27 un texto cifrado alterado o con la clave borrada no se puede descifrar`() {
        val bytes = cipher.encrypt(token)
        val tampered = bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(tampered) }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        assertThrows(Exception::class.java) { cipher.decrypt(bytes) }
    }
}
