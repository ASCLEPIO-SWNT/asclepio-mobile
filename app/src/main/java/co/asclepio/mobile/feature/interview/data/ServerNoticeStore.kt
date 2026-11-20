package co.asclepio.mobile.feature.interview.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Marca "aviso del servidor visto" (R6, PA-F11-3). DataStore propio (`asclepio_interview`): el de preferencias
 * visuales se reescribe entero al cambiarlas. Solo guarda este booleano; nada del paciente.
 */
class ServerNoticeStore(private val store: DataStore<Preferences>) {
    suspend fun seen(): Boolean = try {
        store.data.first()[SEEN] == true
    } catch (e: IOException) {
        false
    }

    suspend fun markSeen() {
        try {
            store.edit { it[SEEN] = true }
        } catch (e: IOException) {
            // Sin disco, el aviso vuelve a mostrarse la próxima vez.
        }
    }

    private companion object {
        val SEEN = booleanPreferencesKey("server_notice_seen")
    }
}
