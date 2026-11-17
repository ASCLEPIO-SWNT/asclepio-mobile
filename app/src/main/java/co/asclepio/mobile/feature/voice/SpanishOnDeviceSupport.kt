package co.asclepio.mobile.feature.voice

import android.os.Build
import android.speech.SpeechRecognizer
import co.asclepio.mobile.core.voice.DictationError

/** Cómo se detecta el reconocimiento local en español según la versión de Android (R1). */
enum class RecognitionStrategy {
    /** API 31–32: solo `isOnDeviceRecognitionAvailable`; el idioma no instalado se conoce al escuchar (R7). */
    ServiceOnly,

    /** API 33+: además `checkRecognitionSupport` con los idiomas instalados en el dispositivo. */
    CheckSupport,
}

fun recognitionStrategy(sdkInt: Int): RecognitionStrategy =
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) RecognitionStrategy.CheckSupport else RecognitionStrategy.ServiceOnly

private const val PREFERRED_TAG = "es-CO"
private const val SPANISH = "es"

/**
 * Elige la variante de español entre los idiomas **instalados** en el dispositivo (R1): `es-CO`, si no `es` y si no
 * la primera `es-*` en el orden recibido. `null` si no hay ninguna: no se descargan modelos (D2).
 */
fun pickSpanishTag(installed: List<String>): String? =
    installed.firstOrNull { it.equals(PREFERRED_TAG, ignoreCase = true) }
        ?: installed.firstOrNull { it.equals(SPANISH, ignoreCase = true) }
        ?: installed.firstOrNull { it.startsWith("$SPANISH-", ignoreCase = true) }

/** Traduce los códigos de error de `SpeechRecognizer` a lo que la UI necesita (R4, R7). */
fun mapRecognizerError(code: Int): DictationError = when (code) {
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> DictationError.LanguageUnavailable
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> DictationError.Permission
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> DictationError.NoMatch
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> DictationError.Busy
    else -> DictationError.Other
}

/** Qué hace "Dictar" (R3, D9): pedir `RECORD_AUDIO` en ese momento o escuchar si ya está concedido. */
enum class DictationAction { RequestPermission, Listen }

fun dictationAction(hasPermission: Boolean): DictationAction =
    if (hasPermission) DictationAction.Listen else DictationAction.RequestPermission
