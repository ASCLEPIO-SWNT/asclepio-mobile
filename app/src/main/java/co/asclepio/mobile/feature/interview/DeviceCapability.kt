package co.asclepio.mobile.feature.interview

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Equipo capaz" (glosario de F-11-mobile, R6): RAM total ≥ 5,5 GiB, modelo verificado en `filesDir/models/` y un
 * arranque del `Engine` en ≤20 s en esta sesión de la app. Todo inyectable para probarlo en la JVM.
 * Un arranque fallido o lento deja el equipo como no capaz hasta que se reinicie la app (D10).
 */
class DeviceCapability(
    private val totalMemBytes: () -> Long,
    private val modelVerified: () -> Boolean,
    private val initTimeoutMs: Long = ENGINE_INIT_TIMEOUT_MS,
) {
    private var failedThisSession = false

    /** RAM suficiente (independiente del modelo): se ofrece la descarga. */
    fun hasEnoughMemory(): Boolean = totalMemBytes() >= MIN_TOTAL_MEM_BYTES

    /** Comprobación previa al arranque del `Engine`. */
    fun isCapable(): Boolean = !failedThisSession && hasEnoughMemory() && modelVerified()

    /**
     * Arranca el `Engine` con [init] y mide el tiempo. `false` si falla o tarda más de [initTimeoutMs]; desde entonces
     * el equipo no es capaz en esta sesión.
     */
    suspend fun initialize(init: suspend () -> Unit): Boolean {
        if (!isCapable()) return false
        val ok = try {
            withTimeoutOrNull(initTimeoutMs) { init(); true } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!ok) failedThisSession = true
        return ok
    }

    companion object {
        /** 5,5 GiB. */
        const val MIN_TOTAL_MEM_BYTES = 5_905_580_032L
        const val ENGINE_INIT_TIMEOUT_MS = 20_000L
    }
}

/** Motor elegido al pulsar "Continuar" (R6). */
enum class EngineChoice { LOCAL, SERVER_NOTICE, SERVER }

/** R6: local si el equipo es capaz; si no, el aviso del servidor solo la primera vez (PA-F11-3). */
fun selectEngine(capable: Boolean, serverNoticeSeen: Boolean): EngineChoice = when {
    capable -> EngineChoice.LOCAL
    serverNoticeSeen -> EngineChoice.SERVER
    else -> EngineChoice.SERVER_NOTICE
}
