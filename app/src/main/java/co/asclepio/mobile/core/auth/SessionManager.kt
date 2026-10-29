package co.asclepio.mobile.core.auth

import co.asclepio.mobile.core.network.ApiResult
import co.asclepio.mobile.core.network.apiCall
import co.asclepio.mobile.core.network.commonMessage
import co.asclepio.mobile.core.network.httpStatus
import co.asclepio.mobile.core.ui.UiMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

sealed interface SessionState {
    data object Loading : SessionState
    data object Welcome : SessionState
    data object NoTenant : SessionState
    data class LoggedOut(val tenant: RememberedTenant) : SessionState
    data object StaffEntry : SessionState
    data object Verifying : SessionState
    data class VerificationError(val message: UiMessage) : SessionState
    data class Authenticated(val principal: VerifiedPrincipal, val generation: Long) : SessionState
}

class LoginAttempt internal constructor(val generation: Long, val mode: AccessMode, internal val tenantId: String?) {
    override fun toString() = "LoginAttempt(generation=$generation, mode=$mode, tenantId=***)"
}

class SessionInvalidated : CancellationException("Session no longer current")

/** Un solo token; ningún resultado anterior puede persistir, autorizar ni cerrar una sesión posterior. */
class SessionManager(
    private val store: SessionStore,
    private val meApi: MeApi,
    private val clock: Clock,
    private val expiryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private val mutex = Mutex()
    @Volatile private var accessToken: String? = null
    @Volatile private var expiresAt: Instant? = null
    @Volatile var generation: Long = 0L
        private set
    private var tenant: RememberedTenant? = null
    private var selectedAccess: AccessMode? = null
    private var expiryJob: Job? = null
    private var loginAttempt: LoginAttempt? = null
    private val invalidationListeners = CopyOnWriteArrayList<() -> Unit>()

    /** Limpieza inmediata de cachés efímeras; el núcleo no conoce ninguna feature. */
    fun onInvalidated(clear: () -> Unit) { invalidationListeners.add(clear) }

    fun currentToken(): String? = accessToken?.takeIf { expiresAt?.isAfter(clock.instant()) == true }
    fun rememberedTenant(): RememberedTenant? = tenant
    fun isCurrent(value: Long): Boolean = value == generation
    fun requireCurrent(value: Long) { if (!isCurrent(value)) throw SessionInvalidated() }

    /** La comprobación de primer plano no cambia la identidad ni repite /me si ya está verificada. */
    suspend fun refresh() {
        val restored = mutex.withLock {
            if (_state.value is SessionState.Authenticated) {
                if (expiresAt?.isAfter(clock.instant()) != true) clearLocked()
                return
            }
            if (_state.value == SessionState.Verifying) return
            tenant = store.tenant()
            when (val saved = store.session()) {
                is StoredSession.Present -> {
                    if (!saved.expiresAt.isAfter(clock.instant()) || saved.accessToken.isBlank()) {
                        clearLocked()
                        return
                    }
                    invalidateLocked()
                    _state.value = SessionState.Verifying
                    generation to saved
                }
                StoredSession.Unreadable -> { clearLocked(); return }
                StoredSession.None -> { _state.value = entryState(); return }
            }
        }
        val (expected, saved) = restored
        // La verificación pertenece al gestor: rotar cancela el await de UI, no su identidad ni la petición.
        expiryScope.launch {
            if (!isCurrent(expected)) return@launch
            val result = apiCall { meApi.meWithToken("Bearer ${saved.accessToken}") }
            mutex.withLock {
                if (!isCurrent(expected)) return@withLock
                if (!saved.expiresAt.isAfter(clock.instant())) { clearLocked(); return@withLock }
                when (result) {
                    is ApiResult.Success -> {
                        val principal = result.value.toPrincipal(tenant)
                        if (principal == null || !saved.expiresAt.isAfter(clock.instant())) {
                            clearLocked()
                        } else activateLocked(saved.accessToken, saved.expiresAt, principal)
                    }
                    is ApiResult.Failure -> {
                        if (result.error.httpStatus == 401) clearLocked() else {
                            _state.value = SessionState.VerificationError(result.error.commonMessage())
                        }
                    }
                }
            }
        }.join()
    }

    suspend fun saveTenant(value: RememberedTenant, expectedGeneration: Long = generation) = mutex.withLock {
        requireCurrent(expectedGeneration)
        invalidateLocked()
        store.clearSession()
        store.saveTenant(value)
        tenant = value
        selectedAccess = AccessMode.PATIENT
        _state.value = entryState()
    }

    /** La UI pide confirmación antes de invocarlo desde un ámbito autenticado. */
    suspend fun selectAccess(mode: AccessMode, expectedGeneration: Long? = null) = mutex.withLock {
        if (expectedGeneration != null && !isCurrent(expectedGeneration)) return@withLock
        selectedAccess = mode
        tenant = store.tenant()
        clearLocked()
    }

    suspend fun showWelcome(expectedGeneration: Long? = null) = mutex.withLock {
        if (expectedGeneration != null && !isCurrent(expectedGeneration)) return@withLock
        selectedAccess = null
        clearLocked()
    }

    suspend fun beginLogin(mode: AccessMode, expectedGeneration: Long = generation): LoginAttempt = mutex.withLock {
        requireCurrent(expectedGeneration)
        if (_state.value is SessionState.Authenticated || (selectedAccess != null && selectedAccess != mode)) throw SessionInvalidated()
        selectedAccess = mode
        tenant = store.tenant()
        clearLocked()
        LoginAttempt(generation, mode, tenant?.tenantId).also { loginAttempt = it }
    }

    suspend fun completeLogin(attempt: LoginAttempt, token: String, expiration: Instant, me: MeDto): Boolean = mutex.withLock {
        if (!isCurrent(attempt.generation) || loginAttempt !== attempt) throw SessionInvalidated()
        val principal = me.toPrincipal(tenant)
        if (token.isBlank() || !expiration.isAfter(clock.instant()) || principal == null ||
            principal.mode != attempt.mode || (attempt.mode == AccessMode.PATIENT && principal.tenantId != attempt.tenantId)
        ) return@withLock false
        store.saveSession(token, expiration)
        if (!expiration.isAfter(clock.instant())) { clearLocked(); return@withLock false }
        activateLocked(token, expiration, principal)
        true
    }

    /** Compatibilidad de lectura del inicio paciente: el perfil ya se verificó antes de montar la pantalla. */
    fun patientPrincipal(): VerifiedPrincipal.Patient? =
        (_state.value as? SessionState.Authenticated)?.principal as? VerifiedPrincipal.Patient

    suspend fun logout(expectedGeneration: Long? = null) = mutex.withLock {
        if (expectedGeneration == null || isCurrent(expectedGeneration)) clearLocked()
    }

    internal suspend fun capture(scope: AccessMode, expectedGeneration: Long): AuthHandle = mutex.withLock {
        requireCurrent(expectedGeneration)
        val authenticated = _state.value as? SessionState.Authenticated ?: throw SessionInvalidated()
        if (authenticated.principal.mode != scope) throw SessionInvalidated()
        val token = currentToken()
        if (token == null) { clearLocked(); throw SessionInvalidated() }
        AuthHandle(authenticated.generation, "Bearer $token", authenticated.principal)
    }

    internal suspend fun finish(handle: AuthHandle, unauthorized: Boolean) = mutex.withLock {
        requireCurrent(handle.generation)
        if (expiresAt?.isAfter(clock.instant()) != true) { clearLocked(); throw SessionInvalidated() }
        if (unauthorized) clearLocked()
    }

    private fun entryState(): SessionState = when (selectedAccess) {
        null -> SessionState.Welcome
        AccessMode.STAFF -> SessionState.StaffEntry
        AccessMode.PATIENT -> tenant?.let { SessionState.LoggedOut(it) } ?: SessionState.NoTenant
    }
    private fun invalidateLocked() {
        generation++
        loginAttempt = null
        invalidationListeners.forEach { it() }
        accessToken = null
        expiresAt = null
        expiryJob?.cancel()
        expiryJob = null
    }
    private suspend fun clearLocked() {
        invalidateLocked()
        store.clearSession()
        _state.value = entryState()
    }
    private fun activateLocked(token: String, expiration: Instant, principal: VerifiedPrincipal) {
        selectedAccess = principal.mode
        loginAttempt = null
        accessToken = token
        expiresAt = expiration
        _state.value = SessionState.Authenticated(principal, generation)
        val expected = generation
        expiryJob?.cancel()
        expiryJob = expiryScope.launch {
            while (isCurrent(expected)) {
                val remaining = Duration.between(clock.instant(), expiration).toMillis()
                if (remaining <= 0) {
                    mutex.withLock { if (isCurrent(expected)) { expiryJob = null; clearLocked() } }
                    return@launch
                }
                delay(remaining)
            }
        }
    }
    fun close() { expiryJob?.cancel() }
}
