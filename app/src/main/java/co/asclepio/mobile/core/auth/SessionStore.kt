package co.asclepio.mobile.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.Base64

/** IPS recordada (R2, R3): no es un dato personal ni un secreto. Nunca incluye el documento. */
data class RememberedTenant(val tenantId: String, val tenantName: String, val siteId: String, val siteName: String)

sealed interface StoredSession {
    data object None : StoredSession

    data class Present(val accessToken: String, val expiresAt: Instant) : StoredSession {
        override fun toString(): String = "Present(accessToken=***, expiresAt=$expiresAt)"
    }

    /** El token no se puede descifrar o los datos guardados están incompletos (R27). */
    data object Unreadable : StoredSession
}

/**
 * Preferences DataStore con dos grupos de claves (D1, D2): la IPS recordada, en claro, y la sesión
 * (`access_token` = Base64 de `iv ‖ ciphertext` y `expires_at` en ISO-8601).
 */
class SessionStore(private val dataStore: DataStore<Preferences>, private val cipher: TokenCipher) {

    suspend fun tenant(): RememberedTenant? {
        val prefs = dataStore.data.first()
        return RememberedTenant(
            tenantId = prefs[TENANT_ID] ?: return null,
            tenantName = prefs[TENANT_NAME] ?: return null,
            siteId = prefs[SITE_ID] ?: return null,
            siteName = prefs[SITE_NAME] ?: return null,
        )
    }

    suspend fun saveTenant(tenant: RememberedTenant) {
        dataStore.edit {
            it[TENANT_ID] = tenant.tenantId
            it[TENANT_NAME] = tenant.tenantName
            it[SITE_ID] = tenant.siteId
            it[SITE_NAME] = tenant.siteName
        }
    }

    suspend fun session(): StoredSession {
        val prefs = dataStore.data.first()
        val encoded = prefs[ACCESS_TOKEN]
        val expires = prefs[EXPIRES_AT]
        if (encoded == null && expires == null) return StoredSession.None
        return try {
            val token = cipher.decrypt(Base64.getDecoder().decode(requireNotNull(encoded)))
            StoredSession.Present(token, Instant.parse(requireNotNull(expires)))
        } catch (e: Exception) {
            StoredSession.Unreadable
        }
    }

    suspend fun saveSession(accessToken: String, expiresAt: Instant) {
        val encoded = Base64.getEncoder().encodeToString(cipher.encrypt(accessToken))
        dataStore.edit {
            it[ACCESS_TOKEN] = encoded
            it[EXPIRES_AT] = expiresAt.toString()
        }
    }

    /** Borra solo el token y su expiración; conserva la IPS recordada (R30). */
    suspend fun clearSession() {
        dataStore.edit {
            it.remove(ACCESS_TOKEN)
            it.remove(EXPIRES_AT)
        }
    }

    private companion object {
        val TENANT_ID = stringPreferencesKey("tenant_id")
        val TENANT_NAME = stringPreferencesKey("tenant_name")
        val SITE_ID = stringPreferencesKey("site_id")
        val SITE_NAME = stringPreferencesKey("site_name")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val EXPIRES_AT = stringPreferencesKey("expires_at")
    }
}
