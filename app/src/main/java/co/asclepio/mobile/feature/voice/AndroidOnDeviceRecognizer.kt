package co.asclepio.mobile.feature.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import co.asclepio.mobile.core.voice.DictationError
import co.asclepio.mobile.core.voice.OnDeviceAvailability
import co.asclepio.mobile.core.voice.OnDeviceRecognizer

/**
 * Adaptador de `SpeechRecognizer` **solo en el dispositivo** (R1, R2; ADR 0006): únicamente
 * `createOnDeviceSpeechRecognizer`, siempre después de `isOnDeviceRecognitionAvailable`. No graba audio, no lanza
 * actividades de reconocimiento, no pide resultados parciales y no descarga modelos (D2).
 *
 * Se usa desde el hilo principal; los callbacks llegan por `context.mainExecutor`. Cada escucha crea un reconocedor
 * nuevo que se destruye al terminar, cancelar o liberar; después no entrega más callbacks.
 */
class AndroidOnDeviceRecognizer(private val context: Context) : OnDeviceRecognizer {
    /** Reconocedor de la comprobación de idiomas (API 33+). */
    private var checking: SpeechRecognizer? = null

    /** Reconocedor de la escucha vigente. */
    private var listening: SpeechRecognizer? = null

    // NewApi: recognitionStrategy solo devuelve CheckSupport con SDK_INT >= 33 (TIRAMISU).
    @SuppressLint("NewApi")
    override fun checkAvailability(onResult: (OnDeviceAvailability) -> Unit) {
        if (!serviceAvailable()) return onResult(OnDeviceAvailability.Unavailable)
        when (recognitionStrategy(Build.VERSION.SDK_INT)) {
            // API 31–32: un idioma no instalado se detecta al escuchar (ERROR_LANGUAGE_*, R7).
            RecognitionStrategy.ServiceOnly -> onResult(OnDeviceAvailability.Available(PREFERRED_TAG))
            RecognitionStrategy.CheckSupport -> checkInstalledSpanish(onResult)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkInstalledSpanish(onResult: (OnDeviceAvailability) -> Unit) {
        destroy(checking)
        val recognizer = create() ?: return onResult(OnDeviceAvailability.Unavailable)
        checking = recognizer
        val finish = { availability: OnDeviceAvailability ->
            if (checking === recognizer) {
                checking = null
                destroy(recognizer)
                onResult(availability)
            }
        }
        val callback = object : RecognitionSupportCallback {
            override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                // Solo cuentan los idiomas instalados (no pending, supported ni online).
                val tag = pickSpanishTag(recognitionSupport.installedOnDeviceLanguages)
                finish(if (tag != null) OnDeviceAvailability.Available(tag) else OnDeviceAvailability.Unavailable)
            }

            override fun onError(error: Int) = finish(OnDeviceAvailability.Unavailable)
        }
        runCatching {
            recognizer.setRecognitionListener(SilentListener)
            recognizer.checkRecognitionSupport(recognizeIntent(null), context.mainExecutor, callback)
        }.onFailure { finish(OnDeviceAvailability.Unavailable) }
    }

    override fun start(languageTag: String, onFinal: (String) -> Unit, onError: (DictationError) -> Unit) {
        cancel()
        val recognizer = create() ?: return onError(DictationError.LanguageUnavailable)
        listening = recognizer
        val finish = { deliver: () -> Unit ->
            if (listening === recognizer) {
                listening = null
                destroy(recognizer)
                deliver()
            }
        }
        val listener = object : RecognitionListener by SilentListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                finish { onFinal(text) }
            }

            override fun onError(error: Int) = finish { onError(mapRecognizerError(error)) }
        }
        runCatching {
            recognizer.setRecognitionListener(listener)
            recognizer.startListening(recognizeIntent(languageTag))
        }.onFailure { finish { onError(DictationError.Other) } }
    }

    override fun stop() {
        runCatching { listening?.stopListening() }
    }

    override fun cancel() {
        val recognizer = listening ?: return
        listening = null
        runCatching { recognizer.cancel() }
        destroy(recognizer)
    }

    override fun release() {
        cancel()
        destroy(checking)
        checking = null
    }

    private fun serviceAvailable(): Boolean = runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    /** `createOnDeviceSpeechRecognizer` lanza `UnsupportedOperationException` sin servicio local: se trata como no disponible. */
    private fun create(): SpeechRecognizer? =
        if (!serviceAvailable()) null else runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()

    private fun destroy(recognizer: SpeechRecognizer?) {
        runCatching { recognizer?.destroy() }
    }

    /** Intent para el reconocedor local; nunca se lanza como actividad. */
    private fun recognizeIntent(languageTag: String?): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        if (languageTag != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private object SilentListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) = Unit

        override fun onResults(results: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        /** Variante preferida en API 31–32, donde no se puede consultar el idioma antes de escuchar. */
        const val PREFERRED_TAG = "es-CO"
    }
}
