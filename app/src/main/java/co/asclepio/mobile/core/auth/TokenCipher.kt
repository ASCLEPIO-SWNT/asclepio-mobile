package co.asclepio.mobile.core.auth

/** Cifra el accessToken antes de persistirlo (D1). En la JVM se usa un cifrador falso; en el dispositivo, [KeystoreTokenCipher]. */
interface TokenCipher {
    fun encrypt(plaintext: String): ByteArray

    /** Lanza una excepción si los bytes no se pueden descifrar (tag inválido, clave invalidada o borrada). */
    fun decrypt(ciphertext: ByteArray): String
}
