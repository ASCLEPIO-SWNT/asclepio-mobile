package co.asclepio.mobile.core.auth

import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall
import co.asclepio.mobile.core.network.httpStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant

/** Estado de la sesión que decide la pila de navegación (D3). */
sealed interface SessionState {
    data object Loading : SessionState
    data object NoTenant : SessionState
    data class LoggedOut(val tenant: RememberedTenant) : SessionState
    data class LoggedIn(val tenant: RememberedTenant) : SessionState
}

/**
 * Sesión del paciente: IPS recordada, token en memoria (ya descifrado) y en disco (cifrado).
 * La sesión es activa si `expiresAt` es posterior a la hora del dispositivo (D11).
 */
class SessionManager(
    private val store: SessionStore,
    private val meApi: MeApi,
    private val clock: Clock,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile private var accessToken: String? = null
    private val mutex = Mutex()

    /** Token para el [AuthInterceptor]; `null` sin sesión activa. */
    fun currentToken(): String? = accessToken

    /** Al abrir la app y al volver a primer plano (ON_START): carga y comprueba la sesión (R3, R27). */
    suspend fun refresh() = mutex.withLock {
        val tenant = store.tenant()
        if (tenant == null) {
            accessToken = null
            _state.value = SessionState.NoTenant
            return@withLock
        }
        when (val session = store.session()) {
            is StoredSession.Present -> if (session.expiresAt.isAfter(clock.instant())) {
                accessToken = session.accessToken
                _state.value = SessionState.LoggedIn(tenant)
            } else {
                clearSessionLocked(tenant)
            }
            StoredSession.Unreadable -> clearSessionLocked(tenant)
            StoredSession.None -> {
                accessToken = null
                _state.value = SessionState.LoggedOut(tenant)
            }
        }
    }

    /** Guarda la IPS recordada (R2). */
    suspend fun saveTenant(tenant: RememberedTenant) = mutex.withLock {
        store.saveTenant(tenant)
        accessToken = null
        _state.value = SessionState.LoggedOut(tenant)
    }

    /** IPS recordada actual, si existe. */
    fun rememberedTenant(): RememberedTenant? = when (val s = _state.value) {
        is SessionState.LoggedIn -> s.tenant
        is SessionState.LoggedOut -> s.tenant
        else -> null
    }

    /** Guarda la sesión ya confirmada con `/me` (R19, R26). */
    suspend fun startSession(token: String, expiresAt: Instant) = mutex.withLock {
        val tenant = checkNotNull(store.tenant())
        store.saveSession(token, expiresAt)
        accessToken = token
        _state.value = SessionState.LoggedIn(tenant)
    }

    /**
     * `GET /me` con la sesión guardada (R28): un 401 borra la sesión; sin red o con 5xx se conserva.
     * Devuelve el perfil si la respuesta es 200.
     */
    suspend fun verifySession(): MeDto? =
        when (val result = apiCall { meApi.me() }) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> {
                if (result.error.httpStatus == 401) logout()
                null
            }
        }

    /** Cerrar sesión (R30): borra token y expiración de memoria y disco, conserva la IPS y no llama a la API. */
    suspend fun logout() = mutex.withLock {
        val tenant = store.tenant()
        if (tenant == null) {
            accessToken = null
            store.clearSession()
            _state.value = SessionState.NoTenant
        } else {
            clearSessionLocked(tenant)
        }
    }

    private suspend fun clearSessionLocked(tenant: RememberedTenant) {
        accessToken = null
        store.clearSession()
        _state.value = SessionState.LoggedOut(tenant)
    }
}
