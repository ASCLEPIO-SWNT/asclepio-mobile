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

/** Aserción de compatibilidad F-01/F-02: una IPS no basta para autorizar, exige principal paciente verificado. */
fun assertPatientSession(manager: SessionManager, tenant: RememberedTenant) {
    val state = manager.state.value as SessionState.Authenticated
    val patient = state.principal as VerifiedPrincipal.Patient
    org.junit.Assert.assertEquals(tenant, patient.tenant)
    org.junit.Assert.assertEquals(tenant.tenantId, patient.tenantId)
    org.junit.Assert.assertTrue(manager.isCurrent(state.generation))
    org.junit.Assert.assertNotNull(manager.currentToken())
}
