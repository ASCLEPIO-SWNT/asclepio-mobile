package co.asclepio.mobile.core.voice

/**
 * Puerto mínimo del reconocimiento de voz **en el dispositivo** (F-08, ADR 0006): el audio nunca sale del teléfono.
 * Solo entrega el texto final; los resultados parciales no se piden (D6). Sin dependencias Android.
 * Todos los métodos y callbacks se usan en el hilo principal.
 */
interface OnDeviceRecognizer {
    /** Comprueba, sin abrir el micrófono ni pedir permisos, si hay reconocimiento local en español (R1). */
    fun checkAvailability(onResult: (OnDeviceAvailability) -> Unit)

    /** Inicia una escucha; entrega como mucho un `onFinal` o un `onError`. */
    fun start(languageTag: String, onFinal: (String) -> Unit, onError: (DictationError) -> Unit)

    /** Deja de escuchar y espera el resultado de lo ya dicho. */
    fun stop()

    /** Cancela la escucha y libera el reconocedor; no entrega más callbacks. */
    fun cancel()

    /** Igual que [cancel]; idempotente. */
    fun release()
}

sealed interface OnDeviceAvailability {
    /** Reconocimiento local disponible con la variante de español [languageTag] (p. ej. `es-CO`). */
    data class Available(val languageTag: String) : OnDeviceAvailability

    data object Unavailable : OnDeviceAvailability
}

/** Errores del reconocedor que importan a la UI (traducción en `mapRecognizerError`). */
enum class DictationError { LanguageUnavailable, Permission, NoMatch, Busy, Other }

/** Estado del dictado en el formulario. Nunca contiene texto reconocido (R12). */
sealed interface DictationUiState {
    data object Checking : DictationUiState

    data object Unavailable : DictationUiState

    data object Idle : DictationUiState

    data object Listening : DictationUiState

    data object Processing : DictationUiState
}

/** Sin reconocedor (valor por defecto y pruebas de F-02): nunca disponible, nunca escucha. */
object NoDictation : OnDeviceRecognizer {
    override fun checkAvailability(onResult: (OnDeviceAvailability) -> Unit) = onResult(OnDeviceAvailability.Unavailable)

    override fun start(languageTag: String, onFinal: (String) -> Unit, onError: (DictationError) -> Unit) = Unit

    override fun stop() = Unit

    override fun cancel() = Unit

    override fun release() = Unit
}
