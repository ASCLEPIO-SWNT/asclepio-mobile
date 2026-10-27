package co.asclepio.mobile.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.security.GeneralSecurityException

/** Cifrador falso para la JVM (D1): prefijo + XOR. No es seguro; solo prueba la lógica de almacenamiento. */
class FakeTokenCipher(private val keyByte: Int = 0x5A) : TokenCipher {
    override fun encrypt(plaintext: String): ByteArray =
        PREFIX + plaintext.toByteArray(Charsets.UTF_8).map { (it.toInt() xor keyByte).toByte() }

    override fun decrypt(ciphertext: ByteArray): String {
        if (ciphertext.size < PREFIX.size || !ciphertext.copyOfRange(0, PREFIX.size).contentEquals(PREFIX)) {
            throw GeneralSecurityException("tag inválido")
        }
        return ciphertext.copyOfRange(PREFIX.size, ciphertext.size)
            .map { (it.toInt() xor keyByte).toByte() }.toByteArray().toString(Charsets.UTF_8)
    }

    private companion object {
        val PREFIX = byteArrayOf(1, 2, 3, 4)
    }
}

/** Cifrador que no puede descifrar nada (clave invalidada o borrada, R27). */
class BrokenTokenCipher : TokenCipher {
    override fun encrypt(plaintext: String): ByteArray = FakeTokenCipher().encrypt(plaintext)
    override fun decrypt(ciphertext: ByteArray): String = throw GeneralSecurityException("clave invalidada")
}

/** Preferences DataStore real sobre un archivo temporal; [close] libera el archivo. */
class TestDataStore(val file: File) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }

    fun close() = scope.cancel()
}

/** Reloj de pruebas que se puede adelantar (R27). */
class MutableClock(var now: java.time.Instant) : java.time.Clock() {
    override fun getZone(): java.time.ZoneId = java.time.ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?): java.time.Clock = this
    override fun instant(): java.time.Instant = now
}
