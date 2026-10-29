package co.asclepio.mobile.core.ui.guide

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.concurrent.ConcurrentHashMap

/** Composición Android: resolver únicamente ids estáticos de recursos en el idioma activo. */
fun createAndroidSpeechGuide(context: Context): SpeechGuideController {
    val appContext = context.applicationContext
    return SpeechGuideController(
        engineFactory = { AndroidSpeechGuideEngine(appContext) },
        resolveString = { resource, localeTag ->
            val configuration = Configuration(appContext.resources.configuration)
            configuration.setLocales(LocaleList.forLanguageTags(localeTag))
            appContext.createConfigurationContext(configuration).getString(resource)
        },
    )
}

/** Motor TTS local: sin permisos de micrófono, archivos, descarga de voces ni fallback de red. */
class AndroidSpeechGuideEngine(private val context: Context) : SpeechGuideEngine {
    private data class Callbacks(val onDone: () -> Unit, val onError: () -> Unit)
    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = ConcurrentHashMap<String, Callbacks>()
    private var tts: TextToSpeech? = null
    @Volatile private var closed = false

    override fun initialize(onReady: (Boolean) -> Unit) {
        check(tts == null && !closed)
        tts = TextToSpeech(context) { status ->
            // El post también evita usar tts antes de completar su asignación en el constructor.
            handler.post {
                if (!closed) {
                    val configured = status == TextToSpeech.SUCCESS && tts?.setOnUtteranceProgressListener(listener) == TextToSpeech.SUCCESS
                    onReady(configured)
                }
            }
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = dispatch(utteranceId, error = false)
        @Deprecated("Required abstract method of the Android TTS listener")
        override fun onError(utteranceId: String?) = dispatch(utteranceId, error = true)
        override fun onError(utteranceId: String?, errorCode: Int) = dispatch(utteranceId, error = true)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = dispatch(utteranceId, error = true)
    }

    private fun dispatch(id: String?, error: Boolean) {
        val callback = id?.let(callbacks::remove) ?: return
        handler.post {
            if (!closed) {
                if (error) callback.onError() else callback.onDone()
            }
        }
    }

    override fun voices(): List<GuideVoice> = tts?.voices.orEmpty().map {
        GuideVoice(
            name = it.name,
            localeTag = it.locale.toLanguageTag(),
            requiresNetwork = it.isNetworkConnectionRequired,
            installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty(),
        )
    }

    override fun selectVoice(name: String): Boolean {
        val current = tts ?: return false
        val voice = current.voices.orEmpty().firstOrNull {
            it.name == name && !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
        } ?: return false
        if (current.setVoice(voice) != TextToSpeech.SUCCESS) return false
        val selected = current.voice ?: return false
        return selected.name == name && !selected.isNetworkConnectionRequired
    }

    override fun speak(text: String, utteranceId: String, onDone: () -> Unit, onError: () -> Unit): Boolean {
        if (closed) return false
        callbacks[utteranceId] = Callbacks(onDone, onError)
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
        if (!result) callbacks.remove(utteranceId)
        return result
    }

    override fun stop() {
        callbacks.clear()
        tts?.stop()
    }

    override fun shutdown() {
        closed = true
        callbacks.clear()
        tts?.shutdown()
        tts = null
    }
}
